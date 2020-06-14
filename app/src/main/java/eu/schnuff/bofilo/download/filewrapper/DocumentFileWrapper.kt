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
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID
        ), null, null, null)?.use {
            val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                // Log.d("dfw", "get child: $filename <-> $name (uri: ${it.getString(idIndex)})")
                if (filename == name) {
                    val childId = it.getString(idIndex)
                    val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, childId)
                    return DocumentFileWrapper(context, DocumentFile.fromSingleUri(context, childUri)!!)
                }
            }
        }
        return null
    }
}