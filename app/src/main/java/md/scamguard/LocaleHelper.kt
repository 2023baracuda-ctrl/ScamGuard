package md.scamguard

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Применяет язык интерфейса к ContextWrapper.
 * Используется в attachBaseContext() обеих Activity.
 */
object LocaleHelper {
    fun wrap(ctx: Context, langCode: String): Context {
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(locale)
        return ctx.createConfigurationContext(cfg)
    }
    fun readSavedLang(ctx: Context): String {
        // лёгкое чтение через прямой SharedPreferences (DataStore асинхронный)
        return ctx.getSharedPreferences("lang_cache", Context.MODE_PRIVATE)
            .getString("lang", "ru") ?: "ru"
    }
    fun cache(ctx: Context, lang: String) {
        ctx.getSharedPreferences("lang_cache", Context.MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
    }
}

