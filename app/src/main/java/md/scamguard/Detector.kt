package md.scamguard

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/** Информация о распознанной организации (банк/телеком/госструктура). */
data class BankMatch(
    val id: String,
    val displayName: String,
    val displayNameRo: String,
    val category: String,          // bank | telecom | utility | state | payment
    val matchedBy: String          // "domain" | "alias" | "sender"
)

data class SmsAnalysis(
    val hasOtp: Boolean,
    val keywordsHit: List<String>,
    val urls: List<String>,
    val claimedBank: BankMatch?,   // распознанная организация (если нашли)
    val pressure: Boolean,
    val reasonCategory: ReasonCategory,
    val reason: String,
)

/** Категория причины для подстановки в уведомление (без цифр кода!). */
enum class ReasonCategory(val ru: String, val ro: String) {
    OTP_GENERIC("код подтверждения", "cod de confirmare"),
    OTP_CREDIT("код подтверждения (возможно оформление кредита)",
        "cod de confirmare (posibilă luare credit)"),
    OTP_TRANSFER("код подтверждения (возможно перевод)",
        "cod de confirmare (posibil transfer)"),
    OTP_LOGIN("код подтверждения (возможно вход в аккаунт)",
        "cod de confirmare (posibilă autentificare)"),
    PRESSURE("давление с требованием срочности",
        "presiune cu cerință de urgență"),
    OTHER("подозрительное сообщение", "mesaj suspect");
}

enum class Threat { NONE, LOW, HIGH }

object CallContext {
    @Volatile private var state: Int = 0
    @Volatile private var lastChangeMs: Long = 0L
    @Volatile var lastNumber: String = ""
    fun markState(s: Int) { state = s; lastChangeMs = System.currentTimeMillis() }
    fun markNumber(n: String?) { if (!n.isNullOrBlank()) lastNumber = n }
    fun callActive(): Boolean = state == 1 || state == 2
    fun msSinceLastChange(): Long =
        if (lastChangeMs == 0L) Long.MAX_VALUE
        else System.currentTimeMillis() - lastChangeMs
    /** Был ли активный звонок, или он закончился не более ms назад. */
    fun activeOrRecent(ms: Long): Boolean =
        callActive() || (state == 0 && msSinceLastChange() < ms)
}

/**
 * Загружает banks.json из assets и предоставляет fuzzy-поиск организации
 * по тексту SMS и по sender'у.
 *
 * Алгоритм совпадения (по убыванию надёжности):
 *   1. Точное совпадение домена (maib.md ⊂ message → MAIB)
 *   2. Substring домена (maibbank.md ⊂ message → MAIB, т.к. maib.md содержится)
 *   3. Sender alphanumeric: "MAIB" → MAIB
 *   4. Alias substring: "Уважаемый клиент МАИБ" → MAIB
 */
object BankLookup {
    @Volatile private var banks: List<BankInfo> = emptyList()

    data class BankInfo(
        val id: String, val displayName: String, val displayNameRo: String,
        val category: String,
        val domains: List<String>, val aliases: List<String>
    )

