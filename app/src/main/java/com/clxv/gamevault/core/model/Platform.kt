package com.clxv.gamevault.core.model

/** Known game platforms. Extensions alone never decide the platform. */
enum class Platform(val label: String, val short: String) {
    NES("Nintendo Entertainment System", "NES"),
    SNES("Super Nintendo", "SNES"),
    N64("Nintendo 64", "N64"),
    GAME_BOY("Game Boy", "GB"),
    GAME_BOY_COLOR("Game Boy Color", "GBC"),
    GBA("Game Boy Advance", "GBA"),
    NINTENDO_DS("Nintendo DS", "NDS"),
    NINTENDO_3DS("Nintendo 3DS", "3DS"),
    GAMECUBE("Nintendo GameCube", "GC"),
    WII("Nintendo Wii", "Wii"),
    WII_U("Nintendo Wii U", "Wii U"),
    SWITCH("Nintendo Switch", "Switch"),
    PS1("Sony PlayStation", "PS1"),
    PS2("Sony PlayStation 2", "PS2"),
    PSP("Sony PSP", "PSP"),
    PS3("Sony PlayStation 3", "PS3"),
    PS4("Sony PlayStation 4", "PS4"),
    PS5("Sony PlayStation 5", "PS5"),
    PS_PKG("Sony PlayStation (PKG)", "PKG"),
    VITA("Sony PlayStation Vita", "Vita"),
    XBOX("Microsoft Xbox", "Xbox"),
    XBOX_360("Microsoft Xbox 360", "X360"),
    DREAMCAST("Sega Dreamcast", "DC"),
    SATURN("Sega Saturn", "Saturn"),
    MEGA_DRIVE("Sega Mega Drive / Genesis", "MD"),
    MASTER_SYSTEM("Sega Master System", "SMS"),
    GAME_GEAR("Sega Game Gear", "GG"),
    SEGA_CD("Sega CD / Mega-CD", "SCD"),
    THIRTY_TWO_X("Sega 32X", "32X"),
    THREE_DO("3DO", "3DO"),
    PHILIPS_CDI("Philips CD-i", "CD-i"),
    PC_ENGINE("PC Engine / TurboGrafx", "PCE"),
    NEO_GEO("SNK Neo Geo", "Neo Geo"),
    NEO_GEO_POCKET("Neo Geo Pocket", "NGP"),
    WONDERSWAN("WonderSwan", "WS"),
    ATARI_2600("Atari 2600", "A26"),
    ATARI_LYNX("Atari Lynx", "Lynx"),
    ATARI_JAGUAR("Atari Jaguar", "Jaguar"),
    AMIGA("Commodore Amiga", "Amiga"),
    ATARI_ST("Atari ST", "ST"),
    ZX_SPECTRUM("ZX Spectrum", "ZX"),
    COLECOVISION("ColecoVision", "CV"),
    INTELLIVISION("Intellivision", "INTV"),
    VECTREX("Vectrex", "Vectrex"),
    GAME_DOT_COM("Game.com", "G.com"),
    PC("PC", "PC"),
    UNKNOWN("Unknown", "?");
}

enum class Region { USA, EUROPE, JAPAN, KOREA, AUSTRALIA, BRAZIL, CHINA, WORLD, UNKNOWN }

enum class ScanStatus { DETECTED, PROBABLY, UNKNOWN, INVALID, MANUAL }

data class DetectionResult(
    val platform: Platform,
    val status: ScanStatus,
    val confidence: Float,           // 0..1
    val format: String,              // e.g. "ISO9660 + UMD", "Raw ROM"
    val region: Region = Region.UNKNOWN,
    val reasons: List<String> = emptyList(),
)

/** A candidate with an accumulating score during layered detection. */
internal data class Candidate(
    val platform: Platform,
    var score: Float = 0f,
    val reasons: MutableList<String> = mutableListOf(),
)
