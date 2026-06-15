package md.scamguard

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Что нашёл детектор в SMS */
data class SmsAnalysis(
    val hasOtp: Boolean,
    val keywordsHit: List<String>,
    val urls: List<String>,
    val claimedBrand: String?,        // банк/сервис, на который ссылается текст
    val suspicious: Boolean,          // итоговый вердикт по содержимому SMS
    val reason: String                // короткое объяснение для UI
)

enum class Threat { NONE, LOW, HIGH }

/** Контекст звонка для связки «SMS пришла во время звонка».
 *  Источник — пассивный TelephonyCallback из CallWatchService. */
object CallContext {
    @Volatile private var state: Int = 0          // 0 IDLE / 1 RINGING / 2 OFFHOOK
    @Volatile private var lastChangeMs: Long = 0L
    fun markState(s: Int) {
        state = s
        lastChangeMs = System.currentTimeMillis()
    }
    /** Идёт активный звонок прямо сейчас (поднята трубка или звонит) */
    fun callActive(): Boolean = state == 1 || state == 2
    /** Звонок только что был (в течение последних N миллисекунд) */
    fun recentlyEnded(ms: Long): Boolean =
        state == 0 && lastChangeMs > 0 && System.currentTimeMillis() - lastChangeMs < ms
    /** Удобный комбинированный сигнал */
    fun activeOrRecent(ms: Long): Boolean = callActive() || recentlyEnded(ms)
}

/** Локальная база ключевых слов / брендов / доменов. Никакой сети. */
object LocalDb {

    // регэксп: «изолированное» число 4–8 цифр (типичный OTP)
    val codeRegex = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

    // ключевые слова RU + RO + EN
    val otpKeywords = listOf(
        // ru
        "код", "одноразов", "никому", "не сообщайте", "пароль", "подтвержд", "верифик",
        // ro
        "cod", "parol", "nu comunica", "verificare", "unic",
        // en
        "code", "otp", "verification", "one-time", "do not share"
    )

    // соц-инженерия: слова давления / срочности
    val pressureKeywords = listOf(
        "срочно", "немедленно", "блокировк", "подозрительн", "опера", "сотрудник",
        "urgent", "blocat", "suspect", "imediat"
    )

    // известные «имена», за которые часто прячутся мошенники в Молдове
    // (claimedBrand → официальный домен)
    val brands: Map<String, String> = mapOf(
        "victoriabank" to "victoriabank.md",
        "maib"          to "maib.md",
        "moldindconbank" to "micb.md",
        "mobias"        to "mobiasbanca.md",
        "ocn"           to "ocn.md",
        "energocom"     to "energocom.md",
        "moldova-agroindbank" to "maib.md",
        "posta"         to "posta.md"
    )

    fun extractUrls(text: String): List<String> =
        Regex("""https?://\S+|[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/\S*)?""")
            .findAll(text).map { it.value.trimEnd('.', ',', ')') }.toList()

    fun domainOf(url: String): String =
        url.removePrefix("http://").removePrefix("https://")
            .substringBefore('/').lowercase()

    fun findClaimedBrand(text: String): String? {
        val lower = text.lowercase()
        return brands.keys.firstOrNull { lower.contains(it) }
    }
}

/** Главный анализатор SMS */
object Detector {

    fun analyze(body: String): SmsAnalysis {
        val low = body.lowercase()
        val hits = (LocalDb.otpKeywords + LocalDb.pressureKeywords).filter { low.contains(it) }
        val hasCode = LocalDb.codeRegex.containsMatchIn(body)
        val urls = LocalDb.extractUrls(body)
        val claimed = LocalDb.findClaimedBrand(body)

        // подозрительность ссылок: «выдаёт себя за бренд X», а домен не совпадает
        var urlMismatch = false
        if (claimed != null && urls.isNotEmpty()) {
            val official = LocalDb.brands[claimed]!!
            urlMismatch = urls.none { LocalDb.domainOf(it).endsWith(official) }
        }

        val hasOtp = hasCode && LocalDb.otpKeywords.any { low.contains(it) }

        val suspicious = hasOtp || urlMismatch ||
            (claimed != null && LocalDb.pressureKeywords.any { low.contains(it) })

        val reason = buildString {
            if (hasOtp) append("в сообщении одноразовый код; ")
            if (urlMismatch) append("ссылка не ведёт на официальный домен ${claimed?.let { LocalDb.brands[it] }}; ")
            if (claimed != null && !urlMismatch && hasOtp) append("ссылается на «$claimed»; ")
            if (hits.any { it in LocalDb.pressureKeywords }) append("использует приёмы давления; ")
        }.trim().trimEnd(';')

        return SmsAnalysis(hasOtp, hits, urls, claimed, suspicious, reason.ifBlank { "Обычное сообщение" })
    }

    /** Итоговая угроза с учётом контекста звонка */
    fun threatLevel(a: SmsAnalysis): Threat = when {
        a.hasOtp && CallContext.activeOrRecent(60_000) -> Threat.HIGH   // код во время/сразу после звонка — главный сигнал
        a.suspicious -> Threat.LOW                                    // подозрительный текст без звонка — мягко
        else -> Threat.NONE
    }
}

/** Локальный журнал подозрительных событий (DataStore preferences, без сети) */
object History {
    private val Context.ds by preferencesDataStore("history")
    private val KEY = stringSetPreferencesKey("events")

    suspend fun add(ctx: Context, line: String) {
        ctx.ds.edit { it[KEY] = (it[KEY] ?: emptySet()) + "${System.currentTimeMillis()}|$line" }
    }
    suspend fun list(ctx: Context): List<Pair<Long, String>> =
        (ctx.ds.data.first()[KEY] ?: emptySet())
            .mapNotNull { e -> e.substringBefore('|').toLongOrNull()?.let { it to e.substringAfter('|') } }
            .sortedByDescending { it.first }
}
