package com.imadlabs.iplayer.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.imadlabs.iplayer.AppContainer
import com.imadlabs.iplayer.playback.PlayerEvent
import com.imadlabs.iplayer.ui.about.AboutScreen
import com.imadlabs.iplayer.ui.about.LicensesScreen
import com.imadlabs.iplayer.ui.browse.BrowseScreen
import com.imadlabs.iplayer.ui.browse.FolderScreen
import com.imadlabs.iplayer.ui.browse.NetworkBrowserScreen
import com.imadlabs.iplayer.ui.home.HomeScreen
import com.imadlabs.iplayer.ui.library.LibraryScreen
import com.imadlabs.iplayer.ui.library.PlaylistScreen
import com.imadlabs.iplayer.ui.music.AlbumScreen
import com.imadlabs.iplayer.ui.music.ArtistScreen
import com.imadlabs.iplayer.ui.music.GenreScreen
import com.imadlabs.iplayer.ui.music.MiniPlayer
import com.imadlabs.iplayer.ui.music.MusicScreen
import com.imadlabs.iplayer.ui.music.NowPlayingScreen
import com.imadlabs.iplayer.ui.settings.DiagnosticsScreen
import com.imadlabs.iplayer.ui.settings.EqualizerScreen
import com.imadlabs.iplayer.ui.settings.SettingsCategoryScreen
import com.imadlabs.iplayer.ui.settings.SettingsScreen
import com.imadlabs.iplayer.ui.startup.StartupDialogsHost
import com.imadlabs.iplayer.ui.videos.ShowScreen
import com.imadlabs.iplayer.ui.videos.VideosScreen

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

object Routes {
    const val HOME = "home"
    const val VIDEOS = "videos"
    const val MUSIC = "music"
    const val BROWSE = "browse"
    const val LIBRARY = "library"
    const val NOW_PLAYING = "now_playing"
    const val SETTINGS = "settings"
    const val SETTINGS_CATEGORY = "settings/{category}"
    const val EQUALIZER = "equalizer"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
    const val DIAGNOSTICS = "diagnostics"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{name}"
    const val GENRE = "genre/{name}"
    const val FOLDER = "folder/{path}"
    const val PLAYLIST = "playlist/{id}"
    const val SHOW = "show/{name}"
    const val NETWORK = "network/{sourceId}?path={path}"

    fun settings(category: String) = "settings/$category"
    fun album(id: Long) = "album/$id"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun genre(name: String) = "genre/${Uri.encode(name)}"
    fun folder(path: String) = "folder/${Uri.encode(path)}"
    fun playlist(id: Long) = "playlist/$id"
    fun show(name: String) = "show/${Uri.encode(name)}"
    fun network(sourceId: Long, path: String? = null) = "network/$sourceId" + (path?.let { "?path=${Uri.encode(it)}" } ?: "")
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, "Home", Icons.Rounded.Home),
    TopLevel(Routes.VIDEOS, "Videos", Icons.Rounded.Movie),
    TopLevel(Routes.MUSIC, "Music", Icons.Rounded.LibraryMusic),
    TopLevel(Routes.BROWSE, "Browse", Icons.Rounded.FolderOpen),
    TopLevel(Routes.LIBRARY, "Library", Icons.Rounded.VideoLibrary),
)

@Composable
fun AppShell(pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val container = LocalContainer.current
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTopLevel = topLevel.any { it.route == route }
    val player by container.playback.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) { nav.navigate(pendingRoute) { launchSingleTop = true }; onRouteConsumed() }
    }
    LaunchedEffect(Unit) {
        container.playback.events.collect { e ->
            // Video notices are shown by the player screen itself.
            if (e is PlayerEvent.Notice && container.playback.state.value.item?.isVideo != true) snackbar.showSnackbar(e.text)
        }
    }

    PermissionGate()
    StartupDialogsHost()

    val showMini = player.item != null && player.item?.isVideo == false && route != Routes.NOW_PLAYING

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxSize()) {
                if (wide && isTopLevel) NavRail(route, nav)
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets(0),
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        Column(Modifier.fillMaxWidth()) {
                            AnimatedVisibility(showMini, enter = fadeIn(), exit = fadeOut()) {
                                MiniPlayer(onOpen = { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } })
                            }
                            if (!wide && isTopLevel) BottomNav(route, nav)
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        AppNavHost(nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNav(route: String?, nav: NavHostController) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        topLevel.forEach { t ->
            NavigationBarItem(
                selected = route == t.route,
                onClick = { nav.navigateTopLevel(t.route) },
                icon = { Icon(t.icon, contentDescription = null) },
                label = { Text(t.label) },
            )
        }
    }
}

@Composable
private fun NavRail(route: String?, nav: NavHostController) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        topLevel.forEach { t ->
            NavigationRailItem(
                selected = route == t.route,
                onClick = { nav.navigateTopLevel(t.route) },
                icon = { Icon(t.icon, contentDescription = null) },
                label = { Text(t.label) },
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    val open: (String) -> Unit = { nav.navigate(it) }
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(open) }
        composable(Routes.VIDEOS) { VideosScreen(open) }
        composable(Routes.MUSIC) { MusicScreen(open) }
        composable(Routes.BROWSE) { BrowseScreen(open) }
        composable(Routes.LIBRARY) { LibraryScreen(open) }
        composable(Routes.NOW_PLAYING) { NowPlayingScreen(back, open) }
        composable(Routes.SETTINGS) { SettingsScreen(back, open) }
        composable(Routes.SETTINGS_CATEGORY) { e -> SettingsCategoryScreen(e.arguments?.getString("category").orEmpty(), back, open) }
        composable(Routes.EQUALIZER) { EqualizerScreen(back) }
        composable(Routes.ABOUT) { AboutScreen(back, open) }
        composable(Routes.LICENSES) { LicensesScreen(back) }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(back) }
        composable(Routes.ALBUM, listOf(navArgument("id") { type = NavType.LongType })) { e ->
            AlbumScreen(e.arguments?.getLong("id") ?: -1, back, open)
        }
        composable(Routes.ARTIST) { e -> ArtistScreen(e.arguments?.getString("name").orEmpty(), back, open) }
        composable(Routes.GENRE) { e -> GenreScreen(e.arguments?.getString("name").orEmpty(), back) }
        composable(Routes.FOLDER) { e -> FolderScreen(e.arguments?.getString("path").orEmpty(), back) }
        composable(Routes.PLAYLIST, listOf(navArgument("id") { type = NavType.LongType })) { e ->
            PlaylistScreen(e.arguments?.getLong("id") ?: -1, back)
        }
        composable(Routes.SHOW) { e -> ShowScreen(e.arguments?.getString("name").orEmpty(), back) }
        composable(
            Routes.NETWORK,
            listOf(navArgument("sourceId") { type = NavType.LongType },
                navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { e ->
            NetworkBrowserScreen(e.arguments?.getLong("sourceId") ?: 0, e.arguments?.getString("path"), back, open)
        }
    }
}
