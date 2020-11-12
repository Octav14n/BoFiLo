package eu.schnuff.bofilo.download.filewrapper

import android.net.Uri
import androidx.core.net.toUri
import java.io.File

class OSFileWrapper(private val file: File): FileWrapper {
    override val uri: Uri
        get() = file.toUri()
    override val name: String
        get() = file.name
    override val isDirectory: Boolean
        get() = file.isDirectory
    override val isFile: Boolean
        get() = file.isFile
    override val lastModified: Long
        get() = file.lastModified()

    override fun createFile(mimeType:String, filename: String): FileWrapper {
        return OSFileWrapper(File(file, filename).apply {
            createNewFile()
        })
    }

    override fun delete() {
        file.delete()
    }

    override fun getChild(filename: String): FileWrapper? {
        val f = File(file, filename)
        if (f.exists())
            return OSFileWrapper(f)
        return null
    }
}