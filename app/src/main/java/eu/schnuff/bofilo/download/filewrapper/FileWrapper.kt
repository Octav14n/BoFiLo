package eu.schnuff.bofilo.download.filewrapper

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

interface FileWrapper {
    val uri: Uri
    val lastModified: Long

    fun createFile(mimeType: String, filename: String): FileWrapper
    fun getChild(filename: String): FileWrapper?

    companion object {
        fun fromUri(context: Context, uri: Uri) : FileWrapper {
            return when {
                "file" == uri.scheme -> OSFileWrapper(File(uri.path))
                DocumentsContract.isDocumentUri(context, uri) ->
                    DocumentFileWrapper(DocumentFile.fromTreeUri(context, uri)!!)
                    //DocumentFileWrapper(DocumentFile.fromSingleUri(context, uri)!!)
                else -> throw IllegalArgumentException("uri is not supported.")
            }
        }
    }
}