package eu.schnuff.bofilo

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.FileNotFoundException
import java.io.FileOutputStream

private const val CONTENT_SCHEME = ContentResolver.SCHEME_CONTENT

object Helpers {

    // This one helps to copy file content between uris by using ContentResolver.
    // We can copy from and to DocumentFiles and normal "file://..." (for cacheDir) uris with this function.
    fun ContentResolver.copyFile(src: Uri, dst: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && src.scheme == CONTENT_SCHEME && dst.scheme == CONTENT_SCHEME) {
            DocumentsContract.copyDocument(this, src, dst) // cant copy to cache/data directory -.-
        } else {
            val input = this.openInputStream(src)?.buffered()
                ?: throw FileNotFoundException(src.toString())
            val outputStream = this.openOutputStream(dst) ?: throw FileNotFoundException(dst.toString())
            (outputStream as? FileOutputStream)?.channel?.truncate(0)
            val output = outputStream.buffered()

            while (input.available() == 0) {
                Thread.sleep(25)
            }
            while (input.available() > 0) {
                input.copyTo(output)
            }

            input.close()
            output.close()
        }
    }

    // https://gist.github.com/VassilisPallas/b88fb701c55cdace0c420356ee7c1464
    object FileInformation {
        const val TAG = "FileInformation"
        /**
         * Get a file path from a Uri. This will get the the path for Storage Access
         * Framework Documents, as well as the _data field for the MediaStore and
         * other file-based ContentProviders.
         *
         * @param context The context.
         */
        fun getPath(context: Context, uri: Uri): String? {
            // DocumentProvider
            if ("content".equals(uri.scheme, ignoreCase = true)) { // ExternalStorageProvider
                Log.v(TAG, "Uri is document uri.")
                if (isExternalStorageDocument(uri)) {
                    Log.v(TAG, "Uri is external storage")
                    val docId = if (DocumentsContract.isDocumentUri(context, uri)) {
                        DocumentsContract.getDocumentId(uri)
                    } else {
                        DocumentsContract.getTreeDocumentId(uri)
                    }
                    val split = docId.split(":").toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    }
                    // TODO handle non-primary volumes
                } else if (isDownloadsDocument(uri)) {
                    Log.v(TAG, "Uri is download")
                    val id = DocumentsContract.getTreeDocumentId(uri)
                    return when {
                        id == "downloads" -> {
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                        }
                        id.startsWith("raw:/") -> {
                            id.substringAfter("raw:/")
                        }
                        else -> {
                            val contentUri = ContentUris.withAppendedId(
                                Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
                            )
                            getDataColumn(context, contentUri, null, null)
                        }
                    }
                } else if (isMediaDocument(uri)) {
                    Log.v(TAG, "Uri is media")
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":").toTypedArray()
                    val type = split[0]
                    var contentUri: Uri? = null
                    if ("image" == type) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else if ("video" == type) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else if ("audio" == type) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    } else {
                        return null
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(
                        split[1]
                    )
                    return getDataColumn(context, contentUri, selection, selectionArgs)
                }
            //} else if ("content".equals(uri.scheme, ignoreCase = true)) {
            //    return getDataColumn(context, uri, null, null)
            } else if ("file".equals(uri.scheme, ignoreCase = true)) {
                Log.v(TAG, "Uri is filepath")
                return uri.path
            }
            Log.v(TAG, "Uri is nothing we know: ${uri.authority}.")
            return null
        }

        fun getName(context: Context, uri: Uri): String? {
            var fileName: String? = null
            context.contentResolver
                .query(uri, null, null, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) { // get file name
                        fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    }
                }
            return fileName
        }

        fun getSize(context: Context, uri: Uri): String? {
            var fileSize: String? = null
            context.contentResolver
                .query(uri, null, null, null, null, null)
                .use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) { // get file size
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (!cursor.isNull(sizeIndex)) {
                            fileSize = cursor.getString(sizeIndex)
                        }
                    }
                }
            return fileSize
        }

        /**
         * Get the value of the data column for this Uri. This is useful for
         * MediaStore Uris, and other file-based ContentProviders.
         *
         * @param context       The context.
         * @param uri           The Uri to query.
         * @param selection     (Optional) Filter used in the query.
         * @param selectionArgs (Optional) Selection arguments used in the query.
         * @return The value of the _data column, which is typically a file path.
         */
        private fun getDataColumn(context: Context, uri: Uri, selection: String?,selectionArgs: Array<String>?): String? {
            val column = "_data"
            val projection = arrayOf(
                column
            )
            context.contentResolver.query(uri, projection, selection, selectionArgs,null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(column)
                        return cursor.getString(columnIndex)
                    }
                }
            return null
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is ExternalStorageProvider.
         */
        private fun isExternalStorageDocument(uri: Uri): Boolean {
            return "com.android.externalstorage.documents" == uri.authority
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is DownloadsProvider.
         */
        private fun isDownloadsDocument(uri: Uri): Boolean {
            return "com.android.providers.downloads.documents" == uri.authority
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is MediaProvider.
         */
        private fun isMediaDocument(uri: Uri): Boolean {
            return "com.android.providers.media.documents" == uri.authority
        }
    }
}
