package eu.schnuff.bofilo.download.filewrapper

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

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

    override fun getChild(filename: String): FileWrapper? {
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