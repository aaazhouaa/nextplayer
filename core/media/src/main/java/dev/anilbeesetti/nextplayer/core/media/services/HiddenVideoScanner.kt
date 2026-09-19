package dev.anilbeesetti.nextplayer.core.media.services

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import java.io.File
import org.koin.core.annotation.Single

/**
 * Scans the file system directly for videos that MediaStore never indexes: files and directories
 * whose names start with a dot (`.hidden.mp4`, `.Movies/`) and directories containing a `.nomedia`
 * marker.
 *
 * On Android 10+ (API 29) the MediaStore `DATA`-based path view is still readable, but direct
 * `File` access to shared storage requires "All files access" (`MANAGE_EXTERNAL_STORAGE`).
 * Callers are responsible for only enabling this when the user has granted that permission.
 *
 * Files that MediaStore already indexes are skipped: they are already visible in the library and
 * must not be duplicated. The `file:` uri returned for genuinely hidden files is consumed directly
 * by the player.
 */
@Single
class HiddenVideoScanner(
    private val context: Context,
) {

    /**
     * Returns video files found under [root] that MediaStore skips.
     *
     * @param root The absolute directory to scan, or null to scan every storage volume.
     * @param respectNoMedia When true, directories whose own name or any ancestor contains a
     *        `.nomedia` marker are excluded entirely (their media is not scanned).
     */
    suspend fun scan(root: String?, respectNoMedia: Boolean = false): List<MediaVideo> {
        if (!isFileSystemAccessible()) return emptyList()

        val roots = root?.let(::listOf) ?: storageVolumeRoots()
        val mediaStorePaths = mediaStoreVideoPaths()

        val seen = mutableSetOf<String>()
        return roots.flatMap { scanDirectory(File(it), mediaStorePaths, seen, respectNoMedia) }
    }

    private fun scanDirectory(
        directory: File,
        mediaStorePaths: Set<String>,
        seen: MutableSet<String>,
        respectNoMedia: Boolean,
        isDirectoryHidden: Boolean = false,
    ): List<MediaVideo> {
        if (!directory.isDirectory) return emptyList()

        val results = mutableListOf<MediaVideo>()
        val children = directory.listFiles() ?: return emptyList()
        val hasNoMediaMarker = children.any { it.name.equals(".nomedia", ignoreCase = true) }

        // When respecting .nomedia, this directory is excluded together with its whole subtree.
        if (respectNoMedia && hasNoMediaMarker) return emptyList()

        for (child in children) {
            if (child.isDirectory) {
                // A hidden or .nomedia directory hides its whole subtree, so propagate the hidden
                // state downward while descending into it.
                val childHidden = isDirectoryHidden || child.name.startsWith(".") || hasNoMediaMarker
                results += scanDirectory(child, mediaStorePaths, seen, respectNoMedia, childHidden)
                continue
            }

            if (!child.isFile) continue
            if (!child.name.startsWith(".") && !hasNoMediaMarker && !isDirectoryHidden) continue
            if (!child.isSupportedVideo()) continue

            val canonical = child.canonicalPath ?: child.absolutePath
            if (!seen.add(canonical)) continue

            // MediaStore doesn't index files hidden by name or by .nomedia, but if it somehow
            // already has this path, the row it returns is the source of truth — skip the duplicate.
            if (mediaStorePaths.contains(child.absolutePath) || mediaStorePaths.contains(canonical)) continue

            results += MediaVideo(
                id = canonical.hashCode().toLong(),
                uri = child.toUri(),
                path = canonical,
                title = child.name,
                parentPath = child.parent ?: "/",
                displayName = child.name,
                duration = 0L,
                size = child.length(),
                width = 0,
                height = 0,
                dateModified = child.lastModified(),
                isHidden = true,
            )
        }

        return results
    }

    private fun storageVolumeRoots(): List<String> = buildList {
        add(Environment.getExternalStorageDirectory().absolutePath)
        context.getExternalFilesDirs(null)
            .mapNotNull { it?.absolutePath?.substringBefore("/Android") }
            .filter { it.isNotBlank() }
            .forEach { add(it) }
    }.distinct()

    private fun mediaStoreVideoPaths(): Set<String> {
        val paths = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                while (cursor.moveToNext()) {
                    cursor.getString(index)?.let { paths.add(it) }
                }
            }
        }
        return paths
    }

    private fun isFileSystemAccessible(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        // Android 10 honours requestLegacyExternalStorage, so direct File access still works.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return runCatching { isExternalStorageManager() }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun isExternalStorageManager(): Boolean = Environment.isExternalStorageManager()

    private fun File.isSupportedVideo(): Boolean {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in SUPPORTED_EXTENSIONS
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf(
            "3gp", "3g2", "avi", "flv", "m2ts", "m4v", "mkv", "mov", "mp2", "mp4",
            "mpeg", "mpg", "mts", "ts", "webm", "wmv",
        )
    }
}
