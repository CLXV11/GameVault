package com.clxv.gamevault.core.detection

import com.clxv.gamevault.core.model.Candidate
import com.clxv.gamevault.core.model.DetectionResult
import com.clxv.gamevault.core.model.Platform
import com.clxv.gamevault.core.model.Region
import com.clxv.gamevault.core.model.ScanStatus
import java.util.Locale
import kotlin.math.max

/**
 * Layered game-file identification engine.
 *
 * Layers, in order of trust:
 *  1. Magic bytes / file signature (small range reads only)
 *  2. Disc structure sniffing (PVD, partition, licence strings) via [reader]
 *  3. Container formats (WBFS/CSO/CHD/PBP/PKG/RVZ...)
 *  4. Folder structure signals
 *  5. Filename serial patterns
 *  6. File extension (weakest)
 *  7. Size heuristics (tie-breaker only — never sole evidence)
 *
 * [reader] performs a bounded, seekable read of at most [maxSniffBytes]
 * bytes from the file. Implementations MUST NOT load multi-gigabyte
 * images into memory — the engine only ever asks for small windows.
 */
class DetectionEngine(private val reader: ByteReader? = null) {

    /** Bounded random access to file bytes. Offsets may exceed Int range for big images. */
    fun interface ByteReader {
        fun readAt(offset: Long, length: Int): ByteArray?
    }

    companion object {
        const val MAX_SNIFF_BYTES = 8 * 1024 * 1024L   // never scan more than the first 8 MiB
        const val MIN_DISC_SIZE = 32 * 1024L           // below this a "disc image" is corrupt

        /** Non-game content that must never become a library entry. */
        val JUNK_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "tif", "tiff", "svg",
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "mp3", "flac", "ogg", "wav", "m4a",
            "pdf", "txt", "doc", "docx", "epub", "cbz", "cbr",
            "py", "sh", "js", "ts", "html", "css", "json", "exe", "msi", "apk", "jar",
            "7z", "rar",
        )

        /** Cover-art sidecar markers, e.g. "Game (USA).cover.png" */
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
        val COVER_NAME_REGEX = Regex("(?i)[._ -]?(cover|front)\$")

