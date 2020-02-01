package eu.schnuff.bofilo.settings

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import eu.schnuff.bofilo.Constants
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.copyFile
import java.io.File


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Replace content with the settings configured in root_preferences.xml
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private val defaultDirectoryPreference: Preference
            get() = findPreference(Constants.PREF_DEFAULT_DIRECTORY)!!
        private val personaliniPreference: Preference
            get() = findPreference(Constants.PREF_PERSONALINI)!!
        private val sharedPreferences: SharedPreferences
            get() = PreferenceManager.getDefaultSharedPreferences(context!!.applicationContext)

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Replace content with the settings configured in root_preferences.xml
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            setSummary()

            // Add custom actions...
            // ... to get the "default directory"
            defaultDirectoryPreference.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                startActivityForResult(intent, PICK_DEFAULT_DIRECTORY)

                true
            }

            // ... to get the personal.ini
            personaliniPreference.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = Constants.MIME_INI
                }

                startActivityForResult(intent, PICK_PERSONALINI)

                true
            }
        }

        // Sets summaries where no automatic summary provider is applicable
        private fun setSummary()  {
            val defaultDirectory = sharedPreferences.getString(Constants.PREF_DEFAULT_DIRECTORY, null)
            defaultDirectoryPreference.summary = if (defaultDirectory == null) {
                getString(R.string.preference_general_choose_directory_summary_default)
            } else {
                getString(R.string.preference_general_choose_directory_summary).format(defaultDirectory)
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode == RESULT_OK && data != null && data.data != null) when (requestCode) {
                PICK_DEFAULT_DIRECTORY -> {
                    DocumentFile.fromTreeUri(context!!.applicationContext, data.data!!)?.let {
                        // Make access to the "default directory" permanent
                        val takeFlags: Int = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        val resolver: ContentResolver = context!!.contentResolver
                        resolver.takePersistableUriPermission(data.data!!, takeFlags)

                        sharedPreferences.edit(true) {
                            putString(Constants.PREF_DEFAULT_DIRECTORY, it.uri.toString())
                        }

                        setSummary()
                    }
                }
                PICK_PERSONALINI -> {
                    DocumentFile.fromSingleUri(context!!.applicationContext, data.data!!)?.let {
                        // copy personal.ini into the data files directory.
                        context!!.contentResolver.copyFile(it.uri, File(context!!.filesDir, "personal.ini").toUri())
                    }
                }
            }
        }

        companion object {
            const val PICK_DEFAULT_DIRECTORY = 2
            const val PICK_PERSONALINI = 3
        }
    }
}