package eu.schnuff.bofilo.settings

import android.R.attr
import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.*
import eu.schnuff.bofilo.Constants
import eu.schnuff.bofilo.R


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        val defaultDirectoryPreference: Preference
            get() = findPreference<Preference>(Constants.PREF_DEFAULT_DIRECTORY)!!

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val sharedPreference = PreferenceManager.getDefaultSharedPreferences(context!!.applicationContext)
            val defaultDirectory = sharedPreference.getString(Constants.PREF_DEFAULT_DIRECTORY, null)
            defaultDirectoryPreference.summary = if (defaultDirectory == null) {
                getString(R.string.choose_directory_summary_default)
            } else {
                getString(R.string.choose_directory_summary).format(defaultDirectory)
            }
            defaultDirectoryPreference.setOnPreferenceClickListener {

                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                startActivityForResult(intent, PICK_DEFAULT_DIRECTORY)

                true
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode == RESULT_OK) when (requestCode) {
                PICK_DEFAULT_DIRECTORY -> if (data != null && data.data != null) {
                    DocumentFile.fromTreeUri(context!!.applicationContext, data.data!!)?.run {
                        val directory = this

                        val takeFlags: Int = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        val resolver: ContentResolver = context!!.contentResolver
                        resolver.takePersistableUriPermission(data.data!!, takeFlags)

                        val preference = PreferenceManager.getDefaultSharedPreferences(context!!.applicationContext)
                        preference.edit(true) {
                            putString(Constants.PREF_DEFAULT_DIRECTORY, directory.uri.toString())
                        }
                    }
                }
            }
        }

        companion object {
            const val PICK_DEFAULT_DIRECTORY = 2
        }
    }
}