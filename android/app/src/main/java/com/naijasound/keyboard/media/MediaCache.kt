package com.naijasound.keyboard.media

import android.content.Context
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.data.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

enum class MediaKind(val extension: String) { Audio("mp3"), Video("mp4") }

/**
 * Downloads clip files once and keeps them in the app's cache folder, so repeat
 * previews and sends cost no data. Old files are cleared past [maxBytes].
 */
class MediaCache(
    context: Context,
    private val http: OkHttpClient,
    private val maxBytes: Long = 80L * 1024 * 1024,
) {
    val directory: File = File(context.cacheDir, "media").apply { mkdirs() }

    fun fileFor(clip: Clip, kind: MediaKind): File {
        val url = urlFor(clip, kind)
        val name = clip.slug.ifBlank { clip.id }.replace(Regex("[^a-z0-9-]"), "").take(60).ifBlank { "clip" }
        return File(directory, "$name-${sha1(url).take(10)}.${kind.extension}")
    }

    fun isCached(clip: Clip, kind: MediaKind): Boolean = fileFor(clip, kind).length() > 0

    suspend fun get(clip: Clip, kind: MediaKind): File = withContext(Dispatchers.IO) {
        val target = fileFor(clip, kind)
        if (target.length() > 0) {
            target.setLastModified(System.currentTimeMillis())
            return@withContext target
        }
        directory.mkdirs()
        val partial = File(directory, "${target.name}.${System.nanoTime()}.part")
        try {
            http.newCall(Request.Builder().url(urlFor(clip, kind)).build()).await().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
                val body = response.body ?: throw IOException("Empty download")
                body.byteStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
            }
        } finally {
            partial.delete()
        }
        trim(keep = target)
        target
    }

    private fun trim(keep: File) {
        val files = directory.listFiles()?.filter { it.isFile && it != keep } ?: return
        var total = files.sumOf { it.length() } + keep.length()
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    private fun urlFor(clip: Clip, kind: MediaKind) = when (kind) {
        MediaKind.Audio -> clip.mp3Url
        MediaKind.Video -> clip.mp4Url
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
