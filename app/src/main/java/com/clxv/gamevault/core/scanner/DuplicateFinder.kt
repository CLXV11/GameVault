package com.clxv.gamevault.core.scanner

import com.clxv.gamevault.data.local.entity.FileRecordEntity
import java.security.MessageDigest

/**
 * Memory-safe duplicate detection.
 *
 * Small files (<= [SMALL_FILE_LIMIT]) are hashed fully. Large disc images
 * are identified by (size, head-1MiB, tail-1MiB) sampling — good enough for
 * real-world duplicate copies without ever loading a 4 GiB ISO into RAM.
 */
object DuplicateFinder {

    const val SMALL_FILE_LIMIT = 64L * 1024 * 1024
    private const val SAMPLE = 1024 * 1024

    fun interface Hasher {
        /** Read up to [len] bytes at [offset]; return actual bytes read. */
        fun read(offset: Long, len: Int): ByteArray?
    }

    fun quickHash(size: Long, hasher: Hasher): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(size.toString().toByteArray())
        if (size <= SMALL_FILE_LIMIT) {
            // Single bounded read — one SAF descriptor instead of one per MiB.
            hasher.read(0, size.toInt())?.let { md.update(it) }
        } else {
            hasher.read(0, SAMPLE)?.let { md.update(it) }
            hasher.read(size - SAMPLE, SAMPLE)?.let { md.update(it) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Groups of file records that share (size, hash). */
    fun groupDuplicates(files: List<FileRecordEntity>): List<List<FileRecordEntity>> {
        return files.groupBy { it.size to it.quickHash }
            .filter { it.value.size > 1 }
            .map { it.value.sortedByDescending { f -> f.size } }
    }
}
