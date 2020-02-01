package eu.schnuff.bofilo

import android.content.ContentResolver
import android.net.Uri

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