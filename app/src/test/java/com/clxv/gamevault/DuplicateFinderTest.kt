package com.clxv.gamevault

import com.clxv.gamevault.core.scanner.DuplicateFinder
import com.clxv.gamevault.data.local.entity.FileRecordEntity
import org.junit.Assert.*
import org.junit.Test

class DuplicateFinderTest {

    private fun file(id: String, size: Long, hash: String) = FileRecordEntity(
        id = id, gameId = "g$id", libraryRootId = "r", uri = "uri$id", documentId = id,
        displayPath = "/x/$id", fileName = id, extension = "iso", size = size,
        modifiedTime = 0, format = "ISO9660", confidence = 1f, status = "DETECTED",
        quickHash = hash,
    )

    @Test fun `groups share size and hash`() {
        val files = listOf(
            file("a", 100, "h1"), file("b", 100, "h1"),
            file("c", 100, "h2"), file("d", 200, "h1"),
        )
        val groups = DuplicateFinder.groupDuplicates(files)
        assertEquals(1, groups.size)
        assertEquals(setOf("a", "b"), groups[0].map { it.id }.toSet())
    }

    @Test fun `large file hashing samples head and tail only`() {
        var calls = 0
        val h = DuplicateFinder.quickHash(100L * 1024 * 1024) { _, _ -> calls++; ByteArray(1024) }
        assertEquals(2, calls)   // head + tail, never the middle
        assertTrue(h.isNotBlank())
    }
}
