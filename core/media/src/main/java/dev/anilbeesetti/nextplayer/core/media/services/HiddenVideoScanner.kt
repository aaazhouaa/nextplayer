package dev.anilbeesetti.nextplayer.core.media.services

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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

    private data class ScanKey(val root: String?, val respectNoMedia: Boolean)

    /** In-memory scan results keyed by root + .nomedia policy, avoiding repeated directory walks. */
    private val cache = ConcurrentHashMap<ScanKey, List<MediaVideo>>()

    /**
     * Returns video files found under [root] that MediaStore skips.
     *
     * Results are cached in memory until [invalidate] is called; repeated calls with the same
     * arguments reuse the previous scan instead of walking the file system again.
     *
     * @param root The absolute directory to scan, or null to scan every storage volume.
     * @param respectNoMedia When true, directories whose own name or any ancestor contains a
     *        `.nomedia` marker are excluded entirely (their media is not scanned).
     */
    suspend fun scan(root: String?, respectNoMedia: Boolean = false): List<MediaVideo> {
        if (!isFileSystemAccessible()) return emptyList()

        val key = ScanKey(root, respectNoMedia)
        cache[key]?.let { return it }

        val roots = root?.let(::listOf) ?: storageVolumeRoots()
        val mediaStorePaths = mediaStoreVideoPaths()

        val seen = mutableSetOf<String>()
        val result = roots.flatMap { rootPath ->
            val rootFile = File(rootPath)
            scanDirectory(
                directory = rootFile,
                mediaStorePaths = mediaStorePaths,
                seen = seen,
                respectNoMedia = respectNoMedia,
                isDirectoryHidden = rootFile.isHiddenPath(),
                ancestorHasNoMedia = rootFile.hasNoMediaAncestor(),
            )
        }
        cache[key] = result
        return result
    }

    /** Drops cached scans so the next [scan] walks the file system again. */
    fun invalidate() {
        cache.clear()
    }

    private fun scanDirectory(
        directory: File,
        mediaStorePaths: Set<String>,
        seen: MutableSet<String>,
        respectNoMedia: Boolean,
        isDirectoryHidden: Boolean = false,
        ancestorHasNoMedia: Boolean = false,
    ): List<MediaVideo> {
        if (!directory.isDirectory) return emptyList()

        val results = mutableListOf<MediaVideo>()
        val children = directory.listFiles() ?: return emptyList()
        val hasNoMediaMarker = children.any { it.name.equals(".nomedia", ignoreCase = true) }
        val effectiveNoMedia = ancestorHasNoMedia || hasNoMediaMarker

        // When respecting .nomedia, this directory is excluded together with its whole subtree.
        if (respectNoMedia && effectiveNoMedia) return emptyList()

        for (child in children) {
            if (child.isDirectory) {
                // A hidden or .nomedia directory hides its whole subtree, so propagate the hidden
                // state downward while descending into it. A non-hidden child under a hidden parent
                // must also inherit the parent's hidden state (multi-level hidden folders).
                val childHidden = isDirectoryHidden || child.name.startsWith(".") || effectiveNoMedia
                results += scanDirectory(child, mediaStorePaths, seen, respectNoMedia, childHidden, effectiveNoMedia)
                continue
            }

            if (!child.isFile) continue
            if (!child.name.startsWith(".") && !effectiveNoMedia && !isDirectoryHidden) continue
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

    /** True when any path segment of this directory starts with a dot. */
    private fun File.isHiddenPath(): Boolean =
        absolutePath.split(File.separator).any { it.startsWith(".") }

    /** True when this directory or any of its ancestors contains a `.nomedia` marker. */
    private fun File.hasNoMediaAncestor(): Boolean {
        var current: File? = this
        while (current != null) {
            if (File(current, ".nomedia").exists()) return true
            val parent = current.parentFile ?: return false
            if (parent == current) return false
            current = parent
        }
        return false
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
