package com.gtx.cineflow

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.gtx.cineflow.addons.AddonCatalogRepository
import com.gtx.cineflow.addons.AddonClient
import com.gtx.cineflow.addons.AddonPlaybackResolver
import com.gtx.cineflow.addons.AddonResult
import com.gtx.cineflow.addons.AddonSource
import com.gtx.cineflow.addons.AddonSourceRanker
import com.gtx.cineflow.addons.AddonSourceRepository
import com.gtx.cineflow.addons.PlaybackResolution
import com.gtx.cineflow.addons.extensions.ExtensionRepository
import com.gtx.cineflow.addons.extensions.ExtensionsScreen
import com.gtx.cineflow.addons.CatalogItem
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.net.URI

// Theme palette. Dark is the MovieBox-style default; Light mirrors it for bright rooms.
internal class AppPalette(
    val ink: Color, val panel: Color, val panel2: Color,
    val accent: Color, val onAccent: Color, val cream: Color, val muted: Color
)

internal val DarkPalette = AppPalette(
    ink = Color(0xFF070A0F), panel = Color(0xFF121821), panel2 = Color(0xFF1C2530),
    accent = Color(0xFF00E08F), onAccent = Color(0xFF04120C),
    cream = Color(0xFFF3F6F8), muted = Color(0xFF93A1AE)
)

internal val LightPalette = AppPalette(
    ink = Color(0xFFF4F6F9), panel = Color(0xFFFFFFFF), panel2 = Color(0xFFE5EAF0),
    accent = Color(0xFF00A86B), onAccent = Color(0xFFFFFFFF),
    cream = Color(0xFF0C1420), muted = Color(0xFF5B6675)
)

internal val appPalette = mutableStateOf(DarkPalette)

internal val Ink: Color get() = appPalette.value.ink
internal val Panel: Color get() = appPalette.value.panel
internal val Panel2: Color get() = appPalette.value.panel2
internal val Peach: Color get() = appPalette.value.accent
internal val OnAccent: Color get() = appPalette.value.onAccent
internal val Cream: Color get() = appPalette.value.cream
internal val Muted: Color get() = appPalette.value.muted
private const val SAMPLE_STREAM = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

data class Movie(
    val id: String = "",
    val title: String = "Untitled",
    val overview: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val year: String = "",
    val rating: String = "",
    val kind: String = "Movie",
    val streamUrl: String = ""
)

/** Maps a Stremio-style catalogue entry into the app's Movie model. */
private fun CatalogItem.toMovie(): Movie = Movie(
    id = id,
    title = name,
    overview = description.orEmpty(),
    posterUrl = poster.orEmpty(),
    backdropUrl = background ?: poster.orEmpty(),
    year = releaseInfo?.take(4).orEmpty(),
    rating = imdbRating ?: "—",
    kind = if (type.equals("series", true) || type.equals("tv", true)) "TV" else "Movie",
    streamUrl = ""
)
data class AddonCard(
    val label: String = "",
    val url: String = "",
    val purpose: String = "",
    val status: String = "Not installed",
    val configurable: Boolean = false
)

data class HomeSection(val title: String = "Featured", val items: List<Movie> = emptyList())
data class HomeResponse(val sections: List<HomeSection> = emptyList())
data class MovieListResponse(val items: List<Movie> = emptyList())
data class StreamResponse(val url: String = "", val title: String = "")
data class PlaybackProgress(val movieId: String = "", val positionMs: Long = 0L, val durationMs: Long = 0L, val updatedAt: Long = 0L)

interface CatalogueApi {
    @GET("home") suspend fun home(): HomeResponse
    @GET("search") suspend fun search(@Query("q") query: String): MovieListResponse
    @GET("detail/{id}") suspend fun detail(@Path("id") id: String): Movie
    @GET("stream/{id}") suspend fun stream(@Path("id") id: String): StreamResponse
}


private fun makeApi(context: Context): CatalogueApi? {
    val prefs = context.getSharedPreferences("cineflow", Context.MODE_PRIVATE)
    val base = prefs.getString("api_base", "")?.trim().orEmpty()
    if (base.isBlank()) return null
    val normalized = if (base.endsWith("/")) base else "$base/"
    return try {
        val uri = URI(normalized)
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) null
        else Retrofit.Builder().baseUrl(normalized)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(CatalogueApi::class.java)
    } catch (_: Exception) { null }
}

// No fabricated international demo titles are shown as recommendations.
private val sampleMovies = emptyList<Movie>()
private fun sampleSections() = emptyList<HomeSection>()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 12, 16)
        window.navigationBarColor = android.graphics.Color.rgb(11, 12, 16)
        setContent { CineFlowApp() }
    }
}