    /** Загружается лениво при первом вызове. */
    fun ensureLoaded(ctx: Context) {
        if (banks.isNotEmpty()) return
        banks = try {
            val text = ctx.assets.open("banks.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val arr = root.getJSONArray("banks")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BankInfo(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    displayNameRo = o.optString("displayNameRo", o.getString("displayName")),
                    category = o.optString("category", "other"),
                    domains = jsonArrayToList(o.optJSONArray("domains")),
                    aliases = jsonArrayToList(o.optJSONArray("aliases")).map { it.lowercase() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Находит банк по тексту SMS и/или sender'у.
     * Возвращает первое совпадение по приоритету: domain > sender > alias.
     */
    fun find(textLower: String, sender: String, urls: List<String>): BankMatch? {
        if (banks.isEmpty()) return null

        // 1) Совпадение по домену из URL'ов (fuzzy: суффикс или префикс)
        for (b in banks) {
            for (d in b.domains) {
                if (urls.any { LocalDb.domainOf(it).let { dom ->
                        dom == d || dom.endsWith(".$d") || dom.contains(d)
                    } }) return BankMatch(b.id, b.displayName, b.displayNameRo, b.category, "domain")
            }
        }

        // 2) Совпадение по alphanumeric sender'у
        val senderClean = sender.lowercase().replace(Regex("[^a-z0-9а-яёî]"), "")
        if (senderClean.isNotBlank() && !senderClean.all { it.isDigit() }) {
            for (b in banks) {
                if (b.aliases.any { senderClean == it || senderClean.contains(it) || it.contains(senderClean) })
                    return BankMatch(b.id, b.displayName, b.displayNameRo, b.category, "sender")
            }
        }

        // 3) Совпадение alias в теле SMS
        for (b in banks) {
            for (a in b.aliases) {
                if (a.length >= 3 && textLower.contains(a))
                    return BankMatch(b.id, b.displayName, b.displayNameRo, b.category, "alias")
            }
        }

        return null
    }

    private fun jsonArrayToList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }
}

object LocalDb {
    val codeRegex = Regex("(?<!\\d)\\d{3,8}(?!\\d)")
    val otpKeywords = listOf(
        "код", "одноразов", "никому", "не сообщайте", "пароль", "подтвержд", "верифик",
        "cod", "parol", "nu comunica", "verificare", "unic", "confirmare", "autentificare",
        "code", "otp", "verification", "one-time", "do not share",
        "password", "verify", "auth", "confirm"
    )
    val standaloneOtpMarkers = listOf("otp", "код:", "code:", "cod:", "pin:", "g-", "g‑")
    val pressureKeywords = listOf(
        "срочно", "немедленно", "блокировк", "подозрительн",
        "urgent", "blocat", "suspect", "imediat"
    )

    /** Ключевые слова для подкатегории "код для кредита". */
    val creditKeywords = listOf(
        "кредит", "займ", "ссуд", "credit", "împrumut", "imprumut", "loan"
    )
    /** Ключевые слова для подкатегории "код для перевода". */
    val transferKeywords = listOf(
        "перевод", "transfer", "retragere", "withdraw", "withdrawal",
        "снятие", "выплат", "плат"
    )
    /** Ключевые слова для подкатегории "код для входа". */
    val loginKeywords = listOf(
        "вход", "войти", "login", "sign-in", "sign in",
        "autentificare", "logare", "autentific"
    )

    fun extractUrls(text: String): List<String> =
        Regex("""https?://\S+|[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/\S*)?""")
            .findAll(text).map { it.value.trimEnd('.', ',', ')') }.toList()

    fun domainOf(url: String): String =
        url.removePrefix("http://").removePrefix("https://")
            .substringBefore('/').lowercase()
}

object Detector {

    /** Окно для «недавнего» звонка (5 минут) — LOW alert. */
    const val RECENT_CALL_WINDOW_MS: Long = 5 * 60_000L
    /** Окно для HIGH (звонок только что закончился, ≤ 2 мин). */
    const val HIGH_CALL_WINDOW_MS: Long = 2 * 60_000L

    fun analyze(ctx: Context, body: String, sender: String = ""): SmsAnalysis {
        BankLookup.ensureLoaded(ctx)
        val low = body.lowercase()
        val kw = LocalDb.otpKeywords.any { low.contains(it) }
        val mk = LocalDb.standaloneOtpMarkers.any { low.contains(it) }
        val hasCode = LocalDb.codeRegex.containsMatchIn(body)
        val hasOtp = (kw || mk) && hasCode
        val urls = LocalDb.extractUrls(body)
        val pressure = LocalDb.pressureKeywords.any { low.contains(it) }

        val bank = BankLookup.find(low, sender, urls)

        // Подкатегория для отображения в уведомлении
        val category = when {
            hasOtp && LocalDb.creditKeywords.any { low.contains(it) } -> ReasonCategory.OTP_CREDIT
            hasOtp && LocalDb.transferKeywords.any { low.contains(it) } -> ReasonCategory.OTP_TRANSFER
            hasOtp && LocalDb.loginKeywords.any { low.contains(it) } -> ReasonCategory.OTP_LOGIN
            hasOtp -> ReasonCategory.OTP_GENERIC
            pressure && bank != null -> ReasonCategory.PRESSURE
            else -> ReasonCategory.OTHER
        }

        val reason = category.ru // короткий текст для лога; UI берёт по локали

        val hits = (LocalDb.otpKeywords + LocalDb.standaloneOtpMarkers + LocalDb.pressureKeywords)
            .filter { low.contains(it) }

        return SmsAnalysis(
            hasOtp = hasOtp, keywordsHit = hits, urls = urls,
            claimedBank = bank, pressure = pressure,
            reasonCategory = category, reason = reason
        )
    }

    /**
     * Уровни тревоги:
     *   HIGH: код + ПРЯМО СЕЙЧАС идёт звонок
     *   LOW:  код + звонок закончился ≤ 5 мин назад
     *   NONE: всё остальное
     *
     * Принцип: КРАСНЫЙ только когда звонок активен в данный момент.
     * Если звонок уже завершён (даже минуту назад) — это ЖЁЛТЫЙ,
     * предупреждение есть, но менее срочное.
     */
    fun threatLevel(a: SmsAnalysis): Threat {
        if (!a.hasOtp) return Threat.NONE
        return when {
            CallContext.callActive() -> Threat.HIGH
            CallContext.activeOrRecent(RECENT_CALL_WINDOW_MS) -> Threat.LOW
            else -> Threat.NONE
        }
    }
}

object History {
    data class Event(
        val time: Long, val level: String, val sender: String, val callNumber: String,
        val reason: String, val smsBody: String, val dismissed: Boolean = false,
        val bankName: String = "",                // распознанная организация для отображения
        val reasonCategory: String = "OTHER"      // ReasonCategory enum name
    )

    private val Context.ds by preferencesDataStore("history")
    private val KEY = stringSetPreferencesKey("events_v2")

    private fun ser(e: Event) = listOf(
        e.time.toString(), e.level,
        b64(e.sender), b64(e.callNumber), b64(e.reason), b64(e.smsBody),
        e.dismissed.toString(),
        b64(e.bankName), e.reasonCategory
    ).joinToString("\u0001")

    private fun deser(s: String): Event? {
        val p = s.split("\u0001")
        if (p.size < 7) return null
        return Event(
            time = p[0].toLongOrNull() ?: return null,
            level = p[1],
            sender = ub64(p[2]),
            callNumber = ub64(p[3]),
            reason = ub64(p[4]),
            smsBody = ub64(p[5]),
            dismissed = p[6].toBoolean(),
            bankName = if (p.size > 7) ub64(p[7]) else "",
            reasonCategory = if (p.size > 8) p[8] else "OTHER"
        )
    }

    private fun b64(s: String) = android.util.Base64.encodeToString(
        s.toByteArray(), android.util.Base64.NO_WRAP)
    private fun ub64(s: String) = try {
        String(android.util.Base64.decode(s, android.util.Base64.NO_WRAP))
    } catch (_: Throwable) { "" }

    suspend fun add(ctx: Context, e: Event) {
        ctx.ds.edit { it[KEY] = (it[KEY] ?: emptySet()) + ser(e) }
    }
    suspend fun list(ctx: Context): List<Event> =
        (ctx.ds.data.first()[KEY] ?: emptySet())
            .mapNotNull { deser(it) }.sortedByDescending { it.time }
    suspend fun markDismissed(ctx: Context, time: Long) {
        ctx.ds.edit { p ->
            val cur = p[KEY] ?: emptySet()
            p[KEY] = cur.mapNotNull { deser(it) }
                .map { if (it.time == time) it.copy(dismissed = true) else it }
                .map { ser(it) }.toSet()
        }
    }
    suspend fun delete(ctx: Context, time: Long) {
        ctx.ds.edit { p ->
            val cur = p[KEY] ?: emptySet()
            p[KEY] = cur.mapNotNull { deser(it) }
                .filter { it.time != time }.map { ser(it) }.toSet()
        }
    }
    suspend fun clearAll(ctx: Context) {
        ctx.ds.edit { p -> p[KEY] = emptySet() }
    }
}

object Prefs {
    private val Context.ds by preferencesDataStore("prefs")
    private val LANG = stringPreferencesKey("lang")
    private val ACCEPTED_EULA = booleanPreferencesKey("accepted_eula_v1")
    private val ACCEPTED_AGE = booleanPreferencesKey("accepted_age_18")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")

    suspend fun lang(ctx: Context): String = ctx.ds.data.first()[LANG] ?: "ru"
    suspend fun setLang(ctx: Context, v: String) { ctx.ds.edit { it[LANG] = v } }
    suspend fun acceptedEula(ctx: Context): Boolean =
        ctx.ds.data.first()[ACCEPTED_EULA] ?: false
    suspend fun setAcceptedEula(ctx: Context, v: Boolean) {
        ctx.ds.edit { it[ACCEPTED_EULA] = v }
    }
    suspend fun acceptedAge(ctx: Context): Boolean =
        ctx.ds.data.first()[ACCEPTED_AGE] ?: false
    suspend fun setAcceptedAge(ctx: Context, v: Boolean) {
        ctx.ds.edit { it[ACCEPTED_AGE] = v }
    }
    /** "system" (по умолчанию), "light" или "dark". */
    suspend fun themeMode(ctx: Context): String =
        ctx.ds.data.first()[THEME_MODE] ?: "system"
    suspend fun setThemeMode(ctx: Context, v: String) {
        ctx.ds.edit { it[THEME_MODE] = v }
    }
    /** Включена ли защита (юзер не отключал её вручную). По умолчанию true. */
    suspend fun protectionEnabled(ctx: Context): Boolean =
        ctx.ds.data.first()[PROTECTION_ENABLED] ?: true
    suspend fun setProtectionEnabled(ctx: Context, v: Boolean) {
        ctx.ds.edit { it[PROTECTION_ENABLED] = v }
    }
}
