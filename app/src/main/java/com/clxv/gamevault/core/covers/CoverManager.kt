package com.clxv.gamevault.core.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom covers live in the app's private files dir — the original game
 * file is never touched. Covers are downscaled to [COVER_WIDTH] px JPEG so
 * a library of thousands of games stays memory-cheap with Coil caching.
 */
@Singleton
class CoverManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        const val COVER_WIDTH = 600
        const val COVER_HEIGHT = 800
    }

    private val coversDir: File get() = File(context.filesDir, "covers").apply { mkdirs() }
    private val thumbsDir: File get() = File(context.cacheDir, "thumbs").apply { mkdirs() }

    fun customCoverFile(gameId: String): File = File(coversDir, "$gameId.jpg")

    fun hasCustomCover(gameId: String): Boolean = customCoverFile(gameId).exists()

    /** Persist an edited bitmap as this game's custom cover. Returns the file path. */
    suspend fun saveCustomCover(gameId: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            COVER_WIDTH,
            (bitmap.height.toFloat() / bitmap.width * COVER_WIDTH).toInt().coerceIn(1, COVER_HEIGHT * 2),
            true,
        )
        val out = customCoverFile(gameId)
        FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        // Invalidate any cached thumbnail.
        File(thumbsDir, "$gameId.jpg").delete()
        out.absolutePath
    }

    fun resetCover(gameId: String) {
        customCoverFile(gameId).delete()
        File(thumbsDir, "$gameId.jpg").delete()
    }

    /** Decode+downscale in one step to avoid multiple full-size bitmaps in memory. */
    suspend fun decodeSampled(uri: Uri, maxDim: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                var sample = 1
                while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) > maxDim) sample *= 2
                val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { input2 ->
                    BitmapFactory.decodeStream(input2, null, o2)
                }
            }
        } catch (e: Exception) { null }
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    /** Crop a source bitmap to a normalized rect (0..1). */
    fun crop(bitmap: Bitmap, left: Float, top: Float, width: Float, height: Float): Bitmap {
        val x = (left.coerceIn(0f, 1f) * bitmap.width).toInt()
        val y = (top.coerceIn(0f, 1f) * bitmap.height).toInt()
        val w = (width.coerceIn(0.01f, 1f) * bitmap.width).toInt().coerceAtMost(bitmap.width - x)
        val h = (height.coerceIn(0.01f, 1f) * bitmap.height).toInt().coerceAtMost(bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w.coerceAtLeast(1), h.coerceAtLeast(1))
    }

    /**
     * Deterministic generated placeholder when no cover exists.
     * Platform-tinted, title-initial based — no network, no fake artwork.
     */
    fun placeholderColors(title: String, platform: String): Pair<Long, Long> {
        val seed = (title + platform).hashCode().toLong()
        val rnd = java.util.Random(seed)
        val hues = floatArrayOf(210f, 260f, 160f, 30f, 340f, 190f)
        val h1 = hues[rnd.nextInt(hues.size)]
        val h2 = (h1 + 40 + rnd.nextInt(50)) % 360
        fun color(h: Float, s: Float, l: Float): Long {
            val c = android.graphics.Color.HSVToColor(floatArrayOf(h, s, l))
            return c.toLong() and 0xFFFFFFFFL
        }
        return color(h1, 0.45f, 0.55f) to color(h2, 0.5f, 0.18f)
    }
}