@Composable
fun CineFlowApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val prefs = remember { context.getSharedPreferences("cineflow", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    appPalette.value = if (effectiveDark) DarkPalette else LightPalette
    val activity = context as? ComponentActivity
    LaunchedEffect(effectiveDark) {
        val bg = if (effectiveDark) android.graphics.Color.rgb(7, 10, 15) else android.graphics.Color.rgb(244, 246, 249)
        val window = activity?.window ?: return@LaunchedEffect
        window.statusBarColor = bg
        window.navigationBarColor = bg
    }
    var apiRevision by remember { mutableIntStateOf(0) }
    val api = remember(apiRevision) { makeApi(context) }
    var sections by remember { mutableStateOf(sampleSections()) }
    var loading by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf<Movie?>(null) }
    var playing by remember { mutableStateOf<Movie?>(null) }
    var tab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(sampleMovies) }
    var settingsOpen by remember { mutableStateOf(false) }
    var extensionsOpen by remember { mutableStateOf(false) }
    val extensionRepo = remember { ExtensionRepository(context) }
    var bannerAutoSlide by remember { mutableStateOf(prefs.getBoolean("banner_auto_slide", true)) }
    var apiBase by remember { mutableStateOf(prefs.getString("api_base", "") ?: "") }
    var addonMessage by remember { mutableStateOf<String?>(null) }
    val addonCatalog = remember { AddonCatalogRepository(context) }
    val addonRows by addonCatalog.homeRows.collectAsState()
    val installedAddons by addonCatalog.addons.collectAsState()
    val addonSummary by addonCatalog.installSummary.collectAsState()
    val addonLoading by addonCatalog.isLoadingHome.collectAsState()
    val addonError by addonCatalog.homeError.collectAsState()
    var addonBusy by remember { mutableStateOf(false) }
    val knownMovies = remember {
        mutableStateMapOf<String, Movie>().apply {
            sampleMovies.forEach { put(it.id, it) }
            try {
                val saved = com.google.gson.Gson().fromJson(prefs.getString("known_movies", "[]"), Array<Movie>::class.java).orEmpty()
                saved.forEach { put(it.id, it) }
            } catch (_: Exception) { }
        }
    }
    val playbackProgress = remember {
        mutableStateMapOf<String, PlaybackProgress>().apply {
            try {
                val saved = com.google.gson.Gson().fromJson(prefs.getString("playback_progress", "[]"), Array<PlaybackProgress>::class.java).orEmpty()
                saved.filter { it.positionMs > 0L && (it.durationMs <= 0L || it.positionMs < it.durationMs - 15_000L) }.forEach { put(it.movieId, it) }
            } catch (_: Exception) { }
        }
    }
    fun persistPlaybackProgress() {
        prefs.edit().putString("playback_progress", com.google.gson.Gson().toJson(playbackProgress.values.toList())).apply()
    }
    val watchlist = remember {
        mutableStateListOf<String>().apply { addAll(prefs.getStringSet("watchlist_ids", emptySet()).orEmpty()) }
    }
    fun persistWatchlist() {
        prefs.edit().putStringSet("watchlist_ids", watchlist.toSet())
            .putString("known_movies", com.google.gson.Gson().toJson(knownMovies.values.toList())).apply()
    }
    val history = remember {
        mutableStateListOf<String>().apply {
            try {
                val saved = com.google.gson.Gson().fromJson(prefs.getString("history_ids", "[]"), Array<String>::class.java).orEmpty()
                addAll(saved)
            } catch (_: Exception) { }
        }
    }
    fun recordHistory(movie: Movie) {
        history.remove(movie.id)
        history.add(0, movie.id)
        while (history.size > 60) history.removeAt(history.size - 1)
        knownMovies[movie.id] = movie
        prefs.edit().putString("history_ids", com.google.gson.Gson().toJson(history.toList())).apply()
    }

    LaunchedEffect(apiRevision) {
        // Seed the built-in add-ons once, then load their catalogues for the home feed.
        // Failures stay non-fatal: unreachable add-ons are simply skipped.
        runCatching { addonCatalog.refreshHome() }
    }

    val addonSections = remember(addonRows) {
        addonRows.filter { it.error == null && it.items.isNotEmpty() }.map { row ->
            val addonName = row.request.addon.manifest.name
            val catalogName = row.request.catalog.name ?: row.request.catalog.id
            HomeSection("$addonName · $catalogName", row.items.map { it.toMovie() })
        }
    }
    LaunchedEffect(addonSections) {
        addonSections.forEach { section -> section.items.forEach { knownMovies[it.id] = it } }
    }

    // Catalogue entries keyed by movie id so a tap on Play can resolve the add-on's streams.
    val addonItems = remember { mutableStateMapOf<String, CatalogItem>() }
    val addonSourceRepo = remember { AddonSourceRepository(context) }
    LaunchedEffect(addonRows) {
        addonRows.forEach { row -> row.items.forEach { addonItems[it.id] = it } }
    }

    // Only the add-ons the user added themselves; nothing is pre-installed.
    val addonCards = remember(installedAddons) {
        installedAddons.map { record ->
            AddonCard(
                label = record.manifest.name.ifBlank { record.baseUrl },
                url = record.baseUrl,
                purpose = if (record.enabled) "Enabled" else "Disabled",
                status = if (record.enabled) "Installed" else "Disabled",
                configurable = record.manifest.behaviorHints?.configurable == true ||
                    record.manifest.behaviorHints?.configurationRequired == true,
            )
        }
    }

    LaunchedEffect(apiRevision) {
        loading = true
        var loaded = false
        if (api != null) {
            try {
                val remote = withContext(Dispatchers.IO) { api.home() }
                if (remote.sections.isNotEmpty()) { sections = remote.sections; remote.sections.flatMap { it.items }.forEach { knownMovies[it.id] = it }; loaded = true }
            } catch (_: Exception) { /* Continue to the next configured source. */ }
        }
        if (!loaded && sections.isEmpty()) sections = sampleSections()
        loading = false
    }
    LaunchedEffect(query, apiRevision) {
        if (tab == "Search") {
            delay(300)
            if (query.isBlank()) results = sections.flatMap { it.items }.ifEmpty { sampleMovies }
            else {
                val remote = try {
                    if (api != null) withContext(Dispatchers.IO) { api.search(query).items } else emptyList()
                } catch (_: Exception) { emptyList() }
                val local = sections.flatMap { it.items }
                    .filter { it.title.contains(query, true) || it.overview.contains(query, true) }
                results = (remote + local).distinctBy { it.id }.ifEmpty {
                    if (api != null) emptyList()
                    else sampleMovies.filter { it.title.contains(query, true) || it.overview.contains(query, true) }
                }
                results.forEach { knownMovies[it.id] = it }
            }
        }
    }
    BackHandler(enabled = playing != null || current != null || settingsOpen || extensionsOpen) {
        when {
            extensionsOpen -> extensionsOpen = false
            settingsOpen -> settingsOpen = false
            playing != null -> playing = null
            current != null -> current = null
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Peach, onPrimary = OnAccent, background = Ink, onBackground = Cream,
            surface = Panel, onSurface = Cream, surfaceVariant = Panel2, onSurfaceVariant = Muted
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Crossfade(targetState = Triple(playing, current, settingsOpen), animationSpec = tween(220), label = "screen") { state ->
                when {
                    extensionsOpen -> ExtensionsScreen(
                        repo = extensionRepo,
                        onBack = { extensionsOpen = false }
                    )
                    state.first != null -> FullPlayer(
                        movie = state.first!!,
                        onBack = { playing = null },
                        initialPositionMs = playbackProgress[state.first!!.id]?.positionMs ?: 0L,
                        onProgress = { movie, position, duration, completed ->
                            knownMovies[movie.id] = movie
                            if (completed || (duration > 0L && position >= duration - 15_000L)) playbackProgress.remove(movie.id)
                            else if (position > 0L) playbackProgress[movie.id] = PlaybackProgress(movie.id, position, duration, System.currentTimeMillis())
                            persistPlaybackProgress()
                            prefs.edit().putString("known_movies", com.google.gson.Gson().toJson(knownMovies.values.toList())).apply()
                        },
                        onPlaybackStarted = { movie -> knownMovies[movie.id] = movie },
                        onPlaybackStopped = { _, _ -> },
                        onRequestUrls = {
                            val item = state.first!!
                            val urls = mutableListOf<String>()
                            if (item.id.startsWith("sample-")) {
                                item.streamUrl.takeIf { safeHttps(it) }?.let { urls += it }
                            }
                            if (urls.isEmpty() && api != null && !item.id.startsWith("sample-")) {
                                runCatching { withContext(Dispatchers.IO) { api.stream(item.id).url } }
                                    .getOrNull()?.takeIf { safeHttps(it) }?.let { urls += it }
                            }
                            if (urls.isEmpty()) {
                                addonItems[item.id]?.let { catalogItem ->
                                    val sources = runCatching {
                                        withContext(Dispatchers.IO) {
                                            addonSourceRepo.load(catalogItem)
                                                .filterIsInstance<AddonResult.Success<List<AddonSource>>>()
                                                .flatMap { it.value }
                                        }
                                    }.getOrDefault(emptyList())
                                    AddonSourceRanker.rank(sources).forEach { source ->
                                        val resolution = runCatching { AddonPlaybackResolver.resolve(source) }.getOrNull()
                                        if (resolution is PlaybackResolution.Playable) {
                                            resolution.item.uri.toString().takeIf { safeHttps(it) }?.let { urls += it }
                                        }
                                    }
                                }
                            }
                            urls.distinct()
                        }
                    )
                    state.second != null -> DetailScreen(
                        movie = state.second!!,
                        canPlay = state.second!!.streamUrl.isNotBlank() || state.second!!.id.startsWith("sample-") || addonItems.containsKey(state.second!!.id),
                        saved = watchlist.contains(state.second!!.id),
                        onBack = { current = null },
                        onPlay = { playing = state.second },
                        onToggleSaved = {
                            val selected = state.second!!
                            val id = selected.id
                            knownMovies[id] = selected
                            if (watchlist.contains(id)) watchlist.remove(id) else watchlist.add(id)
                            persistWatchlist()
                        }
                    )
                    state.third -> SettingsScreen(
                        initial = apiBase,
                        onBack = { settingsOpen = false },
                        onOpenExtensions = { settingsOpen = false; extensionsOpen = true },
                        addonSummary = addonSummary,
                        addonCards = addonCards,
                        addonBusy = addonBusy,
                        addonMessage = addonMessage,
                        onInstallAddon = { url ->
                            scope.launch {
                                addonBusy = true
                                addonMessage = null
                                val result = runCatching { addonCatalog.install(url) }.getOrNull()
                                addonMessage = when (result) {
                                    null -> "Could not reach that URL."
                                    is AddonResult.Failure -> "Not installable: ${result.error.message}"
                                    is AddonResult.Success -> "Installed ${result.value.manifest.name}."
                                    else -> null
                                }
                                runCatching { addonCatalog.refreshHome() }
                                addonBusy = false
                            }
                        },
                        onDeleteAddon = { url ->
                            scope.launch {
                                addonBusy = true
                                runCatching { addonCatalog.uninstall(url) }
                                runCatching { addonCatalog.refreshHome() }
                                addonBusy = false
                            }
                        },
                        bannerAutoSlide = bannerAutoSlide,
                        onBannerAutoSlideChange = { checked ->
                            bannerAutoSlide = checked
                            prefs.edit().putBoolean("banner_auto_slide", checked).apply()
                        },
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            prefs.edit().putString("theme_mode", mode).apply()
                        },
                        onSave = { value ->
                            apiBase = value.trim()
                            prefs.edit().putString("api_base", apiBase).apply()
                            apiRevision++
                            settingsOpen = false
                        }
                    )
                    else -> Box(Modifier.fillMaxSize().background(Ink)) {
                        val activeListState = if (tab == "Library") libraryListState else homeListState
                        val navVisible = navVisibleFrom(activeListState)
                        when (tab) {
                            "Home" -> HomeScreen(
                                sections = buildList {
                                    val continueItems = playbackProgress.values.sortedByDescending { it.updatedAt }.mapNotNull { progress -> knownMovies[progress.movieId] }
                                    if (continueItems.isNotEmpty()) add(HomeSection("Continue watching", continueItems))
                                    addAll(addonSections)
                                    addAll(sections)
                                }, loading = loading, addonLoading = addonLoading, addonError = addonError,
                                bannerAutoSlide = bannerAutoSlide, onOpen = { current = it },
                                onPlay = { playing = it; recordHistory(it) }, onSettings = { settingsOpen = true },
                                onSearch = { tab = "Search" }, onRefresh = { apiRevision++ },
                                listState = homeListState
                            )
                            "Search" -> SearchScreen(query, { query = it }, results, onOpen = { current = it })
                            else -> LibraryScreen(
                                continueItems = playbackProgress.values.sortedByDescending { it.updatedAt }.mapNotNull { knownMovies[it.movieId] },
                                historyItems = history.mapNotNull { knownMovies[it] },
                                watchlistItems = knownMovies.values.filter { watchlist.contains(it.id) },
                                onOpen = { current = it },
                                listState = libraryListState
                            )
                        }
                        AnimatedVisibility(
                            visible = navVisible,
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                            enter = slideInVertically(
                                initialOffsetY = { it * 3 },
                                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(animationSpec = tween(180)) +
                                scaleIn(initialScale = 0.86f, animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)),
                            exit = slideOutVertically(
                                targetOffsetY = { it * 3 },
                                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
                            ) + fadeOut(animationSpec = tween(140)) +
                                scaleOut(targetScale = 0.86f, animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
                        ) {
                            FloatingNavBar(selected = tab, onSelect = { tab = it })
                        }
                    }
                }
            }
        }
    }
}

