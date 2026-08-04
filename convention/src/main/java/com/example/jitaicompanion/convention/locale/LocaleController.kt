package com.example.jitaicompanion.convention.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * One language the apps can be shown in.
 *
 * @param tag BCP-47 tag. Must match the `res/values-<tag>/` qualifier and the entry in
 *   `res/xml/locales_config.xml`.
 * @param nativeName name in the language itself — shown in the picker, so someone who cannot
 *   read the current UI language can still find their own.
 */
data class AppLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String,
)

/**
 * The single place listing which languages the phone and watch apps ship.
 *
 * ## Adding a language
 * 1. Add `mobile/src/main/res/values-<tag>/strings.xml` and
 *    `wear/src/main/res/values-<tag>/strings.xml`, translated from the `values/` originals.
 * 2. Add one `<locale android:name="<tag>"/>` line to each module's
 *    `res/xml/locales_config.xml`.
 * 3. Add one line to [SUPPORTED] below.
 *
 * Nothing else changes: Android resolves the resources, and the in-app picker is built from
 * [SUPPORTED], so the new language appears automatically.
 */
object AppLanguages {

    /** Sentinel meaning "follow the system language" — the default for a fresh install. */
    const val SYSTEM = "system"

    /** Shipped languages, in picker order. */
    val SUPPORTED: List<AppLanguage> = listOf(
        AppLanguage("en", "English", "English"),
        AppLanguage("de", "Deutsch", "German"),
        AppLanguage("nl", "Nederlands", "Dutch"),
    )
}

/**
 * Reads and applies the per-app UI language for both the phone and the watch app.
 *
 * Two mechanisms, split by API level, because mixing them causes the in-app choice to fight
 * the system one:
 *
 * - **API 33+** (all of Wear, most phones): the platform [LocaleManager] owns the per-app
 *   locale. It is the single source of truth, it survives reinstall-level config changes, it
 *   recreates activities on change, and it is what Android's own
 *   *Settings → Apps → … → Language* screen edits. We read and write it directly, and we do
 *   **not** wrap contexts — the framework has already applied the locale.
 * - **API 29–32** (older phones only): no platform support, so we persist the choice ourselves
 *   and override the locale on each `Context` via [wrap].
 *
 * Activities pick this up by overriding `attachBaseContext` — see [wrap].
 */
object LocaleController {

    private const val PREFS = "app_locale"
    private const val KEY_TAG = "language_tag"

    /**
     * The active language tag, or [AppLanguages.SYSTEM] when following the system language.
     *
     * On API 33+ this reflects the platform setting, so a change made in Android's settings
     * screen is picked up here too.
     */
    fun currentTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
            val tag = locales?.takeIf { !it.isEmpty }?.get(0)?.language
            return tag ?: AppLanguages.SYSTEM
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, AppLanguages.SYSTEM) ?: AppLanguages.SYSTEM
    }

    /** The [AppLanguage] currently in effect, or `null` when following the system language. */
    fun currentLanguage(context: Context): AppLanguage? {
        val tag = currentTag(context)
        return AppLanguages.SUPPORTED.firstOrNull { it.tag == tag }
    }

    /**
     * Switches the UI language and persists it.
     *
     * Pass [AppLanguages.SYSTEM] to go back to following the system language.
     *
     * On API 33+ the platform recreates the visible activities itself. Below that the caller
     * must recreate — use [applyAndRecreate] from an Activity to get both behaviours in one
     * call.
     */
    fun setLanguage(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (tag == AppLanguages.SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(tag)
            }
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = locales
            return
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, tag)
            .apply()
    }

    /**
     * Applies [tag] and makes it visible immediately.
     *
     * On API 33+ the platform handles the recreation, so we skip our own to avoid recreating
     * twice (which would flash the screen and drop Compose state a second time).
     */
    fun applyAndRecreate(activity: Activity, tag: String) {
        setLanguage(activity, tag)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            activity.recreate()
        }
    }

    /**
     * Returns a [Context] whose resources resolve in the chosen language.
     *
     * Call from `attachBaseContext` in every Activity, and around any component that loads
     * user-visible strings off the application context (notifications from a Service, for
     * example — `applicationContext` is never wrapped by the framework).
     *
     * On API 33+ this is a deliberate no-op: the platform has already applied the per-app
     * locale to the incoming context, and re-applying a stale stored value here would
     * override a language the user picked in Android's own settings.
     */
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context

        val tag = currentTag(context)
        if (tag == AppLanguages.SYSTEM) return context

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return ContextWrapper(context.createConfigurationContext(config))
    }
}
