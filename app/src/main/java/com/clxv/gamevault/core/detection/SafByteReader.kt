package com.clxv.gamevault.core.detection

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.IOException

/**
 * [DetectionEngine.ByteReader] backed by the Storage Access Framework.
 *
 * Uses a bounded [FileInputStream] over the SAF-provided descriptor and
 * positional reads — at most a few KiB per detection, never the whole file.
 * The descriptor is closed deterministically after each sniff window.
 */
class SafByteReader(private val context: Context, private val uri: Uri) : DetectionEngine.ByteReader {

    override fun readAt(offset: Long, length: Int): ByteArray? {
        if (length <= 0 || offset < 0) return null
        var pfd: ParcelFileDescriptor? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            FileInputStream(pfd.fileDescriptor).use { fis ->
                val channel = fis.channel
                channel.position(offset)
                val buf = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = fis.read(buf, read, length - read)
                    if (n < 0) break
                    read += n
                }
                if (read == length) buf else buf.copyOf(read)
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } finally {
            try { pfd?.close() } catch (_: IOException) {}
        }
    }
}
