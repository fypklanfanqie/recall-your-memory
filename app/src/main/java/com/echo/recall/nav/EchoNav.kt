package com.echo.recall.nav

import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.echo.recall.R
import com.echo.recall.core.data.settings.EchoSettings
import com.echo.recall.core.designsystem.component.GlassDock
import com.echo.recall.core.designsystem.glass.EchoBackground
import com.echo.recall.core.designsystem.glass.GlassHost
import com.echo.recall.core.designsystem.glass.GlassMode
import com.echo.recall.core.designsystem.glass.GlassParams
import com.echo.recall.core.designsystem.glass.ProvideGlassContext
import com.echo.recall.core.designsystem.theme.LocalDarkTheme
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.echo.recall.feature.favorites.FavoritesScreen
import com.echo.recall.feature.memory.MemoryChatScreen
import com.echo.recall.feature.memory.MemoryDetailScreen
import com.echo.recall.feature.memory.MemoryDetailViewModel
import com.echo.recall.feature.memory.MemoryHomeScreen
import com.echo.recall.feature.notes.NotesScreen
import com.echo.recall.feature.settings.AboutScreen
import com.echo.recall.feature.settings.AppStateViewModel
import com.echo.recall.feature.settings.KeepAliveScreen
import com.echo.recall.feature.settings.ModelScreen
import com.echo.recall.feature.settings.ProvidersScreen
import com.echo.recall.feature.settings.SettingsScreen
import com.echo.recall.feature.todos.TodosScreen

/** Dock 五个大功能 */
enum class EchoTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    MEMORY("memory", R.string.dock_memory, Icons.Rounded.GraphicEq, Icons.Rounded.GraphicEq),
    FAVORITES("favorites", R.string.dock_favorites, Icons.Rounded.StarBorder, Icons.Rounded.Star),
    TODOS("todos", R.string.dock_todos, Icons.Rounded.RadioButtonUnchecked, Icons.Rounded.CheckCircle),
    NOTES("notes", R.string.dock_notes, Icons.Rounded.EditNote, Icons.Rounded.EditNote),
    SETTINGS("settings", R.string.dock_settings, Icons.Rounded.Settings, Icons.Rounded.Settings),
}

/** 非 Dock 的二级页面 */
object EchoRoutes {
    const val ARG_MEMORY_ID = MemoryDetailViewModel.ARG_MEMORY_ID
    const val MEMORY_DETAIL = "memory/{$ARG_MEMORY_ID}"
    const val MEMORY_CHAT = "memory/{$ARG_MEMORY_ID}/chat"
    const val MODELS = "models"
    const val PROVIDERS = "providers"
    const val KEEPALIVE = "keepalive"
    const val ABOUT = "about"

    fun memoryDetail(id: String): String = "memory/$id"

    fun memoryChat(id: String): String = "memory/$id/chat"
}

@Composable
fun EchoNavHost(
    settings: EchoSettings,
    viewModel: AppStateViewModel,
) {
    val navController = rememberNavController()
    val dark = LocalDarkTheme.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val glassParams = GlassParams(
        mode = GlassMode.fromId(settings.glassMode),
        blurRadiusDp = settings.glassBlurDp,
        refractionHeightDp = settings.glassRefractionHeightDp,
        refractionAmountDp = settings.glassRefractionAmountDp,
        chromaticAberration = settings.glassChromatic,
        highlightAlpha = settings.glassHighlight,
        tintAlpha = settings.glassTint,
    )

    ProvideGlassContext(params = glassParams, isDark = dark) {
        GlassHost(
            modifier = Modifier.fillMaxSize(),
            content = {
                EchoBackground(
                    dark = dark,
                    backgroundImagePath = settings.backgroundImagePath,
                ) {
                    // 内容全高滚动、延伸到 Dock 底下（图二式「包围」）；
                    // 只保留状态栏顶部留白，底部不再截断——各页列表用 contentPadding 避开 Dock。
                    Scaffold(
                        containerColor = Color.Transparent,
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = EchoTab.MEMORY.route,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = innerPadding.calculateTopPadding()),
                            // iOS 风格转场：推进时新页自右滑入淡入，退出页淡出；返回反向
                            enterTransition = {
                                slideInHorizontally(animationSpec = tween(320)) { it / 3 } + fadeIn(tween(240))
                            },
                            exitTransition = { fadeOut(tween(180)) },
                            popEnterTransition = { fadeIn(tween(240)) },
                            popExitTransition = {
                                slideOutHorizontally(animationSpec = tween(300)) { it / 3 } + fadeOut(tween(200))
                            },
                        ) {
                            composable(EchoTab.MEMORY.route) {
                                MemoryHomeScreen(onOpenMemory = { id ->
                                    navController.navigate(EchoRoutes.memoryDetail(id))
                                })
                            }
                            composable(EchoTab.FAVORITES.route) {
                                FavoritesScreen(onOpenMemory = { id ->
                                    navController.navigate(EchoRoutes.memoryDetail(id))
                                })
                            }
                            composable(EchoTab.TODOS.route) { TodosScreen() }
                            composable(EchoTab.NOTES.route) { NotesScreen() }
                            composable(EchoTab.SETTINGS.route) {
                                SettingsScreen(
                                    settings = settings,
                                    viewModel = viewModel,
                                    onOpenModels = { navController.navigate(EchoRoutes.MODELS) },
                                    onOpenProviders = { navController.navigate(EchoRoutes.PROVIDERS) },
                                    onOpenKeepAlive = { navController.navigate(EchoRoutes.KEEPALIVE) },
                                    onOpenAbout = { navController.navigate(EchoRoutes.ABOUT) },
                                )
                            }
                            composable(EchoRoutes.KEEPALIVE) {
                                KeepAliveScreen(onBack = { navController.popBackStack() })
                            }
                            composable(EchoRoutes.ABOUT) {
                                AboutScreen(onBack = { navController.popBackStack() })
                            }
                            composable(EchoRoutes.MODELS) {
                                ModelScreen(onBack = { navController.popBackStack() })
                            }
                            composable(
                                route = EchoRoutes.MEMORY_DETAIL,
                                arguments = listOf(
                                    navArgument(EchoRoutes.ARG_MEMORY_ID) { type = NavType.StringType },
                                ),
                            ) { entry ->
                                val id = entry.arguments
                                    ?.getString(EchoRoutes.ARG_MEMORY_ID).orEmpty()
                                MemoryDetailScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenChat = { navController.navigate(EchoRoutes.memoryChat(id)) },
                                    onOpenProviders = { navController.navigate(EchoRoutes.PROVIDERS) },
                                )
                            }
                            composable(
                                route = EchoRoutes.MEMORY_CHAT,
                                arguments = listOf(
                                    navArgument(EchoRoutes.ARG_MEMORY_ID) { type = NavType.StringType },
                                ),
                            ) {
                                MemoryChatScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenProviders = { navController.navigate(EchoRoutes.PROVIDERS) },
                                )
                            }
                            composable(EchoRoutes.PROVIDERS) {
                                ProvidersScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            },
            overlay = {
                val haptic = LocalHapticFeedback.current
                GlassDock(
                    items = EchoTab.entries,
                    currentRoute = currentRoute,
                    onSelect = { tab ->
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                )
            },
        )
    }
}