/** Hides the floating bar while scrolling forward and brings it back the moment you scroll back. */
@Composable
private fun navVisibleFrom(state: LazyListState): Boolean {
    var visible by remember { mutableStateOf(true) }
    var lastIndex by remember { mutableIntStateOf(0) }
    var lastOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val forward = index > lastIndex || (index == lastIndex && offset > lastOffset + 6)
                val back = index < lastIndex || (index == lastIndex && offset < lastOffset - 6)
                if (forward && (index > 0 || offset > 60)) visible = false
                if (back) visible = true
                lastIndex = index
                lastOffset = offset
            }
    }
    return visible
}

/** Material 3 style floating navigation bar with a spring-animated selection pill. */
@Composable
fun FloatingNavBar(selected: String, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple("Home", Icons.Default.Home, "Home"),
        Triple("Search", Icons.Default.Search, "Search"),
        Triple("Library", Icons.Default.VideoLibrary, "Library"),
    )
    val selectedIndex = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    Surface(
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Panel2,
        shadowElevation = 16.dp
    ) {
        BoxWithConstraints(Modifier.padding(6.dp).height(58.dp)) {
            val itemWidth = maxWidth / items.size
            val indicatorX by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
                label = "navIndicator"
            )
            Box(
                Modifier.offset(x = indicatorX).width(itemWidth).height(58.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Peach.copy(alpha = 0.16f))
            )
            Row(Modifier.fillMaxWidth().height(58.dp)) {
                items.forEachIndexed { index, item ->
                    val active = index == selectedIndex
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.14f else 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                        label = "navScale"
                    )
                    Column(
                        Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(22.dp))
                            .clickable { onSelect(item.first) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(item.second, item.third, tint = if (active) Peach else Muted, modifier = Modifier.size(22.dp).scale(iconScale))
                        Spacer(Modifier.height(3.dp))
                        Text(item.third, color = if (active) Peach else Muted, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Peach, selectedTextColor = Peach,
    indicatorColor = Peach.copy(alpha = 0.12f),
    unselectedIconColor = Muted, unselectedTextColor = Muted
)

@Composable
fun HomeScreen(
    sections: List<HomeSection>, loading: Boolean, addonLoading: Boolean, addonError: String?,
    bannerAutoSlide: Boolean, onOpen: (Movie) -> Unit,
    onPlay: (Movie) -> Unit, onSettings: () -> Unit, onSearch: () -> Unit, onRefresh: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val heroItems = sections.firstOrNull()?.items.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(com.gtx.cineflow.R.drawable.app_logo),
                    contentDescription = "CineFlow",
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text("CineFlow", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Cream)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh", tint = Cream) }
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search", tint = Cream) }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Tune, "Settings", tint = Cream) }
            }
        }
        if (heroItems.isNotEmpty()) {
            item { HeroCarousel(heroItems, onOpen, onPlay, bannerAutoSlide) }
        } else if (!loading && !addonLoading) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Nothing loaded yet.", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        addonError ?: "Add your TMDB API key in Settings → Integrations for Indian movies, or install add-ons from Settings → Add-ons.",
                        color = Muted, fontSize = 14.sp, lineHeight = 20.sp
                    )
                    OutlinedButton(onClick = onSettings) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp)); Text("Set up catalogue") }
                }
            }
        }
        if (loading || addonLoading) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth(0.45f).height(22.dp).clip(RoundedCornerShape(8.dp)).background(Panel2))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { Box(Modifier.width(126.dp).height(178.dp).clip(RoundedCornerShape(14.dp)).background(Panel)) }
                    }
                }
            }
        }
        items(sections) { section ->
            MovieShelf(section.title, section.items, onOpen)
        }
    }
}

