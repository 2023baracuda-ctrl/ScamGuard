package md.scamguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class UpdateInfo(val available: Boolean, val latestVersion: String,
                      val downloadUrl: String, val notes: String)

/**
 * Качает JSON с описанием последней версии и сравнивает с BuildConfig.VERSION_NAME.
 * URL ниже — твой репозиторий, ветка main, файл version.json.
 */
object Updater {
    // ОТРЕДАКТИРУЙ под свой репозиторий!
    private const val URL_VERSION_JSON =
        "https://raw.githubusercontent.com/2023baracuda-ctrl/ScamGuard/main/version.json"

    suspend fun check(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val txt = URL(URL_VERSION_JSON).readText()
            val o = JSONObject(txt)
            val latest = o.getString("version")
            val link = o.getString("apk")
            val notes = o.optString("notes", "")
            UpdateInfo(isNewer(latest, currentVersion), latest, link, notes)
        } catch (_: Throwable) {
            UpdateInfo(false, currentVersion, "", "")
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val l = local.split(".").mapNotNull { it.toIntOrNull() }
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val ri = r.getOrElse(i) { 0 }
            val li = l.getOrElse(i) { 0 }
            if (ri != li) return ri > li
        }
        return false
    }

    fun openInBrowser(ctx: Context, url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
