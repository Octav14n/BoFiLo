package eu.schnuff.bofilo.download

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_DEFERRED
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.work.*
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.MainActivity
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import java.io.File

private const val EXTRA_PARAM_SRC_URI = "srcUri"
private const val EXTRA_PARAM_DST_URI = "dstUri"
private const val EXTRA_PARAM_DST_DIR_URI = "dstDirUri"
private const val EXTRA_PARAM_DST_FILE_NAME = "dstFileName"
private const val EXTRA_PARAM_DST_MIME_TYPE = "dstMimeType"
private const val EXTRA_PARAM_UPDATE_ITEM_URL = "updateItemUrl"
private const val CHANNEL_ID = "StoryWriteService"
private const val NOTIFICATION_ID = 3002

object StoryWriteService {
    fun start(context: Context, srcUri: Uri, dstUri: Uri) {
        Log.d(this::class.simpleName, "Now writing to %s".format(dstUri))

        while (true) {
            try {
                context.contentResolver.copyFile(srcUri, dstUri)
                if (dstUri.scheme == "file")
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(dstUri.toFile().absolutePath),
                        arrayOf(null),
                        null
                    )
                break
            } catch (e: Exception) {
                Log.e(this::class.simpleName, "Could not write to destination.", e)
            }
        }
        if (srcUri.scheme == ContentResolver.SCHEME_FILE) {
            Log.d(this::class.simpleName, "Now deleting $srcUri .")
            srcUri.path?.let {
                File(it).delete()
            }
        }
    }

    fun start(context: Context, @Suppress("UNUSED_PARAMETER") item: StoryListItem, srcUri: Uri, dstDirUri: Uri, dstFileName: String, dstMimeType: String) {
        val dstUri = getDstUri(context, dstDirUri, dstFileName, dstMimeType)

        if (dstUri == null) {
            Looper.prepare()
            Toast.makeText(context, "Writing failed. No destination uri could be given.", Toast.LENGTH_SHORT).show()
            return
        }

        start(context, srcUri, dstUri)
    }

    private fun getDstUri(context: Context, dstDirUri: Uri, fileName: String, mimeType: String) : Uri? {
        val df = FileWrapper.fromUri(context, dstDirUri)

        Log.d(this::class.simpleName, "Now creating file: uri: %s, filename: %s, mime-type: %s".format(df.uri, fileName, mimeType))

        return try {
            df.createFile(mimeType, fileName).uri
        } catch (e: java.lang.Exception) {
            Looper.prepare()
            Log.e(this::class.simpleName, "Could not create file '$fileName' in '$dstDirUri' with mimeType '$mimeType'", e)
            Toast.makeText(context, "Could not create file '$fileName'.", Toast.LENGTH_LONG)
            null
        }
    }
}