@Composable
fun HeroCarousel(items: List<Movie>, onOpen: (Movie) -> Unit, onPlay: (Movie) -> Unit, autoSlide: Boolean) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    if (autoSlide && items.size > 1) {
        LaunchedEffect(pagerState, items.size) {
            while (true) {
                delay(5_000)
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
                }
            }
        }
    }
    Column {
        HorizontalPager(state = pagerState) { page ->
            HeroBanner(items[page], onOpen, onPlay)
        }
        if (items.size > 1) {
            val shown = items.size.coerceAtMost(10)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(shown) { index ->
                    val active = (pagerState.currentPage % shown) == index
                    Box(
                        Modifier.padding(horizontal = 3.dp)
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) Peach else Muted.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    continueItems: List<Movie>, historyItems: List<Movie>, watchlistItems: List<Movie>,
    onOpen: (Movie) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Text(
                "Library", color = Cream, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
        if (continueItems.isNotEmpty()) item { MovieShelf("Continue watching", continueItems, onOpen) }
        if (historyItems.isNotEmpty()) item { MovieShelf("Watch history", historyItems, onOpen) }
        item {
            if (watchlistItems.isEmpty()) {
                Text(
                    "Your watchlist is empty. Save a title with the bookmark button on its details page.",
                    color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp)
                )
            } else {
                MovieShelf("Watchlist", watchlistItems, onOpen)
            }
        }
    }
}

@Composable
fun HeroBanner(movie: Movie, onOpen: (Movie) -> Unit, onPlay: (Movie) -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(430.dp)
            .clip(RoundedCornerShape(26.dp)).background(Panel)
    ) {
        if (movie.backdropUrl.isNotBlank()) AsyncImage(
            model = movie.backdropUrl, contentDescription = movie.title,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.05f),
            0.42f to Color.Black.copy(alpha = 0.16f),
            1f to Color(0xFF090A0D)
        )))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = Peach, shape = RoundedCornerShape(50)) {
                Text("FEATURED", Modifier.padding(horizontal = 11.dp, vertical = 5.dp), color = OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            }
            Text(movie.title, fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${movie.year}  •  ★ ${movie.rating}  •  ${movie.kind}", color = Cream.copy(alpha = .82f), fontSize = 13.sp)
            Text(movie.overview, color = Cream.copy(alpha = .85f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, lineHeight = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onPlay(movie) }, colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = OnAccent), shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { onOpen(movie) }, shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream)) {
                    Icon(Icons.Default.Info, null); Spacer(Modifier.width(5.dp)); Text("Details")
                }
            }
        }
    }
}

