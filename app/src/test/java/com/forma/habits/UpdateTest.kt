package com.forma.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class UpdateTest {
    @Test fun `tags compare numerically, not as text`() {
        assertTrue(isNewerVersion("v1.0.4", "1.0.3"))
        assertTrue(isNewerVersion("1.1.0", "1.0.9"))
        // The case plain string comparison gets wrong.
        assertTrue(isNewerVersion("v1.10.0", "v1.9.0"))
        assertFalse(isNewerVersion("v1.0.3", "1.0.3"))
        assertFalse(isNewerVersion("v1.0.2", "1.0.3"))
    }

    @Test fun `a shorter tag is the same release, not an older one`() {
        assertFalse(isNewerVersion("v1.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0"))
        assertTrue(isNewerVersion("1.1", "1.0.9"))
    }

    @Test fun `labelled and unreadable tags never offer a downgrade`() {
        assertTrue(isNewerVersion("v2.0.0-beta", "1.9.9"))
        assertFalse(isNewerVersion("v1.0.0-beta", "1.0.0"))
        assertFalse(isNewerVersion("latest", "1.0.3"))
        assertFalse(isNewerVersion("", "1.0.3"))
    }

    private fun release(vararg assets: String) = """
        {"tag_name":"v1.0.4","name":"Kimi v1.0.4","body":"Fixes things.",
         "html_url":"https://github.com/vabxsen/Kimi/releases/tag/v1.0.4",
         "assets":[${assets.joinToString(",")}]}
    """.trimIndent()

    private fun asset(name: String, size: Long = 4_467_395L) =
        """{"name":"$name","size":$size,"browser_download_url":"https://example.invalid/$name"}"""

    @Test fun `the apk is found by suffix, so renaming the file keeps updates working`() {
        val parsed = parseRelease(release(asset("Kimi-v1.0.4.apk")))
        assertEquals("v1.0.4", parsed.version)
        assertEquals("Kimi-v1.0.4.apk", parsed.fileName)
        assertEquals(4_467_395L, parsed.size)
        assertEquals("Fixes things.", parsed.notes)

        val renamed = parseRelease(release(asset("kimi-release-signed.APK")))
        assertEquals("kimi-release-signed.APK", renamed.fileName)
    }

    @Test fun `other attached files are ignored`() {
        val parsed = parseRelease(release(
            asset("Kimi-source.zip"), asset("checksums.txt"), asset("Kimi-v1.0.4.apk")
        ))
        assertEquals("Kimi-v1.0.4.apk", parsed.fileName)
    }

    @Test fun `a release with nothing installable is refused, not guessed at`() {
        val error = runCatching { parseRelease(release(asset("Kimi-source.zip"))) }.exceptionOrNull()
        assertTrue(error is KimiMessage)
        assertEquals(R.string.err_update_no_file, (error as KimiMessage).resId)
    }

    @Test fun `download size reads as a size, never as zero`() {
        assertEquals("4.3", megabytes(4_467_395L, Locale.US))
        assertEquals("?", megabytes(0L, Locale.US))
        // A tiny asset still has to look like a download rather than "0.0 MB".
        assertEquals("0.0", megabytes(51_200L, Locale.US))
    }
}