        /** Platform tokens in folder or file names ("like emulators that know their games"). */
        val PLATFORM_NAME_TOKENS = mapOf(
            "wii u" to Platform.WII_U, "wii" to Platform.WII,
            "gamecube" to Platform.GAMECUBE, "gc" to Platform.GAMECUBE, "ngc" to Platform.GAMECUBE,
            "nintendo ds" to Platform.NINTENDO_DS, "nds" to Platform.NINTENDO_DS,
            "nintendo 3ds" to Platform.NINTENDO_3DS, "3ds" to Platform.NINTENDO_3DS,
            "n64" to Platform.N64, "snes" to Platform.SNES, "nes" to Platform.NES,
            "gba" to Platform.GBA, "gbc" to Platform.GAME_BOY_COLOR, "gameboy" to Platform.GAME_BOY,
            "switch" to Platform.SWITCH,
            "ps1" to Platform.PS1, "psx" to Platform.PS1, "playstation" to Platform.PS1,
            "ps2" to Platform.PS2, "psp" to Platform.PSP, "ps3" to Platform.PS3,
            "ps4" to Platform.PS4, "ps5" to Platform.PS5, "vita" to Platform.VITA,
            "xbox 360" to Platform.XBOX_360, "x360" to Platform.XBOX_360, "xbox" to Platform.XBOX,
            "dreamcast" to Platform.DREAMCAST, "dc" to Platform.DREAMCAST, "saturn" to Platform.SATURN,
            "mega drive" to Platform.MEGA_DRIVE, "genesis" to Platform.MEGA_DRIVE,
            "master system" to Platform.MASTER_SYSTEM, "game gear" to Platform.GAME_GEAR,
            "amiga" to Platform.AMIGA, "atari" to Platform.ATARI_2600, "zx" to Platform.ZX_SPECTRUM,
        )
    }

    /** Context gathered once per file and shared by all rules. */
    private class Ctx(
        val name: String,
        val ext: String,           // lowercase, no dot
        val size: Long,
        val parentNames: List<String>,   // lowercase folder names, root..leaf
        val reader: ByteReader?,
    ) {
        private var sniffed: ByteArray? = null

        /**
         * Bounded sniff window that GROWS on demand: an early small read
         * (magic bytes) must never cap a later deep scan (disc strings).
         */
        fun head(limit: Long = MAX_SNIFF_BYTES): ByteArray? {
            val want = minOf(limit, size).toInt()
            if (want <= 0) return null
            val cur = sniffed
            if (cur != null && cur.size >= want) return cur
            val r = reader ?: return null
            sniffed = try { r.readAt(0, want) } catch (e: Exception) { null }
            return sniffed
        }
    }

    fun detect(fileName: String, size: Long, parentNames: List<String> = emptyList()): DetectionResult {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        val ctx = Ctx(fileName, ext, size, parentNames.map { it.lowercase(Locale.US) }, reader)

        // Completely empty / clearly truncated files are invalid, not "unknown".
        if (size <= 0L) return invalid(ctx, "empty file")
        val discExt = DISC_EXTENSIONS.contains(ext)
        if (discExt && size < MIN_DISC_SIZE) return invalid(ctx, "file too small to be a ${ext.uppercase()} disc image")

        val candidates = LinkedHashMap<Platform, Candidate>()

        /** Weak evidence accumulates (name/folder/extension hints may combine). */
        fun add(platform: Platform, score: Float, reason: String) {
            val c = candidates.getOrPut(platform) { Candidate(platform) }
            c.score = (c.score + score).coerceAtMost(1f)
            if (!c.reasons.contains(reason)) c.reasons.add(reason)
        }

        /** Strong evidence (magic bytes, disc structures) stands on its own. */
        fun addStrong(platform: Platform, score: Float, reason: String) {
            val c = candidates.getOrPut(platform) { Candidate(platform) }
            if (score > c.score) c.score = score
            if (!c.reasons.contains(reason)) c.reasons.add(reason)
        }

        // ---- Layer 1-2: signatures & structures (strongest) ----
        checkMagic(ctx, ::add)
        checkContainers(ctx, ::add)
        checkDiscStructures(ctx, ::add)
        checkFolderStructure(ctx, ::add)

        // ---- Layer 6-7: filename + extension (weakest) ----
        checkNameTokens(ctx, ::add)
        val serialHits = checkSerialPatterns(ctx, ::add)
        checkExtension(ctx, ::add)
        checkSizeTieBreak(ctx, ::add)

        val best = candidates.values.filter { it.score > 0f }.maxByOrNull { it.score }

        val region = extractRegion(ctx.name)
        val normalizedTitle = normalizeTitle(ctx.name)

        if (best == null) {
            return DetectionResult(
                platform = Platform.UNKNOWN, status = ScanStatus.UNKNOWN,
                confidence = 0f, format = ext.uppercase().ifBlank { "file" },
                region = region, reasons = listOf("no identifying signals"),
            )
        }

        val status = when {
            best.score >= STRONG -> ScanStatus.DETECTED
            best.score >= WEAK    -> ScanStatus.PROBABLY
            else                  -> ScanStatus.UNKNOWN
        }
        return DetectionResult(
            platform = if (status == ScanStatus.UNKNOWN) Platform.UNKNOWN else best.platform,
            status = status,
            confidence = best.score.coerceIn(0f, 1f),
            format = describeFormat(ctx, best.platform),
            region = region,
            reasons = best.reasons,
        )
    }

    private fun invalid(ctx: Ctx, why: String) = DetectionResult(
        platform = Platform.UNKNOWN, status = ScanStatus.INVALID,
        confidence = 0f, format = ctx.ext.uppercase().ifBlank { "file" },
        reasons = listOf(why),
    )

    // ------------------------------------------------------------------
    // Signal rules
    // ------------------------------------------------------------------

    private fun checkMagic(ctx: Ctx, addStrong: (Platform, Float, String) -> Unit) {
        val add = addStrong
        val h = ctx.head(0x20000) ?: return
        val s = ctx.size

        // NES / Famicom
        if (h.startsWithAt(0, ascii("NES\u001A"))) add(Platform.NES, 1.0f, "iNES header NES\u001A")
        // FDS (Famicom Disk System)
        if (h.startsWithAt(0, ascii("FDS\u001A"))) add(Platform.NES, 0.9f, "FDS disk header")

        // Game Boy / Game Boy Color: Nintendo logo at 0x104
        val gbLogo = byteArrayOf(0xCE.toByte(), 0xED.toByte(), 0x66, 0x66, 0xCC.toByte(), 0x0C, 0x00, 0x0B, 0x03, 0x73, 0x00, 0x83.toByte(), 0x00, 0x0C, 0x00, 0x0D)
        if (h.startsWithAt(0x104, gbLogo)) {
            val cgb = h.getOrElse(0x143) { 0 }.toInt() and 0xFF
            if (cgb == 0x80 || cgb == 0xC0) add(Platform.GAME_BOY_COLOR, 1.0f, "Nintendo logo + CGB flag")
            else add(Platform.GAME_BOY, 1.0f, "Nintendo logo in GB header")
        }

        // GBA: logo at 0x04, "GAME BOY ADVANCE" at 0xA0
        val gbaLogo = byteArrayOf(0x96.toByte(), 0x00, 0x1D, 0x0A, 0x00, 0x0B, 0x00, 0x09, 0x00, 0x08, 0x11, 0x1F, 0x88.toByte(), 0x89.toByte(), 0x00, 0x0E)
        if (h.startsWithAt(0x04, gbaLogo) && h.readAscii(0xA0, 12).startsWith("GAME BOY")) {
            add(Platform.GBA, 1.0f, "GBA logo + header string")
        }

        // Nintendo DS/DSi: logo at 0xC0
        val ndsLogo = byteArrayOf(0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51, 0x69, 0x9A.toByte(), 0xA2.toByte(), 0x21)
        if (h.startsWithAt(0xC0, ndsLogo)) {
            val unit = h.getOrElse(0x12) { 0 }.toInt() and 0xFF
            if (unit == 0x03) add(Platform.NINTENDO_3DS, 0.5f, "3DS-style unit code in DS header")
            add(Platform.NINTENDO_DS, 1.0f, "Nintendo DS cartridge logo")
        }

        // 3DS: NCSD at 0x100 (CCI/.3ds), NCCH partitions
        if (h.startsWithAt(0x100, ascii("NCSD"))) add(Platform.NINTENDO_3DS, 1.0f, "NCSD media header")
        if (h.startsWithAt(0x100, ascii("NCCH"))) add(Platform.NINTENDO_3DS, 0.95f, "NCCH content header")
        // CIA: header size 0x2020 + type 0, NCCH content inside; magic check is weak, verify size
        if (ctx.ext == "cia" && h.readInt32LE(0) == 0x2020 && s > 0x4000) add(Platform.NINTENDO_3DS, 0.85f, "CIA installable package structure")

        // Switch: XCI (HFS0 "HEAD" at 0x100), NSP (PFS0 at 0), NRO ("NRO0"), NCA
        if (h.startsWithAt(0x100, ascii("HEAD")) && s > 0x1000) add(Platform.SWITCH, 0.95f, "XCI cartridge header (HFS0)")
        if (h.startsWithAt(0, ascii("PFS0"))) add(Platform.SWITCH, 1.0f, "PFS0 package (NSP)")
        if (h.startsWithAt(0, ascii("NRO0"))) add(Platform.SWITCH, 1.0f, "NRO homebrew header")
        if (h.startsWithAt(0x200, ascii("NCA3")) || h.startsWithAt(0x200, ascii("NCA2")) || h.startsWithAt(0x200, ascii("NCA0"))) {
            add(Platform.SWITCH, 1.0f, "NCA content archive")
        }

        // N64: endianness magics at 0x0
        val w = h.readInt32BE(0)
        when (w) {
            0x80371240.toInt() -> add(Platform.N64, 1.0f, "N64 big-endian magic (z64)")
            0x40123780.toInt() -> add(Platform.N64, 1.0f, "N64 byte-swapped magic (n64)")
            0x37804012.toInt() -> add(Platform.N64, 1.0f, "N64 little-endian magic (v64)")
        }

        // GameCube / Wii share the 0xC2339F3D magic at 0x1C; size separates them reliably.
        if (h.readInt32BE(0x1C) == 0xC2339F3D.toInt()) {
            val isWii = s > 0x57058000L || ctx.ext == "wbfs" || ctx.ext == "rvz"
            if (isWii) add(Platform.WII, 0.95f, "GC/Wii disc magic + DVD-scale size")
            else add(Platform.GAMECUBE, 0.95f, "GameCube disc magic (0xC2339F3D)")
        }
        // WUD/WUX Wii U: WUD starts with SEA magic? WUX is compressed WUD.
        if (ctx.ext == "wux" && s > 0x1000) add(Platform.WII_U, 0.85f, "WUX compressed Wii U image")
        if (h.startsWithAt(0, ascii("SEA\u0000")) && s > 0x100000) add(Platform.WII_U, 0.85f, "WUD disc header")

        // Sega Mega Drive: "SEGA" at 0x100
        if (h.readAscii(0x100, 4) == "SEGA") {
            if (ctx.ext == "32x") add(Platform.THIRTY_TWO_X, 0.9f, "SEGA header + .32x extension")
            else add(Platform.MEGA_DRIVE, 1.0f, "SEGA header at 0x100")
        }
        // Master System / Game Gear: "TMR SEGA" at 0x7FF0
        if (h.startsWithAt(0x7FF0, ascii("TMR SEGA"))) {
            if (ctx.ext == "gg") add(Platform.GAME_GEAR, 1.0f, "TMR SEGA header (Game Gear)")
            else add(Platform.MASTER_SYSTEM, 1.0f, "TMR SEGA header (Master System)")
        }

        // Atari Lynx
        if (h.startsWithAt(0, ascii("LYNX"))) add(Platform.ATARI_LYNX, 1.0f, "LYNX header")

        // Amiga ADF: "DOS" at 0
        if (h.startsWithAt(0, ascii("DOS")) && (ctx.ext == "adf" || ctx.ext == "adz")) add(Platform.AMIGA, 1.0f, "Amiga DOS disk header")
        if (h.startsWithAt(0, ascii("KICK")) && ctx.ext == "adf") add(Platform.AMIGA, 1.0f, "Amiga kickstart disk")

        // Xbox executables
        if (h.startsWithAt(0, ascii("XBEH"))) add(Platform.XBOX, 1.0f, "XBE executable header")
        if (h.startsWithAt(0, ascii("XEX2")) || h.startsWithAt(0, ascii("XEX1"))) add(Platform.XBOX_360, 1.0f, "XEX executable header")

        // Sony executables / containers
        if (h.startsWithAt(0, byteArrayOf(0x7F, 0x50, 0x4B, 0x47))) add(Platform.PS_PKG, 0.95f, "PKG header (\u007FPKG)") // PS3/PS4/PS5 share it
        if (h.startsWithAt(0, ascii("\u0000PSF"))) { // PARAM.SFO
            when {
                ctx.parentNames.any { it == "psp_game" || it.contains("psp") } -> add(Platform.PSP, 1.0f, "PARAM.SFO inside PSP_GAME")
                ctx.parentNames.any { it == "ps3_game" || it == "usrdir" } -> add(Platform.PS3, 1.0f, "PARAM.SFO inside PS3_GAME")
                ctx.parentNames.any { it == "sce_sys" } -> add(Platform.PS4, 0.85f, "PARAM.SFO inside sce_sys")
                ctx.parentNames.any { it == "sce_sys" } -> add(Platform.VITA, 0.85f, "PARAM.SFO inside sce_sys")
                ctx.parentNames.any { it.contains("cusa") } -> add(Platform.PS4, 0.85f, "PARAM.SFO near CUSA id")
                else -> add(Platform.PS_PKG, 0.6f, "PARAM.SFO")
            }
        }

        // VPK (Vita): zip with eboot.bin/param.sfo — check local header + name hints
        if (ctx.ext == "vpk" && h.startsWithAt(0, byteArrayOf(0x50, 0x4B, 0x03, 0x04)) && s > 0x400) {
            add(Platform.VITA, 0.8f, "ZIP container (VPK layout)")
        }
    }

    private fun checkContainers(ctx: Ctx, addRaw: (Platform, Float, String) -> Unit) {
        val add: (Platform, Float, String) -> Unit = { p, sc, r -> addRaw(p, sc.coerceAtMost(0.9f), r) }
        val addWeak = addRaw
        val h = ctx.head(0x1000) ?: return
        when {
            // WBFS: Wii-only container
            h.startsWithAt(0, ascii("WBFS")) -> add(Platform.WII, 1.0f, "WBFS container header")
            // CSO: PSP compressor (also used for PS1/PS2 but overwhelmingly PSP); confirm via UMD strings
            h.startsWithAt(0, ascii("CISO")) -> {
                add(Platform.PSP, 0.75f, "CISO compressed image")
            }
            // CHD: compressed CD/DVD; generic — weak, needs disc sniff below to resolve
            h.startsWithAt(0, ascii("MComprHD")) -> addWeak(Platform.UNKNOWN, 0.30f, "CHD compressed image (console unresolved)")
            // PBP: PSP EBOOT or PS1 popstation
            h.startsWithAt(0, ascii("PBP\u0000")) || h.startsWithAt(0, ascii("PBP\u0020")) -> {
                val body = ctx.head(0x40000)
                if (body != null && body.indexOfSub(ascii("PSISOIMG")) >= 0) add(Platform.PS1, 0.9f, "PBP containing PSISOIMG (PS1 conversion)")
                else add(Platform.PSP, 0.85f, "PBP package (PSP EBOOT / PS1 conversion)")
            }
            // RVZ: Dolphin container — GC or Wii unresolved by itself
            h.startsWithAt(0, ascii("RVZ\u0001")) -> addWeak(Platform.UNKNOWN, 0.4f, "RVZ container (console unresolved)")
        }
    }

    /** Disc sniffing: bounded scans for structure strings, never whole-file reads. */
    private fun checkDiscStructures(ctx: Ctx, add: (Platform, Float, String) -> Unit) {   // strong layer
        val h = ctx.head(0x10000) ?: return
        // ISO9660 primary volume descriptor at sector 16: 0x01 'C' 'D' '0' '0' '1'
        val isIso = h.size > 0x8006 && h[0x8000] == 0x01.toByte() &&
                h[0x8001] == 0x43.toByte() && h[0x8002] == 0x44.toByte() && h[0x8003] == 0x30.toByte()
        if (isIso) {
            add(Platform.UNKNOWN, 0.35f, "ISO9660 filesystem (console unresolved)")
            // PS1/PS2 boot area: look for console-specific strings within first 8 MiB
            val deep = ctx.head() ?: h
            when {
                deep.indexOfSub(ascii("PSP_GAME")) >= 0 || deep.indexOfSub(ascii("UMD_DATA")) >= 0 ->
                    add(Platform.PSP, 1.0f, "UMD filesystem (PSP_GAME/UMD_DATA)")
                deep.indexOfSub(ascii("PS3_GAME")) >= 0 || deep.indexOfSub(ascii("PS3_DISC")) >= 0 ->
                    add(Platform.PS3, 1.0f, "PS3_GAME filesystem")
                deep.indexOfSub(ascii("BOOT2")) >= 0 && deep.indexOfSub(ascii("cdrom0:")) >= 0 ->
                    add(Platform.PS2, 0.9f, "SYSTEM.CNF BOOT2 (PS2)")
                deep.indexOfSub(ascii("Sony Computer Entertainment")) >= 0 || deep.indexOfSub(ascii("Licensed by")) >= 0 -> {
                    // Both PS1 and PS2 carry the licence string; size is the discriminator.
                    if (ctx.size > 1_000_000_000L) add(Platform.PS2, 0.85f, "Sony licence string + DVD-scale size")
                    else add(Platform.PS1, 0.85f, "Sony licence string + CD-scale size")
                }
                deep.indexOfSub(ascii("CD-RTOS")) >= 0 -> add(Platform.PHILIPS_CDI, 0.9f, "CD-RTOS (CD-i) system area")
                deep.indexOfSub(ascii("CDI/")) >= 0 && ctx.ext == "iso" -> add(Platform.PHILIPS_CDI, 0.7f, "CD-i application path")
                deep.indexOfSub(ascii("MICROSOFT*XBOX*MEDIA")) >= 0 || deep.indexOfSub(ascii("MICROSOFT*XBOX*360")) >= 0 -> {
                    if (deep.indexOfSub(ascii("360")) >= 0) add(Platform.XBOX_360, 0.9f, "XDVDFS 360 volume string")
                    else add(Platform.XBOX, 0.9f, "XDVDFS volume string")
                }
            }
            return
        }

        // Non-ISO disc images: cue/bin, gdi, cdi
        val deep = ctx.head() ?: return
        when {
            deep.indexOfSub(ascii("SEGA SEGAKATANA")) >= 0 || deep.indexOfSub(ascii("SEGADISCSYSTEM")) >= 0 ->
                add(Platform.DREAMCAST, 0.95f, "Dreamcast IP.BN boot sector")
            deep.indexOfSub(ascii("SEGASATURN")) >= 0 ->
                add(Platform.SATURN, 0.95f, "Saturn boot sector")
            ctx.ext == "gdi" && deep.indexOfSub(ascii("Sega")) >= 0 ->
                add(Platform.DREAMCAST, 0.9f, "GDI track list")
            ctx.ext == "gdi" ->
                add(Platform.DREAMCAST, 0.8f, "GDI track descriptor")
            ctx.ext == "cdi" ->
                add(Platform.DREAMCAST, 0.5f, "CDI image (no signature; extension only)")
            ctx.ext == "cue" ->
                add(Platform.UNKNOWN, 0.4f, "CUE sheet (console unresolved)")
        }
    }

    private fun checkFolderStructure(ctx: Ctx, add: (Platform, Float, String) -> Unit) {
        val parents = ctx.parentNames
        if (parents.isEmpty()) return
        if (parents.any { it == "psp_game" }) add(Platform.PSP, 1.0f, "PSP_GAME folder structure")
        if (parents.any { it == "ps3_game" || it == "ps3_disc.sfb" }) add(Platform.PS3, 1.0f, "PS3_GAME folder structure")
        if (parents.any { it == "usrdir" } && parents.any { it == "ps3_game" }) add(Platform.PS3, 1.0f, "PS3_GAME/USRDIR layout")
        if (parents.any { it == "sce_sys" }) add(Platform.PS4, 0.85f, "sce_sys metadata folder")
        if (parents.any { it.endsWith(".xci") || it.endsWith(".nsp") }) add(Platform.SWITCH, 0.9f, "Switch title folder")
        if (parents.any { Regex("^[a-z]{4}\\d{5}$").matches(it) }) add(Platform.PS4, 0.8f, "PS4 title-id folder (CUSA/BCUS pattern)")
        if (parents.any { Regex("^[a-z]{4}\\d{5}$").matches(it) } && parents.any { it == "sce_sys" }) add(Platform.PS5, 0.7f, "PS5-style title-id folder")
        if (parents.any { it == "nintendo 3ds" || it == "3ds" }) add(Platform.NINTENDO_3DS, 0.6f, "3DS folder name")
        if (parents.any { it.startsWith("ps3") }) add(Platform.PS3, 0.7f, "PS3-named folder")
    }

    /** Platform words in folder names ("Wii", "PS2", ...) — the emulator-style hint layer. */
    private fun checkNameTokens(ctx: Ctx, add: (Platform, Float, String) -> Unit) {
        ctx.parentNames.forEach { p ->
            PLATFORM_NAME_TOKENS[p]?.let { add(it, 0.75f, "folder name: $p") }
        }
        val words = Regex("[a-z0-9]+").findAll(ctx.name.lowercase(Locale.US).substringBeforeLast('.'))
            .map { it.value }.toSet()
        words.forEach { w ->
            PLATFORM_NAME_TOKENS[w]?.let { add(it, 0.7f, "console name in filename: $w") }
        }
        // multi-word folder names
        val joined = ctx.parentNames.joinToString(" ")
        PLATFORM_NAME_TOKENS.keys.filter { it.contains(' ') }.forEach { k ->
            if (k in joined) add(PLATFORM_NAME_TOKENS.getValue(k), 0.75f, "folder name: $k")
        }
    }

    private fun checkSerialPatterns(ctx: Ctx, add: (Platform, Float, String) -> Unit) {
        val n = ctx.name.uppercase(Locale.US)
        fun serial(regex: Regex, platform: Platform, score: Float, label: String) {
            if (regex.containsMatchIn(n)) add(platform, score, label)
        }
        // Sony serials
        serial(Regex("\\b(SLUS|SCUS|SCES|SLES|SLPS|SLPM|SCPS|SLKA)-?\\d{3,5}\\b"), Platform.PS1, 0.4f, "PS1-era serial in filename")
        serial(Regex("\\b(SLUS|SCES|SLPS|SLPM|SLKA|SCJS)-?2\\d{4}\\b"), Platform.PS2, 0.5f, "PS2 serial in filename")
        serial(Regex("\\b(CUSA|BCUS|BCES|PCJS|PCSA)-?\\d{5}\\b"), Platform.PS4, 0.9f, "PS4 title-id in filename")
        serial(Regex("\\b(PCSB|PCSE|PCSF|PCSH|PCSG|PCSC)-?\\d{5}\\b"), Platform.VITA, 0.9f, "Vita title-id in filename")
        serial(Regex("\\b(NTR|TWL)-?[A-Z0-9]{4}\\b"), Platform.NINTENDO_DS, 0.8f, "DS product code in filename")
        serial(Regex("\\b(CTR|TWL)-?[A-Z0-9]{4}\\b"), Platform.NINTENDO_3DS, 0.8f, "3DS product code in filename")
        serial(Regex("\\b(0100[0-9A-F]{12})\\b"), Platform.SWITCH, 0.9f, "Switch title id in filename")
        serial(Regex("\\b(DOL|RVL|SXE|S4S)-?[A-Z0-9]{2}\\b"), Platform.GAMECUBE, 0.4f, "Nintendo disc serial")
        serial(Regex("\\bMK-\\d{5}\\b"), Platform.MEGA_DRIVE, 0.7f, "Mega Drive serial (MK-xxxxx)")
        serial(Regex("\\bT-\\d{2,3}[A-Z]?\\b"), Platform.SATURN, 0.4f, "Sega T-series serial")
        serial(Regex("\\b(LTU|LTC)-\\d{5}\\b"), Platform.XBOX_360, 0.6f, "Xbox 360 media id")
    }

    private fun checkExtension(ctx: Ctx, add: (Platform, Float, String) -> Unit) {
        val e = ctx.ext
        val byExt: Map<String, Pair<Platform, Float>> = mapOf(
            // Nintendo cartridge ROMs
            "nes" to (Platform.NES to 0.6f), "fds" to (Platform.NES to 0.7f),
            "sfc" to (Platform.SNES to 0.6f), "smc" to (Platform.SNES to 0.6f), "fig" to (Platform.SNES to 0.5f), "swc" to (Platform.SNES to 0.5f),
            "z64" to (Platform.N64 to 0.7f), "n64" to (Platform.N64 to 0.7f), "v64" to (Platform.N64 to 0.7f),
            "gb" to (Platform.GAME_BOY to 0.6f), "gbc" to (Platform.GAME_BOY_COLOR to 0.6f), "gba" to (Platform.GBA to 0.6f),
            "nds" to (Platform.NINTENDO_DS to 0.6f), "ids" to (Platform.NINTENDO_DS to 0.6f),
            "3ds" to (Platform.NINTENDO_3DS to 0.6f), "cci" to (Platform.NINTENDO_3DS to 0.6f), "cia" to (Platform.NINTENDO_3DS to 0.6f), "cxi" to (Platform.NINTENDO_3DS to 0.6f),
            "gcm" to (Platform.GAMECUBE to 0.7f),
            "wbfs" to (Platform.WII to 0.9f), "rvz" to (Platform.GAMECUBE to 0.4f),
            "wud" to (Platform.WII_U to 0.7f), "wux" to (Platform.WII_U to 0.7f), "rpx" to (Platform.WII_U to 0.6f),
            "xci" to (Platform.SWITCH to 0.7f), "nsp" to (Platform.SWITCH to 0.7f), "nro" to (Platform.SWITCH to 0.7f),
            "nca" to (Platform.SWITCH to 0.7f),
            // Sony
            "pbp" to (Platform.PSP to 0.5f), "cso" to (Platform.PSP to 0.6f),
            "pkg" to (Platform.PS_PKG to 0.5f),
            "vpk" to (Platform.VITA to 0.6f),
            // Microsoft
            "xbe" to (Platform.XBOX to 0.7f), "xex" to (Platform.XBOX_360 to 0.7f),
            "zar" to (Platform.XBOX_360 to 0.5f),
            // Sega
            "md" to (Platform.MEGA_DRIVE to 0.6f), "smd" to (Platform.MEGA_DRIVE to 0.6f), "gen" to (Platform.MEGA_DRIVE to 0.6f), "bin_md" to (Platform.MEGA_DRIVE to 0.5f),
            "sms" to (Platform.MASTER_SYSTEM to 0.6f), "gg" to (Platform.GAME_GEAR to 0.6f),
            "32x" to (Platform.THIRTY_TWO_X to 0.7f),
            "gdi" to (Platform.DREAMCAST to 0.7f), "cdi" to (Platform.DREAMCAST to 0.5f),
            // Others
            "pce" to (Platform.PC_ENGINE to 0.6f), "sgx" to (Platform.PC_ENGINE to 0.5f),
            "ngc" to (Platform.NEO_GEO_POCKET to 0.5f), "ngp" to (Platform.NEO_GEO_POCKET to 0.5f),
            "ws" to (Platform.WONDERSWAN to 0.5f), "wsc" to (Platform.WONDERSWAN to 0.5f),
            "a26" to (Platform.ATARI_2600 to 0.7f),
            "lnx" to (Platform.ATARI_LYNX to 0.6f), "lyx" to (Platform.ATARI_LYNX to 0.6f),
            "j64" to (Platform.ATARI_JAGUAR to 0.6f), "jag" to (Platform.ATARI_JAGUAR to 0.6f),
            "adf" to (Platform.AMIGA to 0.7f), "adz" to (Platform.AMIGA to 0.6f), "ipf" to (Platform.AMIGA to 0.6f), "dms" to (Platform.AMIGA to 0.5f),
            "st" to (Platform.ATARI_ST to 0.6f), "msa" to (Platform.ATARI_ST to 0.5f),
            "tzx" to (Platform.ZX_SPECTRUM to 0.7f), "tap" to (Platform.ZX_SPECTRUM to 0.5f),
            "z80" to (Platform.ZX_SPECTRUM to 0.5f), "sna" to (Platform.ZX_SPECTRUM to 0.5f),
            "col" to (Platform.COLECOVISION to 0.6f), "cv" to (Platform.COLECOVISION to 0.5f),
            "int" to (Platform.INTELLIVISION to 0.6f), "rom" to (Platform.UNKNOWN to 0.1f),
            "vec" to (Platform.VECTREX to 0.6f),
            "tgc" to (Platform.GAME_DOT_COM to 0.6f),
        )
        val hit = byExt[e] ?: return
        // Generic .bin: extension alone is meaningless (handled via disc sniff).
        if (e == "bin" || e == "iso") return
        add(hit.first, hit.second, ".$e extension")
    }

    /** Size only breaks ties between already-plausible candidates — never sole evidence. */
    private fun checkSizeTieBreak(ctx: Ctx, add: (Platform, Float, String) -> Unit) {
        val s = ctx.size
        // GameCube ceiling is ~1.5 GiB; a >1.46 GiB GC/Wii-family image is Wii.
        if (s in (0x57058000L + 1)..0x1D47F5C00L && ctx.ext in setOf("iso", "gcm", "rvz")) {
            add(Platform.WII, 0.35f, "size above GameCube maximum")
        }
    }

    private fun describeFormat(ctx: Ctx, platform: Platform): String {
        val h = ctx.head(0x100) ?: return ctx.ext.uppercase().ifBlank { "file" }
        return when {
            ctx.ext == "wbfs" -> "WBFS"
            ctx.ext == "cso" -> "CSO (compressed UMD)"
            ctx.ext == "rvz" -> "RVZ"
            h.startsWithAt(0, ascii("MComprHD")) -> "CHD"
            h.size > 0x8001 && h[0x8001] == 0x43.toByte() && h[0x8002] == 0x44.toByte() -> "ISO9660 disc"
            ctx.ext == "cue" -> "CUE/BIN"
            ctx.ext == "gdi" -> "GDI"
            ctx.ext.isNotBlank() -> ctx.ext.uppercase()
            else -> "file"
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    val DISC_EXTENSIONS = setOf("iso", "bin", "cue", "img", "gcm", "chd", "cdi", "gdi", "mdf", "nrg", "wbfs", "rvz", "cso")

    fun extractRegion(name: String): Region {
        val m = Regex("\\(([^)]*)\\)").findAll(name).map { it.groupValues[1] }.toList() +
                Regex("\\[([^]]*)\\]").findAll(name).map { it.groupValues[1] }.toList()
        for (tag in m) {
            val t = tag.uppercase(Locale.US)
            when {
                "USA" in t || "NTSC-U" in t || "(U)" == tag -> return Region.USA
                "EUROPE" in t || "PAL-E" in t -> return Region.EUROPE
                "JAPAN" in t || "NTSC-J" in t || "(J)" == tag -> return Region.JAPAN
                "KOREA" in t -> return Region.KOREA
                "AUSTRALIA" in t || "AU" == t -> return Region.AUSTRALIA
                "BRAZIL" in t -> return Region.BRAZIL
                "CHINA" in t -> return Region.CHINA
                "WORLD" in t -> return Region.WORLD
                "EN" == t || "EN," in t || ",EN" in t || t.startsWith("EN ") -> return Region.UNKNOWN // language tag, not region
            }
        }
        return Region.UNKNOWN
    }

    /** Human-friendly title: strip tags, extension, disc markers; keep case. */
    fun normalizeTitle(fileName: String): String {
        var t = fileName.substringBeforeLast('.', fileName)
        t = t.replace(Regex("\\s*\\[[^\\]]*\\]\\s*"), " ")
        t = t.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
        t = t.replace(Regex("(?i)\\b(disc|disk)\\s*\\d+\\b.*$"), "")
        t = t.replace(Regex("(?i)\\b(rev \\d+|v\\d+\\.\\d+)\\b"), "")
        t = t.trim().replace(Regex("\\s+"), " ")
        if (t.isBlank()) t = fileName.substringBeforeLast('.', fileName)
        return t.trim()
    }

    private val STRONG = 0.85f
    private val WEAK = 0.45f
}
