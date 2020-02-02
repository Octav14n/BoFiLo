package eu.schnuff.bofilo.download.filewrapper

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class DocumentFileWrapper(private val file: DocumentFile): FileWrapper {
    override val uri: Uri
        get() = file.uri
    override val lastModified: Long
        get() = file.lastModified()

    override fun createFile(mimeType: String, filename: String): FileWrapper {
        return DocumentFileWrapper(file.createFile(mimeType, filename)!!)
    }

    override fun getChild(filename: String): FileWrapper? {
        val f = file.findFile(filename)
        if (f != null)
            return DocumentFileWrapper(f)
        return null
    }
}