package eu.schnuff.bofilo.settings

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import eu.schnuff.bofilo.Constants
import eu.schnuff.bofilo.Helpers
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.R
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
        private val defaultSrcDirectoryPreference
            get() = findPreference<Preference>(Constants.PREF_DEFAULT_SRC_DIRECTORY)!!
        private val personaliniPreference: Preference
            get() = findPreference(Constants.PREF_PERSONALINI)!!
        private val sharedPreferences: SharedPreferences
            get() = PreferenceManager.getDefaultSharedPreferences(context!!.applicationContext)

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Replace content with the settings configured in root_preferences.xml
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            setSummary()


            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            // Add custom actions...
            // ... to get the "default directory"
            defaultDirectoryPreference.setOnPreferenceClickListener {
                startActivityForResult(intent, PICK_DEFAULT_DIRECTORY)
                true
            }
            defaultSrcDirectoryPreference.setOnPreferenceClickListener {
                startActivityForResult(intent, PICK_DEFAULT_SRC_DIRECTORY)
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

            val defaultSrcDirectory = sharedPreferences.getString(Constants.PREF_DEFAULT_SRC_DIRECTORY, null)
            defaultSrcDirectoryPreference.summary = if (defaultSrcDirectory == null) {
                getString(R.string.preference_general_choose_src_directory_summary_default)
            } else {
                getString(R.string.preference_general_choose_src_directory_summary).format(defaultSrcDirectory)
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode == RESULT_OK && data != null && data.data != null) when (requestCode) {
                PICK_DEFAULT_DIRECTORY, PICK_DEFAULT_SRC_DIRECTORY -> data.data?.let { uri ->
                    val settingName = when (requestCode) {
                        PICK_DEFAULT_DIRECTORY -> Constants.PREF_DEFAULT_DIRECTORY
                        PICK_DEFAULT_SRC_DIRECTORY -> Constants.PREF_DEFAULT_SRC_DIRECTORY
                        else -> throw IllegalStateException("Name not found.")
                    }

                    // I have no idea where the file is on the filesystem (if it is even there and not in the cloud)
                    DocumentFile.fromTreeUri(context!!.applicationContext, uri)?.let {
                        // Make access to the "default directory" permanent
                        val takeFlags: Int = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        val resolver: ContentResolver = context!!.contentResolver
                        resolver.takePersistableUriPermission(data.data!!, takeFlags)

                        sharedPreferences.edit(true) {
                            putString(settingName, it.uri.toString())
                        }

                        setSummary()
                    }

                    val externPath = Helpers.FileInformation.getPath(context!!, uri)
                    if (externPath != null) {
                        // I found the file on the filesystem.
                        // request permission to access it.
                        Log.d(TAG, "Extern path found, now requesting permissions.")
                        TedPermission
                            .with(context!!)
                            .setPermissionListener(object : PermissionListener {
                                override fun onPermissionGranted() {
                                    sharedPreferences.edit(true) {
                                        putString(settingName, Uri.fromFile(File(externPath)).toString())
                                    }

                                    setSummary()
                                }

                                override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {}
                            })
                            .setRationaleMessage(R.string.permission_rationale)
                            .setDeniedMessage(R.string.permission_denied)
                            .setPermissions(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ).check()
                    }
                }
                PICK_PERSONALINI -> {
                    DocumentFile.fromSingleUri(requireContext().applicationContext, data.data!!)?.let {
                        // copy personal.ini into the data files directory.
                        requireContext().contentResolver.copyFile(it.uri, File(requireContext().filesDir, "personal.ini").toUri())
                    }
                }
            }
        }

        companion object {
            const val PICK_DEFAULT_DIRECTORY = 2
            const val PICK_DEFAULT_SRC_DIRECTORY = 3
            const val PICK_PERSONALINI = 4
            const val TAG = "Settings"
        }
    }
}