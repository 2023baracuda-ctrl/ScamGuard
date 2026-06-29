package md.scamguard

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Минимальный сетевой клиент: единственный исходящий запрос — анонимное
 * подтверждение приёма EULA. Текст SMS и любые персональные данные
 * никогда не покидают устройство.
 */
object Reporter {

    private const val BASE_URL = "https://app.scamguardrm.workers.dev"

    const val CURRENT_EULA_VERSION = "1.1"

    /** Отправляется один раз при первом принятии EULA. */
    fun consent(ctx: Context) {
        val hash = deviceHash(ctx)
        post("$BASE_URL/consent", JSONObject().apply {
            put("eulaVersion", CURRENT_EULA_VERSION)
            put("deviceHash", hash)
        })
    }

    /**
     * SHA-256 от ANDROID_ID + соль. ANDROID_ID используется только как
     * необратимый источник энтропии для consent receipt — никаких
     * персональных данных за ним не стоит.
     */
    @SuppressLint("HardwareIds")
    private fun deviceHash(ctx: Context): String {
        val id = Settings.Secure.getString(ctx.contentResolver,
            Settings.Secure.ANDROID_ID) ?: "unknown"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(("sg_salt_v1::$id").toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun post(url: String, body: JSONObject) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toString().toByteArray()) }
                    inputStream.use { it.readBytes() }
                    disconnect()
                }
            }
        }
    }
}
