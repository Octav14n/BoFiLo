package eu.schnuff.bofilo.download.filewrapper

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import eu.schnuff.bofilo.Constants
import eu.schnuff.bofilo.persistence.AppDatabase
import eu.schnuff.bofilo.persistence.filewrappercache.FileWrapperCacheItem
import kotlin.concurrent.thread

const val UPDATE_CACHE_ON_ERROR_INTERVAL = 25000L

class DocumentFileWrapper(
    private val context: Context,
    private val file: DocumentFile
): FileWrapper {
    override val uri: Uri
        get() = file.uri

    override val name: String
        get() = file.name ?: "NAN"
    override val isDirectory: Boolean
        get() = file.isDirectory
    override val isFile: Boolean
        get() = file.isFile

    override val lastModified: Long
        get() = file.lastModified()

    override fun createFile(mimeType: String, filename: String): FileWrapper {
        var f: DocumentFileWrapper? = null
        while (f == null) {
            f = try {
                file.createFile(mimeType, filename)?.run {
                    DocumentFileWrapper(context, this)
                }
            } catch (e: Throwable) {
                Log.d(this::class.simpleName, "Failed creating file: %s [mime-type: %s]".format(filename, mimeType), e)
                if (last_cache_updated < System.currentTimeMillis() + UPDATE_CACHE_ON_ERROR_INTERVAL) {
                    last_cache_updated = System.currentTimeMillis()
                    getChild(filename, useCaching = false)
                } else null
            }
        }
        return f
    }

    override fun delete() {
        file.delete()
    }

    private val isCached: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.PREF_CACHE_SAF, false)
    private var childCache: Map<String, String>

    init {
        val dao = AppDatabase.getDatabase(context).fileWrapperCacheDao()
        val items = dao.get(parentUri = uri)
        val map = HashMap<String, String>(items.count())
        for (item in items) {
            map[item.filename] = item.childId
        }
        childCache = map
        if (childCache.isNotEmpty())
            thread {
                renewChildCache()
            }
    }

    override fun getChild(filename: String) = getChild(filename, useCaching = null)
    private fun getChild(filename: String, useCaching: Boolean? = null): DocumentFileWrapper? {
        val isCached = useCaching ?: this.isCached
        if (isCached) {
            if (childCache.isEmpty())
                renewChildCache()
            if (childCache.containsKey(filename)) {
                val df = DocumentFile.fromSingleUri(context, DocumentsContract.buildDocumentUriUsingTree(uri, childCache[filename]))
                if (df != null)
                    return DocumentFileWrapper(context, df)
                return null
            }
        } else {
            queryDocumentTree { originalFileName, childId ->
                if (originalFileName == filename) {
                    val df = DocumentFile.fromSingleUri(context, DocumentsContract.buildDocumentUriUsingTree(uri, childId))
                    if (df != null)
                        return DocumentFileWrapper(context, df)
                    return null
                }
            }
        }

        return null
    }

    private inline fun queryDocumentTree(itterateChilds: (filename: String, childId: String) -> Unit) {
        val baseUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getTreeDocumentId(uri)
        )

        context.contentResolver.query(baseUri, arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID
        ), null, emptyArray(), null)?.use {
            val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val childId = it.getString(idIndex)
                itterateChilds(name, childId)
            }
        }
    }

    private fun renewChildCache() {
        val old = childCache.toMutableMap()
        val new = HashMap<String, String>(old.count())
        val dao = AppDatabase.getDatabase(context).fileWrapperCacheDao()

        queryDocumentTree { name, childId ->
            when {
                // Nothing changed for this file-name
                (old.containsKey(name) && old[name] == childId) -> old.remove(name)
                // Key changed for this file-name
                old.containsKey(name) -> {
                    old.remove(name)
                    dao.set(childId, uri, name)
                }
                // File-name is new
                else -> dao.add(childId, uri, name)
            }
            new[name] = childId
        }

        val uriS = uri.toString()
        dao.remove(old.map {
            FileWrapperCacheItem(it.value, uriS, it.key)
        })

        childCache = new
    }

    companion object {
        private var last_cache_updated = 0L
    }
}