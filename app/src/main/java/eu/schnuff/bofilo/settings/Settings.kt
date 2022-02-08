package eu.schnuff.bofilo.settings

import android.content.Context
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import eu.schnuff.bofilo.Constants
import eu.schnuff.bofilo.R

class Settings (context: Context) {
    private val p = PreferenceManager.getDefaultSharedPreferences(context)
    private val defaultWebViewUserAgent = context.getString(R.string.webview_user_agent_default)

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

    val webViewUserAgent
    get() = p.getString(Constants.PREF_WEBVIEW_USER_AGENT, defaultWebViewUserAgent)
}