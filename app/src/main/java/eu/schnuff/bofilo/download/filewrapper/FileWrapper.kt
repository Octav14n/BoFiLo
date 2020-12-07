package eu.schnuff.bofilo.download.filewrapper

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import eu.schnuff.bofilo.Constants
import java.io.File

interface FileWrapper {
    val uri: Uri
    val name: String
    val lastModified: Long
    val isDirectory: Boolean
    val isFile: Boolean

    fun createFile(mimeType: String, filename: String): FileWrapper
    fun getChild(filename: String): FileWrapper?
    fun delete()

    

    companion object {
        fun fromUri(context: Context, uri: Uri) : FileWrapper {
            val isCached = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.PREF_CACHE_SAF, false)
            if (isCached && !cache.containsKey(uri))
                cache[uri] = fromUriImpl(context, uri)
            return cache[uri]!!
        }
        private fun fromUriImpl(context: Context, uri: Uri) : FileWrapper {
            return when (uri.scheme) {
                ContentResolver.SCHEME_FILE, null -> OSFileWrapper(File(uri.path!!))
                ContentResolver.SCHEME_CONTENT -> DocumentFileWrapper(context, if(DocumentFile.isDocumentUri(context, uri)) {
                    DocumentFile.fromSingleUri(context, uri)
                } else {
                    DocumentFile.fromTreeUri(context, uri)
                }!!)
                else -> throw IllegalArgumentException("uri '$uri', scheme '${uri.scheme}' is not supported.")
            }
        }
        private val cache = HashMap<Uri, FileWrapper>()
    }
}