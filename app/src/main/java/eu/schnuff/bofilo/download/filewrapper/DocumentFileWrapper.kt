package eu.schnuff.bofilo.download.filewrapper

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import eu.schnuff.bofilo.Constants

class DocumentFileWrapper(
    private val context: Context,
    private val file: DocumentFile
): FileWrapper {
    override val uri: Uri
        get() = file.uri

    override val name: String
        get() = file.name ?: "NAN"
    override val isDirectory: Boolean
        get() = file.isDirectory
    override val isFile: Boolean
        get() = file.isFile

    override val lastModified: Long
        get() = file.lastModified()

    override fun createFile(mimeType: String, filename: String): FileWrapper {
        return DocumentFileWrapper(context, file.createFile(mimeType, filename)!!)
    }

    override fun delete() {
        file.delete()
    }

    private val isCached: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.PREF_CACHE_SAF, false)
    private var childCache: Map<String, String>? = null

    override fun getChild(filename: String): FileWrapper? {
        val isCached = this.isCached
        childCache?.also {
            if (isCached) {
                if (it.containsKey(filename)) {
                    val df = DocumentFile.fromSingleUri(context, DocumentsContract.buildDocumentUriUsingTree(uri, it[filename]))
                    if (df != null)
                        return DocumentFileWrapper(context, df)
                    return null
                }
                return null
            }
        }
        val baseUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getDocumentId(uri)
        )
        // Log.d("dfw", "querying $uri --> $baseUri")
        context.contentResolver.query(baseUri, arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID
        ), DocumentsContract.Document.COLUMN_DISPLAY_NAME + " = ?", arrayOf(filename), null)?.use {
            val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            if (isCached && childCache == null) {
                val map = HashMap<String, String>(it.count)
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex)
                    val childId = it.getString(idIndex)
                    map[name] = childId
                }
                childCache = map
                it.moveToFirst()
                it.moveToPrevious()
            }
            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                if (filename == name) {
                    val childId = it.getString(idIndex)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, childId)
                    return DocumentFileWrapper(context, DocumentFile.fromSingleUri(context, childUri)!!)
                }
            }
        }
        return null
    }
}