package eu.schnuff.bofilo

import android.content.ContentResolver
import android.net.Uri

object Constants {
    const val PREF_PERSONALINI = "pref_dev_personalini"
    const val PREF_DEFAULT_DIRECTORY = "pref_default_directory"
    const val PREF_SAVE_CACHE = "pref_save_cache"
    const val PREF_IS_ADULT = "pref_is_adult"
    const val MIME_EPUB = "application/epub+zip"
    const val MIME_INI = "*/*"
}

fun ContentResolver.copyFile(src: Uri, dst: Uri) {
    //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    //    DocumentsContract.copyDocument(this, src, dst) // cant copy to cache/data directory -.-
    //} else {
    val input = this.openInputStream(src)!!.buffered()
    val output = this.openOutputStream(dst)!!.buffered()
    input.copyTo(output)
    input.close()
    output.close()
    //}
}