package eu.schnuff.bofilo

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

object Constants {
    const val PREF_PERSONALINI = "pref_dev_personalini"
    const val PREF_DEFAULT_DIRECTORY = "pref_default_directory"
    const val PREF_SAVE_CACHE = "pref_save_cache"
    const val PREF_IS_ADULT = "pref_is_adult"
    const val MIME_EPUB = "application/epub+zip"
    const val MIME_INI = "*/*"
}

fun ContentResolver.copyFile(src: Uri, dst: Uri) {
    val input = BufferedInputStream(this.openInputStream(src)!!)
    val output = BufferedOutputStream(this.openOutputStream(dst)!!)
    val buffer = ByteArray(32 * 1024)
    var len: Int
    while (input.read(buffer).also{ len = it } > 0) {
        output.write(buffer, 0, len)
    }
    input.close()
    output.close()
}