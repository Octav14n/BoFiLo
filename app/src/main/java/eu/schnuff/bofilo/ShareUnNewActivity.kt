package eu.schnuff.bofilo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import eu.schnuff.bofilo.download.StoryUnNewService

class ShareUnNewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_update)

        intent?.let {
            onNewIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        // Handle potential new download request
        super.onNewIntent(intent)

        if (intent.action == INTENT) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                unNewStory(this, it.toUri())
            }
        }
        finish()
    }

    companion object {
        const val INTENT = "eu.schnuff.bofilo.action.unnew"

        fun unNewStory(context: Context, uri: Uri) {
            // Copy file to cache directory
            StoryUnNewService.start(context, uri)
            Handler(context.mainLooper).post {
                Toast.makeText(context, "Started un-new-ing.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}