@Composable
fun MovieShelf(title: String, movies: List<Movie>, onOpen: (Movie) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Cream)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) { movies.forEach { MoviePoster(it, onOpen) } }
    }
}

@Composable
fun MoviePoster(movie: Movie, onOpen: (Movie) -> Unit) {
    Column(Modifier.width(132.dp).clickable { onOpen(movie) }, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.fillMaxWidth().height(182.dp).clip(RoundedCornerShape(15.dp)).background(Panel2)) {
            if (movie.posterUrl.isNotBlank()) AsyncImage(
                model = movie.posterUrl, contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            Surface(Modifier.align(Alignment.TopEnd).padding(7.dp), color = Color.Black.copy(alpha = .72f), shape = RoundedCornerShape(7.dp)) {
                Text("★ ${movie.rating.ifBlank { "—" }}", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = Peach, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(movie.title, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(movie.year, color = Muted, fontSize = 11.sp)
    }
}

@Composable
fun SearchScreen(query: String, onQuery: (String) -> Unit, results: List<Movie>, onOpen: (Movie) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
        Text("Discover", Modifier.padding(top = 18.dp, bottom = 16.dp), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Cream)
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search movies and series…", color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, null) } },
            shape = RoundedCornerShape(16.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Peach, unfocusedBorderColor = Panel2,
                focusedTextColor = Cream, unfocusedTextColor = Cream,
                cursorColor = Peach, focusedContainerColor = Panel, unfocusedContainerColor = Panel
            )
        )
        Spacer(Modifier.height(18.dp))
        Text(if (query.isBlank()) "Popular titles" else "Results", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        if (results.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No titles found", color = Muted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(results, key = { it.id }) { movie ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).clickable { onOpen(movie) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = movie.posterUrl, contentDescription = null, modifier = Modifier.size(width = 82.dp, height = 112.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(movie.title, color = Cream, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${movie.year} • ${movie.kind}", color = Muted, fontSize = 12.sp)
                            Text("★ ${movie.rating}", color = Peach, fontSize = 12.sp)
                            Text(movie.overview, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }
    }
}

@Composable
fun MovieGrid(title: String, movies: List<Movie>, onOpen: (Movie) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
        Text(title, Modifier.padding(vertical = 20.dp), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Cream)
        if (movies.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BookmarkBorder, null, tint = Muted, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Your saved titles appear here", color = Muted)
                }
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) { items(movies.size) { index -> MoviePoster(movies[index], onOpen) } }
        }
    }
}

