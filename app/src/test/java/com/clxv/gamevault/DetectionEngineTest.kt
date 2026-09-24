package com.clxv.gamevault

import com.clxv.gamevault.core.detection.DetectionEngine
import com.clxv.gamevault.core.model.Platform
import com.clxv.gamevault.core.model.Region
import com.clxv.gamevault.core.model.ScanStatus
import org.junit.Assert.*
import org.junit.Test

class DetectionEngineTest {

    class FakeReader(private val data: ByteArray) : DetectionEngine.ByteReader {
        override fun readAt(offset: Long, length: Int): ByteArray? {
            if (offset >= data.size || length <= 0) return ByteArray(0)
            val end = minOf(data.size.toLong(), offset + length).toInt()
            return data.copyOfRange(offset.toInt(), end)
        }
    }

    private fun buf(size: Int = 0x20000, init: (ByteArray) -> Unit = {}): ByteArray =
        ByteArray(size).also(init)

    private fun put(b: ByteArray, off: Int, s: String) =
        s.toByteArray(Charsets.US_ASCII).copyInto(b, off)

    private fun put32be(b: ByteArray, off: Int, v: Int) {
        b[off] = (v shr 24).toByte(); b[off + 1] = (v shr 16).toByte()
        b[off + 2] = (v shr 8).toByte(); b[off + 3] = v.toByte()
    }

    private fun detect(data: ByteArray, name: String, size: Long, parents: List<String> = emptyList()) =
        DetectionEngine(FakeReader(data)).detect(name, size, parents)

    @Test fun `NES magic detected`() {
        val d = detect(buf { put(it, 0, "NES\u001A") }, "mario.nes", 256 * 1024)
        assertEquals(Platform.NES, d.platform)
        assertEquals(ScanStatus.DETECTED, d.status)
    }

    @Test fun `Game Boy logo detected, CGB flag routes to GBC`() {
        val logo = byteArrayOf(0xCE.toByte(), 0xED.toByte(), 0x66, 0x66, 0xCC.toByte(), 0x0C,
            0x00, 0x0B, 0x03, 0x73, 0x00, 0x83.toByte(), 0x00, 0x0C, 0x00, 0x0D)
        val d = detect(buf { logo.copyInto(it, 0x104) }, "zelda.gb", 512 * 1024)
        assertEquals(Platform.GAME_BOY, d.platform)
        val d2 = detect(buf { logo.copyInto(it, 0x104); it[0x143] = 0x80.toByte() }, "oracle.gbc", 1024 * 1024)
        assertEquals(Platform.GAME_BOY_COLOR, d2.platform)
    }

    @Test fun `GameCube magic with GC size stays GameCube`() {
        val d = detect(buf { put32be(it, 0x1C, 0xC2339F3D) }, "metroid.gcm", 1_459_978_240L)
        assertEquals(Platform.GAMECUBE, d.platform)
        assertNotEquals(Platform.WII, d.platform)
    }

    @Test fun `Same magic with DVD size is Wii`() {
        val d = detect(buf { put32be(it, 0x1C, 0xC2339F3D) }, "galaxy.iso", 4_699_979_776L)
        assertEquals(Platform.WII, d.platform)
    }

    @Test fun `WBFS header is Wii`() {
        val d = detect(buf { put(it, 0, "WBFS") }, "mk.wbfs", 4_000_000_000L)
        assertEquals(Platform.WII, d.platform)
        assertEquals(ScanStatus.DETECTED, d.status)
    }

    @Test fun `PSP UMD disc detected, never as generic ISO`() {
        val d = detect(buf {
            it[0x8000] = 0x01; put(it, 0x8001, "CD001")
            put(it, 0x9000, "PSP_GAME")
        }, "crisis.iso", 1_700_000_000L)
        assertEquals(Platform.PSP, d.platform)
        assertEquals(ScanStatus.DETECTED, d.status)
    }

    @Test fun `PS2 identified by BOOT2, not confused with PS1`() {
        val d = detect(buf {
            it[0x8000] = 0x01; put(it, 0x8001, "CD001")
            put(it, 0xC000, "BOOT2 = cdrom0:\\SLUS_209.53;1")
        }, "gow2.iso", 4_200_000_000L)
        assertEquals(Platform.PS2, d.platform)
    }

    @Test fun `Generic cue is never classified as PS1`() {
        val d = detect(buf { put(it, 0, "FILE \"game.bin\" BINARY") }, "random.cue", 500_000)
        assertEquals(Platform.UNKNOWN, d.platform)
        assertEquals(ScanStatus.UNKNOWN, d.status)
    }

    @Test fun `PS3 folder structure identifies PS3`() {
        val d = detect(buf(0x100), "param.sfo", 4096, parents = listOf("PS3_GAME", "USRDIR"))
        assertEquals(Platform.PS3, d.platform)
    }

    @Test fun `Xbox XDVDFS string detected`() {
        val d = detect(buf {
            it[0x8000] = 0x01; put(it, 0x8001, "CD001")
            put(it, 0x20000, "MICROSOFT*XBOX*MEDIA")
        }, "halo.iso", 2_000_000_000L)
        assertEquals(Platform.XBOX, d.platform)
    }

    @Test fun `XEX magic is Xbox 360`() {
        val d = detect(buf { put(it, 0, "XEX2") }, "default.xex", 2_000_000)
        assertEquals(Platform.XBOX_360, d.platform)
    }

    @Test fun `Switch NSP PFS0 detected`() {
        val d = detect(buf { put(it, 0, "PFS0") }, "game.nsp", 500_000_000)
        assertEquals(Platform.SWITCH, d.platform)
    }

    @Test fun `Truncated disc image flagged invalid`() {
        val d = detect(ByteArray(0), "broken.iso", 1000)
        assertEquals(ScanStatus.INVALID, d.status)
    }

    @Test fun `PS2 serial in filename boosts PS2`() {
        val d = detect(buf(0x100), "Game [SLUS-20953].iso", 4_000_000_000L)
        assertEquals(Platform.PS2, d.platform)
    }

    @Test fun `Region tags parsed`() {
        val e = DetectionEngine()
        assertEquals(Region.USA, e.extractRegion("Crash (USA).iso"))
        assertEquals(Region.EUROPE, e.extractRegion("Crash (Europe) (En,Fr,De).iso"))
        assertEquals(Region.UNKNOWN, e.extractRegion("Crash.iso"))
    }

    @Test fun `Title normalization strips tags and disc numbers`() {
        val e = DetectionEngine()
        assertEquals("Crash Bandicoot", e.normalizeTitle("Crash Bandicoot (USA) [SLUS-00067].bin"))
        assertEquals("Final Fantasy IX", e.normalizeTitle("Final Fantasy IX (Disc 1 of 4) (USA).cue"))
    }
}
