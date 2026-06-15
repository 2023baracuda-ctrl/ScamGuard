package md.scamguard

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

data class SmsAnalysis(
    val hasOtp: Boolean,
    val keywordsHit: List<String>,
    val urls: List<String>,
    val claimedBrand: String?,
    val suspicious: Boolean,
    val reason: String
)

enum class Threat { NONE, LOW, HIGH }

object CallContext {
    @Volatile private var state: Int = 0
    @Volatile private var lastChangeMs: Long = 0L
    @Volatile var lastNumber: String = ""
    fun markState(s: Int) {
        state = s
        lastChangeMs = System.currentTimeMillis()
    }
    fun markNumber(n: String?) { if (!n.isNullOrBlank()) lastNumber = n }
    fun callActive(): Boolean = state == 1 || state == 2
    fun recentlyEnded(ms: Long): Boolean =
        state == 0 && lastChangeMs > 0 && System.currentTimeMillis() - lastChangeMs < ms
    fun activeOrRecent(ms: Long): Boolean = callActive() || recentlyEnded(ms)
}

object LocalDb {
    val codeRegex = Regex("(?<!\\d)\\d{3,8}(?!\\d)")
    val otpKeywords = listOf(
        "код", "одноразов", "никому", "не сообщайте", "пароль",
        "подтвержд", "верифик",
        "cod", "parol", "nu comunica", "verificare", "unic",
        "confirmare", "autentificare",
        "code", "otp", "verification", "one-time", "do not share",
        "password", "verify", "auth", "confirm"
    )
    val standaloneOtpMarkers = listOf("otp", "код:", "code:", "cod:", "pin:")
    val pressureKeywords = listOf(
        "срочно", "немедленно", "блокировк", "подозрительн",
        "urgent", "blocat", "suspect", "imediat"
    )
    val brands: Map<String, String> = mapOf(
        "victoriabank" to "victoriabank.md",
        "maib" to "maib.md",
        "moldindconbank" to "micb.md",
        "micb" to "micb.md",
        "mobiasbanca" to "mobiasbanca.md",
        "ocn" to "ocn.md",
        "energocom" to "energocom.md",
        "posta moldovei" to "posta.md",
        "orange" to "orange.md",
        "moldtelecom" to "moldtelecom.md",
        "moldcell" to "moldcell.md"
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

object Detector {
    fun analyze(body: String): SmsAnalysis {
        val low = body.lowercase()
        val keywordHit = LocalDb.otpKeywords.any { low.contains(it) }
        val markerHit  = LocalDb.standaloneOtpMarkers.any { low.contains(it) }
        val hasCode    = LocalDb.codeRegex.containsMatchIn(body)
        val hasOtp = (keywordHit || markerHit) && hasCode
        val urls = LocalDb.extractUrls(body)
        val claimed = LocalDb.findClaimedBrand(body)
        val pressure = LocalDb.pressureKeywords.filter { low.contains(it) }
        var urlMismatch = false
        if (claimed != null && urls.isNotEmpty()) {
            val official = LocalDb.brands[claimed]!!
            urlMismatch = urls.none { LocalDb.domainOf(it).endsWith(official) }
        }
        val suspicious = hasOtp || urlMismatch ||
                (claimed != null && pressure.isNotEmpty())
        val reason = buildString {
            if (hasOtp) append("одноразовый код в сообщении; ")
            if (urlMismatch) append("ссылка не на официальный домен (${LocalDb.brands[claimed]}); ")
            if (claimed != null && !urlMismatch && hasOtp)
                append("ссылается на «$claimed»; ")
            if (pressure.isNotEmpty()) append("использует приёмы давления; ")
        }.trim().trimEnd(';')
        val hits = (LocalDb.otpKeywords + LocalDb.standaloneOtpMarkers + LocalDb.pressureKeywords)
            .filter { low.contains(it) }
        return SmsAnalysis(hasOtp, hits, urls, claimed, suspicious,
            reason.ifBlank { "Обычное сообщение" })
    }
    fun threatLevel(a: SmsAnalysis): Threat = when {
        a.hasOtp && CallContext.activeOrRecent(120_000) -> Threat.HIGH
        a.suspicious -> Threat.LOW
        else -> Threat.NONE
    }
}

object History {
    private val Context.ds by preferencesDataStore("history")
    private val KEY = stringSetPreferencesKey("events_v2")

    data class Event(
        val time: Long, val level: String, val sender: String,
        val callNumber: String, val reason: String, val smsBody: String,
        val dismissed: Boolean = false
    )
    private fun ser(e: Event) = listOf(
        e.time.toString(), e.level, b64(e.sender), b64(e.callNumber),
        b64(e.reason), b64(e.smsBody), e.dismissed.toString()
    ).joinToString("\u0001")
    private fun deser(s: String): History.Event? {
        val p = s.split("\u0001"); if (p.size < 7) return null
        return Event(p[0].toLongOrNull() ?: return null,
            p[1], ub64(p[2]), ub64(p[3]), ub64(p[4]), ub64(p[5]), p[6].toBoolean())
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
}

object Prefs {
    private val Context.ds by preferencesDataStore("prefs")
    private val LANG = stringPreferencesKey("lang")
    private val NUMS = stringSetPreferencesKey("trusted_nums")
    suspend fun lang(ctx: Context): String = ctx.ds.data.first()[LANG] ?: "ru"
    suspend fun setLang(ctx: Context, v: String) { ctx.ds.edit { it[LANG] = v } }
    suspend fun nums(ctx: Context): Set<String> = ctx.ds.data.first()[NUMS] ?: emptySet()
    suspend fun addNum(ctx: Context, n: String) {
        ctx.ds.edit { it[NUMS] = (it[NUMS] ?: emptySet()) + n }
    }
    suspend fun removeNum(ctx: Context, n: String) {
        ctx.ds.edit { it[NUMS] = (it[NUMS] ?: emptySet()) - n }
    }
}
