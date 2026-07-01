package md.scamguard

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Применяет язык интерфейса к ContextWrapper.
 * Используется в attachBaseContext() обеих Activity.
 */
object LocaleHelper {
    private val SUPPORTED = setOf("ru", "ro", "en")

    fun wrap(ctx: Context, langCode: String): Context {
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(locale)
        return ctx.createConfigurationContext(cfg)
    }

    /**
     * Возвращает сохранённый язык. Если ничего не сохранено — берём системный
     * (если это ru/ro/en), иначе fallback на "ru" — основная аудитория Молдовы.
     */
    fun readSavedLang(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("lang_cache", Context.MODE_PRIVATE)
        val saved = prefs.getString("lang", null)
        if (saved != null && saved in SUPPORTED) return saved
        // Первый запуск — определяем по системе
        val sys = Locale.getDefault().language.lowercase()
        return if (sys in SUPPORTED) sys else "ru"
    }

    fun cache(ctx: Context, lang: String) {
        ctx.getSharedPreferences("lang_cache", Context.MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
    }
}