package com.clxv.gamevault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clxv.gamevault.ui.collections.CollectionsScreen
import com.clxv.gamevault.ui.detail.CoverEditorScreen
import com.clxv.gamevault.ui.detail.GameDetailScreen
import com.clxv.gamevault.ui.duplicates.DuplicatesScreen
import com.clxv.gamevault.ui.library.LibraryScreen
import com.clxv.gamevault.ui.settings.SettingsScreen

object Routes {
    const val LIBRARY = "library"
    const val GAME = "game/{gameId}"
    const val SETTINGS = "settings"
    const val COLLECTIONS = "collections"
    const val DUPLICATES = "duplicates"
    const val COVER_EDITOR = "cover/{gameId}"
    fun game(id: String) = "game/$id"
    fun coverEditor(id: String) = "cover/$id"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenGame = { nav.navigate(Routes.game(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenCollections = { nav.navigate(Routes.COLLECTIONS) },
                onOpenDuplicates = { nav.navigate(Routes.DUPLICATES) },
            )
        }
        composable(Routes.GAME, arguments = listOf(navArgument("gameId") { type = NavType.StringType })) {
            GameDetailScreen(onBack = { nav.popBackStack() }, onEditCover = { nav.navigate(Routes.coverEditor(it)) })
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.COLLECTIONS) { CollectionsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DUPLICATES) { DuplicatesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.COVER_EDITOR, arguments = listOf(navArgument("gameId") { type = NavType.StringType })) {
            CoverEditorScreen(onBack = { nav.popBackStack() })
        }
    }
}
