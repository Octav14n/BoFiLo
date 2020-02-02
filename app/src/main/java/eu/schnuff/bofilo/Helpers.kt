package eu.schnuff.bofilo

import android.content.ContentResolver
import android.net.Uri

object Helpers {

    // This one helps to copy file content between uris by using ContentResolver.
    // We can copy from and to DocumentFiles and normal "file://..." (for cacheDir) uris with this function.
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
}