@Composable
fun DetailScreen(movie: Movie, canPlay: Boolean, saved: Boolean, onBack: () -> Unit, onPlay: () -> Unit, onToggleSaved: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(360.dp).background(Panel)) {
                AsyncImage(model = movie.backdropUrl.ifBlank { movie.posterUrl }, contentDescription = movie.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .08f), Ink))))
                IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp).align(Alignment.TopStart).clip(CircleShape).background(Color.Black.copy(alpha = .45f))) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Cream)
                }
                AsyncImage(model = movie.posterUrl, contentDescription = null, modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 0.dp).width(126.dp).height(176.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(movie.title, color = Cream, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp)
                Text("${movie.year}  •  ${movie.kind}  •  ★ ${movie.rating}", color = Peach, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onPlay, enabled = canPlay, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = OnAccent, disabledContainerColor = Panel2, disabledContentColor = Muted), shape = RoundedCornerShape(13.dp)) {
                        Icon(if (canPlay) Icons.Default.PlayArrow else Icons.Default.Info, null); Spacer(Modifier.width(5.dp)); Text(if (canPlay) "Watch now" else "Metadata only")
                    }
                    OutlinedButton(onClick = onToggleSaved, shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream)) {
                        Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null)
                        Spacer(Modifier.width(5.dp)); Text(if (saved) "Saved" else "Save")
                    }
                }
                Text("Synopsis", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(movie.overview.ifBlank { "No synopsis is available for this title yet." }, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ClosedCaption, null, tint = Peach)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Playback", color = Cream, fontWeight = FontWeight.SemiBold)
                            Text("Available quality depends on your authorized media source.", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    initial: String, onBack: () -> Unit, onOpenExtensions: () -> Unit,
    addonSummary: String, addonCards: List<AddonCard>, addonBusy: Boolean, addonMessage: String?,
    onInstallAddon: (String) -> Unit, onDeleteAddon: (String) -> Unit,
    bannerAutoSlide: Boolean, onBannerAutoSlideChange: (Boolean) -> Unit,
    themeMode: String, onThemeModeChange: (String) -> Unit,
    onSave: (String) -> Unit
) {
    var addonUrl by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    var value by remember(initial) { mutableStateOf(initial) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Cream) }
                Text("Settings", color = Cream, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        item { HorizontalDivider(color = Panel2) }
        item { Text("Extensions", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text("Browse CloudStream-style extension repositories and download providers.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) }
        item {
            OutlinedButton(onClick = onOpenExtensions, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Peach)) {
                Icon(Icons.Default.Extension, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open Extensions")
            }
        }
        item { HorizontalDivider(color = Panel2) }
        item { Text("Add-ons", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text("Public add-ons install automatically on first launch. Each card shows its status; use Configure when an add-on needs its own settings. These are third-party sources — availability and legality vary by provider.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) }
        item { Text(addonSummary, color = Peach, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        if (addonBusy) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Peach, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Working on add-ons…", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = addonUrl, onValueChange = { addonUrl = it },
                    modifier = Modifier.weight(1f), label = { Text("Add-on URL") },
                    placeholder = { Text("https://example.com/manifest.json") },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Peach, focusedTextColor = Cream, unfocusedTextColor = Cream, cursorColor = Peach)
                )
                Button(
                    onClick = { onInstallAddon(addonUrl.trim()) },
                    enabled = addonUrl.isNotBlank() && !addonBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = OnAccent),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Install") }
            }
        }
        addonMessage?.let { message ->
            item { Text(message, color = Peach, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        items(addonCards) { card ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(card.label, color = Cream, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = when (card.status) { "Installed" -> Peach; "Not installable" -> Color(0xFFE5484D); else -> Panel2 },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(card.status, Modifier.padding(horizontal = 9.dp, vertical = 3.dp), color = if (card.status == "Installed") OnAccent else Cream, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(card.purpose, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(card.url, color = Muted.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (card.status == "Installed") {
                            OutlinedButton(onClick = { onDeleteAddon(card.url) }, enabled = !addonBusy, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B))) { Text("Delete", fontSize = 12.sp) }
                        } else {
                            Button(onClick = { onInstallAddon(card.url) }, enabled = !addonBusy, colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = OnAccent), shape = RoundedCornerShape(12.dp)) { Text("Install", fontSize = 12.sp) }
                        }
                        if (card.configurable) {
                            OutlinedButton(onClick = { runCatching { uriHandler.openUri(card.url + "/configure") } }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream)) { Text("Configure", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        item { HorizontalDivider(color = Panel2) }
        item { Text("Home banner", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-slide banners", color = Cream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Slide the featured banner right to left automatically.", color = Muted, fontSize = 12.sp)
                }
                Switch(checked = bannerAutoSlide, onCheckedChange = onBannerAutoSlideChange)
            }
        }
        item { HorizontalDivider(color = Panel2) }
        item { Text("Appearance", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text("Choose the app theme.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("light" to "Light", "dark" to "Dark", "system" to "System").forEach { (value, label) ->
                    if (themeMode == value) {
                        Button(onClick = { onThemeModeChange(value) }, colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = OnAccent), shape = RoundedCornerShape(12.dp)) { Text(label, fontSize = 12.sp) }
                    } else {
                        OutlinedButton(onClick = { onThemeModeChange(value) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream)) { Text(label, fontSize = 12.sp) }
                    }
                }
            }
        }
        item { HorizontalDivider(color = Panel2) }
        item { Text("Authorized catalogue backend", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text("Optional custom backend for catalogue and playable streams you are authorized to access. Its API contract is documented in README.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) }
        item {
            OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth(), label = { Text("HTTPS API base URL") }, placeholder = { Text("https://api.example.com/") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Peach, focusedTextColor = Cream, unfocusedTextColor = Cream, cursorColor = Peach))
        }
        item { Text("Endpoints: home, search?q=…, detail/{id}, stream/{id}. Keep metadata and stream providers separate; the stream endpoint must return an authorized HTTPS URL.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
        item {
            Button(onClick = { onSave(value) }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = OnAccent), shape = RoundedCornerShape(15.dp)) {
                Text("Save settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FullPlayer(
    movie: Movie,
    onBack: () -> Unit,
    initialPositionMs: Long,
    onProgress: (Movie, Long, Long, Boolean) -> Unit,
    onPlaybackStarted: (Movie) -> Unit,
    onPlaybackStopped: (Movie, Long) -> Unit,
    onRequestUrls: suspend () -> List<String>
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var candidates by remember(movie.id) { mutableStateOf<List<String>>(emptyList()) }
    var attempt by remember(movie.id) { mutableIntStateOf(0) }
    var failure by remember(movie.id) { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    val streamUrl = candidates.getOrNull(attempt)

    DisposableEffect(movie.id) {
        val oldOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = oldOrientation }
    }
    LaunchedEffect(movie.id) {
        candidates = try { onRequestUrls() } catch (_: Exception) { emptyList() }
        attempt = 0
        failure = candidates.isEmpty()
    }
    val player = remember(streamUrl) {
        streamUrl?.let { url ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                if (initialPositionMs > 0L) seekTo(initialPositionMs)
                prepare()
                playWhenReady = true
                var reportedStarted = false
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && !reportedStarted) {
                            reportedStarted = true
                            onPlaybackStarted(movie)
                        }
                        if (playbackState == Player.STATE_ENDED) onProgress(movie, currentPosition, duration.coerceAtLeast(0L), true)
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        // Fall through to the next ranked source before giving up.
                        if (attempt + 1 < candidates.size) attempt += 1 else failure = true
                    }
                })
            }
        }
    }
    DisposableEffect(player) {
        onDispose {
            player?.let { active ->
                if (active.currentPosition > 0L && active.duration > 0L && active.playbackState != Player.STATE_ENDED) {
                    onProgress(movie, active.currentPosition, active.duration, false)
                }
                onPlaybackStopped(movie, active.currentPosition.coerceAtLeast(0L))
                active.release()
            }
        }
    }
    LaunchedEffect(player, movie.id) {
        val activePlayer = player ?: return@LaunchedEffect
        while (isActive) {
            delay(5_000)
            if (activePlayer.duration > 0L && activePlayer.currentPosition > 0L) {
                onProgress(movie, activePlayer.currentPosition, activePlayer.duration, activePlayer.playbackState == Player.STATE_ENDED)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).clickable { showControls = !showControls }) {
        if (player != null) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS) } },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!failure) CircularProgressIndicator(color = Peach)
                Text(if (failure) "Playback could not start" else "Preparing stream…", color = Cream)
                if (failure) Text(
                    "No playable stream was found. Open the title again, or add/configure an add-on in Settings → Add-ons. Torrent-only sources need a debrid add-on.",
                    color = Muted, fontSize = 12.sp
                )
            }
        }
        if (showControls) {
            Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .55f))) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("CineFlow player", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                }
            }
        }
    }
}

private fun safeHttps(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme == "https" && !uri.host.isNullOrBlank()
} catch (_: Exception) { false }
