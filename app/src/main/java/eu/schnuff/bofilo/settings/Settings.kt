package eu.schnuff.bofilo.settings

import android.content.Context
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import eu.schnuff.bofilo.Constants

class Settings (context: Context) {
    private val p = PreferenceManager.getDefaultSharedPreferences(context)

    val showConsole
    get() = p.getBoolean(Constants.PREF_SHOW_CONSOLE, true)

    val isAdult
    get() = p.getBoolean(Constants.PREF_IS_ADULT, false)

    val saveCache
    get() = p.getBoolean(Constants.PREF_SAVE_CACHE, false)

    val dstDir
    get() = p.getString(Constants.PREF_DEFAULT_DIRECTORY, null)?.toUri()

    val srcDir
    get() = p.getString(Constants.PREF_DEFAULT_SRC_DIRECTORY, null)?.toUri()
}