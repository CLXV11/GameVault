package com.clxv.gamevault.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clxv.gamevault.R
import com.clxv.gamevault.core.model.Platform

/** Generated brand-tile icon for each platform (res/drawable-nodpi/platform_*.png). */
fun platformIconRes(p: Platform): Int = when (p) {
    Platform.PS1 -> R.drawable.platform_ps1
    Platform.PS2 -> R.drawable.platform_ps2
    Platform.PS3 -> R.drawable.platform_ps3
    Platform.PS4 -> R.drawable.platform_ps4
    Platform.PS5 -> R.drawable.platform_ps5
    Platform.PSP -> R.drawable.platform_psp
    Platform.VITA -> R.drawable.platform_vita
    Platform.PS_PKG -> R.drawable.platform_ps_pkg
    Platform.NES -> R.drawable.platform_nes
    Platform.SNES -> R.drawable.platform_snes
    Platform.N64 -> R.drawable.platform_n64
    Platform.GAME_BOY -> R.drawable.platform_game_boy
    Platform.GAME_BOY_COLOR -> R.drawable.platform_game_boy_color
    Platform.GBA -> R.drawable.platform_gba
    Platform.NINTENDO_DS -> R.drawable.platform_nintendo_ds
    Platform.NINTENDO_3DS -> R.drawable.platform_nintendo_3ds
    Platform.GAMECUBE -> R.drawable.platform_gamecube
    Platform.WII -> R.drawable.platform_wii
    Platform.WII_U -> R.drawable.platform_wii_u
    Platform.SWITCH -> R.drawable.platform_switch
    Platform.XBOX -> R.drawable.platform_xbox
    Platform.XBOX_360 -> R.drawable.platform_xbox_360
    Platform.DREAMCAST -> R.drawable.platform_dreamcast
    Platform.SATURN -> R.drawable.platform_saturn
    Platform.MEGA_DRIVE -> R.drawable.platform_mega_drive
    Platform.MASTER_SYSTEM -> R.drawable.platform_master_system
    Platform.GAME_GEAR -> R.drawable.platform_game_gear
    Platform.SEGA_CD -> R.drawable.platform_sega_cd
    Platform.THIRTY_TWO_X -> R.drawable.platform_thirty_two_x
    Platform.THREE_DO -> R.drawable.platform_three_do
    Platform.PHILIPS_CDI -> R.drawable.platform_philips_cdi
    Platform.PC_ENGINE -> R.drawable.platform_pc_engine
    Platform.NEO_GEO -> R.drawable.platform_neo_geo
    Platform.NEO_GEO_POCKET -> R.drawable.platform_neo_geo_pocket
    Platform.WONDERSWAN -> R.drawable.platform_wonderswan
    Platform.ATARI_2600 -> R.drawable.platform_atari_2600
    Platform.ATARI_LYNX -> R.drawable.platform_atari_lynx
    Platform.ATARI_JAGUAR -> R.drawable.platform_atari_jaguar
    Platform.AMIGA -> R.drawable.platform_amiga
    Platform.ATARI_ST -> R.drawable.platform_atari_st
    Platform.ZX_SPECTRUM -> R.drawable.platform_zx_spectrum
    Platform.COLECOVISION -> R.drawable.platform_colecovision
    Platform.INTELLIVISION -> R.drawable.platform_intellivision
    Platform.VECTREX -> R.drawable.platform_vectrex
    Platform.GAME_DOT_COM -> R.drawable.platform_game_dot_com
    Platform.PC -> R.drawable.platform_pc
    Platform.UNKNOWN -> R.drawable.platform_unknown
}

@Composable
fun PlatformIcon(
    platform: Platform,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(id = platformIconRes(platform)),
        contentDescription = platform.label,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

/** Look a platform up by its short name (used for auto-generated collections). */
fun platformByShort(short: String): Platform? =
    Platform.entries.firstOrNull { it.short == short }
