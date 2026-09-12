package com.forma.habits

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Kimi ships from GitHub Releases rather than a store, so the app has to tell a person when a
 * newer build exists. The check is manual, never automatic: nothing here runs unless the button
 * in Settings is pressed, so simply using Kimi still contacts nobody but Firebase.
 */
const val UpdateRepository = "vabxsen/Kimi"
private const val LatestReleaseUrl = "https://api.github.com/repos/$UpdateRepository/releases/latest"
const val ReleasesPageUrl = "https://github.com/$UpdateRepository/releases/latest"

/** GitHub rejects unidentified callers, so every request names the app. */
private const val UserAgent = "Kimi-Android"
private const val MaxMetadataBytes = 512 * 1024
private const val NetworkTimeoutMillis = 20_000

data class UpdateRelease(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val fileName: String,
    val downloadUrl: String,
    val size: Long
)

/**
 * Numeric comparison of dotted versions. A leading `v` and any trailing label (`-beta`, `+3`) are
 * ignored, so `v1.2.0` and `1.2` compare as the same release and `1.10` correctly beats `1.9`.
 */
internal fun versionParts(value: String): List<Int> = value.trim().removePrefix("v").removePrefix("V")
    .split('.', '-', '+', '_')
    .map { part -> part.takeWhile(Char::isDigit) }
    .takeWhile { it.isNotEmpty() }
    .mapNotNull { it.toIntOrNull() }

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val offered = versionParts(candidate)
    val installed = versionParts(current)
    // An unparseable tag is never treated as an upgrade: better to show nothing than to push a
    // person at a download Kimi cannot reason about.
    if (offered.isEmpty()) return false
    for (index in 0 until maxOf(offered.size, installed.size)) {
        val a = offered.getOrElse(index) { 0 }
        val b = installed.getOrElse(index) { 0 }
        if (a != b) return a > b
    }
    return false
}

/**
 * Reads the one release asset Kimi can actually install.
 *
 * Assets are matched on the `.apk` suffix rather than an exact file name, so renaming the file in a
 * future release does not silently break updates for everyone already running an older build.
 */
internal fun parseRelease(body: String): UpdateRelease {
    val json = JSONObject(body)
    val version = json.optString("tag_name").ifBlank { json.optString("name") }
    demand(version.isNotBlank(), R.string.err_update_unreadable)
    val assets = json.optJSONArray("assets")
    val apk = (0 until (assets?.length() ?: 0))
        .map { assets!!.getJSONObject(it) }
        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
    demand(apk != null, R.string.err_update_no_file)
    return UpdateRelease(
        version = version,
        notes = json.optString("body").trim(),
        pageUrl = json.optString("html_url").ifBlank { ReleasesPageUrl },
        fileName = apk!!.optString("name"),
        downloadUrl = apk.optString("browser_download_url"),
        size = apk.optLong("size")
    )
}

private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout = NetworkTimeoutMillis
    readTimeout = NetworkTimeoutMillis
    setRequestProperty("User-Agent", UserAgent)
}

object Updates {
    /** Blocking; callers move it off the main thread. */
    fun fetchLatest(): UpdateRelease {
        val connection = open(LatestReleaseUrl).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = connection.responseCode
            // Anonymous callers get 60 requests an hour per address, which a person pressing a
            // button will never reach - but a shared network address can, so it is named plainly.
            demand(code != 403 && code != 429, R.string.err_update_rate_limited)
            demand(code == 200, R.string.err_update_unavailable)
            // Read by hand rather than with readNBytes: that arrived in API 33 and Kimi supports 26.
            val body = connection.inputStream.use { stream ->
                val buffer = ByteArray(16 * 1024)
                val collected = java.io.ByteArrayOutputStream()
                while (collected.size() < MaxMetadataBytes) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    collected.write(buffer, 0, read)
                }
                collected.toByteArray().toString(Charsets.UTF_8)
            }
            return parseRelease(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Where a downloaded build waits. Kept in the cache so Android can reclaim it under pressure. */
    fun downloadDirectory(context: Context) = File(context.cacheDir, "updates")

    /**
     * Streams the asset to internal cache, reporting progress.
     *
     * The file is written under a temporary name and only moved into place once the whole asset has
     * arrived, so an interrupted download can never be mistaken for an installable build.
     */
    fun download(context: Context, release: UpdateRelease, onProgress: (Long, Long) -> Unit): File {
        val directory = downloadDirectory(context).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, release.fileName)
        val partial = File(directory, release.fileName + ".part")
        val connection = open(release.downloadUrl)
        try {
            demand(connection.responseCode == 200, R.string.err_update_download)
            val total = if (release.size > 0) release.size else connection.contentLengthLong
            var written = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                }
            }
            // A truncated file would otherwise reach the installer and fail as "app not installed".
            demand(release.size <= 0 || written == release.size, R.string.err_update_download)
            partial.renameTo(target)
            return target
        } catch (e: Throwable) {
            partial.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Android refuses an update signed with a different key, and reports it only as a blunt
     * "app not installed". Checking first turns that into a sentence that explains itself - which
     * matters because Kimi's own v1.0.0 asset was signed with a debug key, not the release one.
     */
    fun signedLikeInstalled(context: Context, file: File): Boolean = runCatching {
        val packages = context.packageManager
        fun certificates(archive: Boolean): Set<String> {
            @Suppress("DEPRECATION")
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val info = if (archive) packages.getPackageArchiveInfo(file.absolutePath, flag)
            else packages.getPackageInfo(context.packageName, flag)
            @Suppress("DEPRECATION")
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                info?.signingInfo?.apkContentsSigners else info?.signatures
            val digest = MessageDigest.getInstance("SHA-256")
            return signatures.orEmpty().map { digest.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
        }
        val offered = certificates(archive = true)
        offered.isNotEmpty() && offered == certificates(archive = false)
    }.getOrDefault(true) // Never block an install on a check that itself failed to run.

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Sends the person to the one Android screen that can grant install permission to Kimi. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
