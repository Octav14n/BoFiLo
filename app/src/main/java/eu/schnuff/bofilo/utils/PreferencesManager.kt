package eu.schnuff.bofilo.utils

import android.content.Context
import androidx.preference.PreferenceManager

class PreferencesManager(private var mContext: Context) {

    private var mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
    private var mPreferencesEditor = mPreferences!!.edit()

    fun getVersionForLastIntro(): Int {
        return mPreferences.getInt("version_number", 0)
    }

    fun setVersionForLastIntro(versionNumber: Int) {
        mPreferencesEditor.putInt("version_number", versionNumber)
        mPreferencesEditor.apply()
    }
}