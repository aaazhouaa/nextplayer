package dev.anilbeesetti.nextplayer.core.media.services

import android.content.Context
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HiddenVideoScannerTest {

    private lateinit var root: File
    private lateinit var scanner: HiddenVideoScanner

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        scanner = HiddenVideoScanner(context)
        root = createTempDirectory("hidden-video-scanner").toFile()
    }

    @Test
    fun `finds hidden files and hidden directories but skips normal media`() = runBlocking {
        val hiddenFile = File(root, ".hidden.mp4").apply { writeText("video") }
        val hiddenDir = File(root, ".Movies").apply { mkdirs() }
        val videoInHiddenDir = File(hiddenDir, "episode.mkv").apply { writeText("video") }
        val noMediaDir = File(root, "private").apply { mkdirs() }
        val videoInNoMediaDir = File(noMediaDir, "clip.mov").apply { writeText("video") }
        File(noMediaDir, ".nomedia").apply { writeText("") }
        File(root, "normal.mp4").apply { writeText("video") }
        File(root, ".notes.txt").apply { writeText("not a video") }

        val result = scanner.scan(root.absolutePath)

        val paths = result.map { it.path }.toSet()
        assertEquals(3, result.size)
        assertTrue(paths.contains(hiddenFile.path))
        assertTrue(paths.contains(videoInHiddenDir.path))
        assertTrue(paths.contains(videoInNoMediaDir.path))
        assertTrue(result.all { it.isHidden })
    }

    @Test
    fun `respecting nomedia excludes directories with nomedia marker`() = runBlocking {
        val hiddenFile = File(root, ".hidden.mp4").apply { writeText("video") }
        val noMediaDir = File(root, "private").apply { mkdirs() }
        File(noMediaDir, "clip.mov").apply { writeText("video") }
        File(noMediaDir, ".nomedia").apply { writeText("") }

        val result = scanner.scan(root.absolutePath, respectNoMedia = true)

        val paths = result.map { it.path }.toSet()
        assertEquals(1, result.size)
        assertTrue(paths.contains(hiddenFile.path))
        assertTrue(result.none { it.path.startsWith(noMediaDir.path) })
    }

    @Test
    fun `finds videos in multi-level hidden folders`() = runBlocking {
        val hiddenRoot = File(root, ".Movies").apply { mkdirs() }
        val seasonDir = File(hiddenRoot, "Season1").apply { mkdirs() }
        val videoInSeason = File(seasonDir, "episode.mkv").apply { writeText("video") }
        val directVideo = File(hiddenRoot, "movie.mp4").apply { writeText("video") }

        val result = scanner.scan(hiddenRoot.absolutePath)

        val paths = result.map { it.path }.toSet()
        assertEquals(2, result.size)
        assertTrue(paths.contains(videoInSeason.path))
        assertTrue(paths.contains(directVideo.path))
        assertTrue(result.all { it.isHidden })
    }

    @Test
    fun `finds videos when entering a non-dot child of a hidden ancestor`() = runBlocking {
        val hiddenRoot = File(root, ".Movies").apply { mkdirs() }
        val seasonDir = File(hiddenRoot, "Season1").apply { mkdirs() }
        val deeperDir = File(seasonDir, "Sub").apply { mkdirs() }
        val videoInDeeperDir = File(deeperDir, "episode.mkv").apply { writeText("video") }

        // Simulate the user navigating into `.Movies/Season1` (root itself is not dot-named).
        val result = scanner.scan(seasonDir.absolutePath)

        val paths = result.map { it.path }.toSet()
        assertEquals(1, result.size)
        assertTrue(paths.contains(videoInDeeperDir.path))
        assertTrue(result.all { it.isHidden })
    }
}
