package com.whatsapp.clone.platform

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─── Category model ────────────────────────────────────────────────────────────

enum class FileCategory {
    Image, Video, Audio, DocumentPDF, DocumentWord, Archive, Other
}

data class CategorizedItem(
    val id: Long,
    val displayName: String,
    val size: Long,
    val mimeType: String,
    val category: FileCategory
)

// ─── Scanner ──────────────────────────────────────────────────────────────────

class StorageScanner(private val context: Context) {

    /**
     * Queries MediaStore.Files on the external volume (Scoped Storage compliant —
     * NO raw /storage/emulated/0 traversal), filters empty records, classifies by
     * MIME type, and returns a [FileCategory] → [CategorizedItem] map.
     */
    suspend fun scan(): Map<FileCategory, List<CategorizedItem>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<FileCategory, MutableList<CategorizedItem>>()
        FileCategory.entries.forEach { result[it] = mutableListOf() }

        val collectionUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        // Filter: only non-empty files (SIZE > 0 eliminates directories & 0-byte stubs)
        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"

        context.contentResolver.query(
            collectionUri,
            projection,
            selection,
            null,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idIdx       = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIdx     = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIdx     = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeIdx     = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id          = cursor.getLong(idIdx)
                val name        = cursor.getString(nameIdx) ?: continue
                val size        = cursor.getLong(sizeIdx)
                val rawMime     = cursor.getString(mimeIdx)

                // Resolve MIME: prefer MediaStore value; fallback to extension lookup
                val mimeType = if (!rawMime.isNullOrBlank()) {
                    rawMime.lowercase().trim()
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                }

                val category = classify(mimeType, name)
                result[category]!!.add(CategorizedItem(id, name, size, mimeType, category))
            }
        }

        result.mapValues { (_, list) -> list.toList() }
    }

    // ─── MIME classifier ──────────────────────────────────────────────────────

    private fun classify(mime: String, name: String): FileCategory = when {
        mime.startsWith("image/") -> FileCategory.Image
        mime.startsWith("video/") -> FileCategory.Video
        mime.startsWith("audio/") -> FileCategory.Audio

        mime == "application/pdf" -> FileCategory.DocumentPDF

        mime in WORD_MIME_TYPES ||
        name.endsWith(".doc", ignoreCase = true) ||
        name.endsWith(".docx", ignoreCase = true) -> FileCategory.DocumentWord

        mime in ARCHIVE_MIME_TYPES ||
        ARCHIVE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } -> FileCategory.Archive

        else -> FileCategory.Other
    }

    companion object {
        private val WORD_MIME_TYPES = setOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/csv"
        )
        private val ARCHIVE_MIME_TYPES = setOf(
            "application/zip",
            "application/x-rar-compressed",
            "application/x-tar",
            "application/gzip",
            "application/x-7z-compressed"
        )
        private val ARCHIVE_EXTENSIONS = listOf(".zip", ".rar", ".tar", ".gz", ".7z", ".bz2")
    }
}

// ─── Aggregated stats ─────────────────────────────────────────────────────────

data class CategoryStats(
    val category: FileCategory,
    val itemCount: Int,
    val totalBytes: Long,
    val sampleNames: List<String>   // up to 5 filenames for dashboard preview
)

fun Map<FileCategory, List<CategorizedItem>>.toStats(): List<CategoryStats> =
    map { (cat, items) ->
        CategoryStats(
            category   = cat,
            itemCount  = items.size,
            totalBytes = items.sumOf { it.size },
            sampleNames = items.take(5).map { it.displayName }
        )
    }.filter { it.itemCount > 0 }
