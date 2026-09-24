package com.clxv.gamevault

import com.clxv.gamevault.core.detection.DetectionEngine
import com.clxv.gamevault.core.model.Platform
import com.clxv.gamevault.core.model.Region
import com.clxv.gamevault.core.model.ScanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the layered detection engine.
 * FakeReader emulates bounded SAF reads: it never returns more than the
 * buffer holds, no matter how much the engine asks for.
 */
class DetectionEngineTest {

    class FakeReader(private val data: ByteArray) : DetectionEngine.ByteReader {
        override fun readAt(offset: Long, length: Int): ByteArray? {
            if (offset >= data.size || length <= 0) return ByteArray(0)
            val end = minOf(data.size.toLong(), offset + length).toInt()
            return data.copyOfRange(offset.toInt(), end)
        }
    }

    private fun put(b: ByteArray, off: Int, s: String) {
        s.toByteArray(Charsets.US_ASCII).copyInto(b, off)
    }

    private fun put32be(b: ByteArray, off: Int, v: Int) {
        b[off] = (v shr 24).toByte()
        b[off + 1] = (v shr 16).toByte()
        b[off + 2] = (v shr 8).toByte()
        b[off + 3] = v.toByte()
    }

    private fun detect(
        data: ByteArray,
        name: String,
        size: Long,
        parents: List<String> = emptyList(),
    ): com.clxv.gamevault.core.model.DetectionResult =
        DetectionEngine(FakeReader(data)).detect(name, size, parents)

    // ------------------------------------------------------------------
    @Test
    fun nesMagicDetected() {
        val buf = ByteArray(0x20000)
        put(buf, 0, "NES\u001A")
        val d = detect(buf, "mario.nes", 256L * 1024)
        assertEquals(Platform.NES, d.platform)
        assertEquals(ScanStatus.DETECTED, d.status)
    }

    @Test
    fun gameBoyLogoDetected() {
        val logo = byteArrayOf(
            0xCE.toByte(), 0xED.toByte(), 0x66, 0x66, 0xCC.toByte(), 0x0C, 0x00, 0x0B,
            0x03, 0x73, 0x00, 0x83.toByte(), 0x00, 0x0C, 0x00, 0x0D,
        )
        val gb = ByteArray(0x20000)
        logo.copyInto(gb, 0x104)
        assertEquals(Platform.GAME_BOY, detect(gb, "zelda.gb", 512L * 1024).platform)

        val gbc = ByteArray(0x20000)
        logo.copyInto(gbc, 0x104)
        gbc[0x143] = 0x80.toByte()
        assertEquals(Platform.GAME_BOY_COLOR, detect(gbc, "oracle.gbc", 1024L * 1024).platform)
    }

    @Test
    fun gameCubeMagicWithGcSizeStaysGameCube() {
        val buf = ByteArray(0x20000)
        put32be(buf, 0x1C, 0xC2339F3D.toInt())
        val d = detect(buf, "metroid.gcm", 1_459_978_240L)
        assertEquals(Platform.GAMECUBE, d.platform)
        assertNotEquals(Platform.WII, d.platform)
    }

    @Test
    fun sameMagicWithDvdSizeIsWii() {
        val buf = ByteArray(0x20000)
        put32be(buf, 0x1C, 0xC2339F3D.toInt())
        val d = detect(buf, "galaxy.iso", 4_699_979_776L)
        assertEquals(Platform.WII, d.platform)
    }

    @Test
    fun wbfsHeaderIsWii() {
        val buf = ByteArray(0x20000)
        put(buf, 0, "WBFS")
        val d = detect(buf, "mk.wbfs", 4_000_000_000L)
        assertEquals(Platform.WII, d.platform)
        assertEquals(ScanStatus.DETECTED, d.status)
    }

    @Test
    fun pspUmdDiscDetectedNeverGenericIso() {
        val buf = ByteArray(0x20000)
        buf[0x8000] = 0x01
        put(buf, 0x8001, "CD001")
        put(buf, 0x9000, "PSP_GAME")
        val d = detect(buf, "crisis.iso", 1_700_000_000L)
        assertEquals(Platform.PSP, d.platform)
        assertEquals(ScanStatus.DETECTED, d.status)
    }

    @Test
    fun ps2IdentifiedByBoot2NotPs1() {
        val buf = ByteArray(0x20000)
        buf[0x8000] = 0x01
        put(buf, 0x8001, "CD001")
        put(buf, 0xC000, "BOOT2 = cdrom0:\\SLUS_209.53;1")
        val d = detect(buf, "gow2.iso", 4_200_000_000L)
        assertEquals(Platform.PS2, d.platform)
    }

    @Test
    fun genericCueNeverClassifiedAsPs1() {
        val buf = ByteArray(0x20000)
        put(buf, 0, "FILE \"game.bin\" BINARY")
        val d = detect(buf, "random.cue", 500_000L)
        assertEquals(Platform.UNKNOWN, d.platform)
        assertEquals(ScanStatus.UNKNOWN, d.status)
    }

    @Test
    fun ps3FolderStructureIdentifiesPs3() {
        val d = detect(ByteArray(0x100), "param.sfo", 4096L, parents = listOf("PS3_GAME", "USRDIR"))
        assertEquals(Platform.PS3, d.platform)
    }

    @Test
    fun xboxXdvfsStringDetected() {
        val buf = ByteArray(0x40000)   // signature lives at 0x20000 — must be INSIDE the buffer
        buf[0x8000] = 0x01
        put(buf, 0x8001, "CD001")
        put(buf, 0x20000, "MICROSOFT*XBOX*MEDIA")
        val d = detect(buf, "halo.iso", 2_000_000_000L)
        assertEquals(Platform.XBOX, d.platform)
    }

    @Test
    fun xexMagicIsXbox360() {
        val buf = ByteArray(0x1000)
        put(buf, 0, "XEX2")
        assertEquals(Platform.XBOX_360, detect(buf, "default.xex", 2_000_000L).platform)
    }

    @Test
    fun switchNspPfs0Detected() {
        val buf = ByteArray(0x1000)
        put(buf, 0, "PFS0")
        assertEquals(Platform.SWITCH, detect(buf, "game.nsp", 500_000_000L).platform)
    }

    @Test
    fun truncatedDiscImageFlaggedInvalid() {
        val d = detect(ByteArray(0), "broken.iso", 1000L)
        assertEquals(ScanStatus.INVALID, d.status)
    }

    @Test
    fun ps2SerialInFilenameBoostsPs2() {
        val d = detect(ByteArray(0x100), "Game [SLUS-20953].iso", 4_000_000_000L)
        assertEquals(Platform.PS2, d.platform)
    }

    @Test
    fun regionTagsParsed() {
        val e = DetectionEngine()
        assertEquals(Region.USA, e.extractRegion("Crash (USA).iso"))
        assertEquals(Region.EUROPE, e.extractRegion("Crash (Europe) (En,Fr,De).iso"))
        assertEquals(Region.UNKNOWN, e.extractRegion("Crash.iso"))
    }

    @Test
    fun titleNormalizationStripsTagsAndDiscNumbers() {
        val e = DetectionEngine()
        assertEquals("Crash Bandicoot", e.normalizeTitle("Crash Bandicoot (USA) [SLUS-00067].bin"))
        assertEquals("Final Fantasy IX", e.normalizeTitle("Final Fantasy IX (Disc 1 of 4) (USA).cue"))
    }
}
