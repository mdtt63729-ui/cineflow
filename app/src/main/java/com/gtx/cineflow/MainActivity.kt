package com.gtx.cineflow

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

private val Ink = Color(0xFF0B0C10)
private val Panel = Color(0xFF171920)
private val Panel2 = Color(0xFF22242D)
private val Peach = Color(0xFFFFA17A)
private val Cream = Color(0xFFF4F0EA)
private val Muted = Color(0xFFA6A8B2)
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
    val prefs = remember { context.getSharedPreferences("cineflow", Context.MODE_PRIVATE) }
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
    var apiBase by remember { mutableStateOf(prefs.getString("api_base", "") ?: "") }
    var tmdbKey by remember { mutableStateOf(prefs.getString("tmdb_key", "") ?: "") }
    var jellyfinServer by remember { mutableStateOf(prefs.getString("jellyfin_server", "") ?: "") }
    var jellyfinUser by remember { mutableStateOf(prefs.getString("jellyfin_user", "") ?: "") }
    var jellyfinToken by remember { mutableStateOf(prefs.getString("jellyfin_token", "") ?: "") }
    var jellyfinUserId by remember { mutableStateOf(prefs.getString("jellyfin_user_id", "") ?: "") }
    val tmdbApi = remember { makeTmdbApi() }
    val addonCatalog = remember { AddonCatalogRepository(context) }
    val addonRows by addonCatalog.homeRows.collectAsState()
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

    LaunchedEffect(apiRevision) {
        // Seed the built-in add-ons once, then load their catalogues for the home feed.
        // Failures stay non-fatal: unreachable add-ons are simply skipped.
        runCatching {
            addonCatalog.installBuiltInsIfMissing()
            addonCatalog.refreshHome()
        }
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

    LaunchedEffect(apiRevision) {
        loading = true
        var loaded = false
        if (api != null) {
            try {
                val remote = withContext(Dispatchers.IO) { api.home() }
                if (remote.sections.isNotEmpty()) { sections = remote.sections; remote.sections.flatMap { it.items }.forEach { knownMovies[it.id] = it }; loaded = true }
            } catch (_: Exception) { /* Continue to the next configured source. */ }
        }
        if (jellyfinServer.isNotBlank() && jellyfinToken.isNotBlank() && jellyfinUserId.isNotBlank()) {
            try {
                val jf = withContext(Dispatchers.IO) {
                    val service = makeJellyfinApi(jellyfinServer)
                    val libraries = service.views(jellyfinUserId, jellyfinToken).Items
                    libraries.mapNotNull { library ->
                        val media = service.items(jellyfinUserId, jellyfinToken, parentId = library.Id).Items
                            .map { it.toCineFlow(jellyfinServer, jellyfinToken) }
                        if (media.isEmpty()) null else HomeSection("Jellyfin · ${library.Name}", media)
                    }
                }
                if (jf.isNotEmpty()) { sections = jf + sections.filterNot { it.title.startsWith("Jellyfin ·") }; jf.flatMap { it.items }.forEach { knownMovies[it.id] = it }; loaded = true }
            } catch (_: Exception) { /* Keep cached and metadata feeds available if the server is offline. */ }
        }
        if (!loaded && tmdbKey.isNotBlank()) {
            try {
                val key = tmdbKey.trim()
                val remoteSections = withContext(Dispatchers.IO) {
                    listOf(
                        HomeSection(
                            "Indian movies · Hindi, Bengali & regional",
                            tmdbApi.indianMovies(key).results.map { it.copy(mediaType = "movie").toMovie() }
                        ),
                        HomeSection(
                            "Indian series · Hindi, Bengali & regional",
                            tmdbApi.indianTv(key).results.map { it.copy(mediaType = "tv").toMovie() }
                        )
                    ).filter { it.items.isNotEmpty() }
                }
                if (remoteSections.isNotEmpty()) { sections = remoteSections; remoteSections.flatMap { it.items }.forEach { knownMovies[it.id] = it }; loaded = true }
            } catch (_: Exception) { /* UI remains usable offline. */ }
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
                    if (api != null) withContext(Dispatchers.IO) { api.search(query).items }
                    else if (tmdbKey.isNotBlank()) withContext(Dispatchers.IO) { tmdbApi.search(tmdbKey.trim(), query).results.filter { it.mediaType != "person" }.map { it.toMovie() } }
                    else emptyList()
                } catch (_: Exception) { emptyList() }
                val localJellyfin = sections.filter { it.title.startsWith("Jellyfin ·") }.flatMap { it.items }
                    .filter { it.title.contains(query, true) || it.overview.contains(query, true) }
                results = (remote + localJellyfin).distinctBy { it.id }.ifEmpty {
                    when {
                        api != null || tmdbKey.isNotBlank() || jellyfinToken.isNotBlank() -> emptyList()
                        else -> sampleMovies.filter { it.title.contains(query, true) || it.overview.contains(query, true) }
                    }
                }
                results.forEach { knownMovies[it.id] = it }
            }
        }
    }
    BackHandler(enabled = playing != null || current != null || settingsOpen) {
        when {
            settingsOpen -> settingsOpen = false
            playing != null -> playing = null
            current != null -> current = null
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Peach, onPrimary = Ink, background = Ink, onBackground = Cream,
            surface = Panel, onSurface = Cream, surfaceVariant = Panel2, onSurfaceVariant = Muted
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Crossfade(targetState = Triple(playing, current, settingsOpen), animationSpec = tween(220), label = "screen") { state ->
                when {
                    state.first != null -> FullPlayer(
                        movie = state.first!!,
                        onBack = { playing = null },
                        initialPositionMs = playbackProgress[state.first!!.id]?.positionMs ?: 0L,
                        onProgress = { movie, position, duration, completed ->
                            knownMovies[movie.id] = movie
                            if (movie.id.startsWith("jellyfin:") && jellyfinToken.isNotBlank()) {
                                val report = JellyfinPlaybackReport(
                                    ItemId = movie.id.removePrefix("jellyfin:"),
                                    PositionTicks = position.coerceAtLeast(0L) * 10_000L
                                )
                                scope.launch(Dispatchers.IO) {
                                    runCatching { makeJellyfinApi(jellyfinServer).playbackProgress(jellyfinToken, report) }
                                }
                            }
                            if (completed || (duration > 0L && position >= duration - 15_000L)) playbackProgress.remove(movie.id)
                            else if (position > 0L) playbackProgress[movie.id] = PlaybackProgress(movie.id, position, duration, System.currentTimeMillis())
                            persistPlaybackProgress()
                            prefs.edit().putString("known_movies", com.google.gson.Gson().toJson(knownMovies.values.toList())).apply()
                        },
                        onPlaybackStarted = { movie ->
                            if (movie.id.startsWith("jellyfin:") && jellyfinToken.isNotBlank()) {
                                val report = JellyfinPlaybackReport(ItemId = movie.id.removePrefix("jellyfin:"))
                                scope.launch(Dispatchers.IO) {
                                    runCatching { makeJellyfinApi(jellyfinServer).playbackStarted(jellyfinToken, report) }
                                }
                            }
                        },
                        onPlaybackStopped = { movie, position ->
                            if (movie.id.startsWith("jellyfin:") && jellyfinToken.isNotBlank()) {
                                val report = JellyfinPlaybackReport(ItemId = movie.id.removePrefix("jellyfin:"), PositionTicks = position.coerceAtLeast(0L) * 10_000L)
                                scope.launch(Dispatchers.IO) {
                                    runCatching { makeJellyfinApi(jellyfinServer).playbackStopped(jellyfinToken, report) }
                                }
                            }
                        },
                        onRequestUrl = {
                            val item = state.first!!
                            val remoteUrl = try {
                                if (api != null && !item.id.startsWith("sample-") && !item.id.startsWith("tmdb:") && !item.id.startsWith("jellyfin:")) withContext(Dispatchers.IO) { api.stream(item.id).url } else ""
                            } catch (_: Exception) { "" }
                            when {
                                item.id.startsWith("jellyfin:") -> item.streamUrl.takeIf { safeHttps(it) }.orEmpty()
                                item.id.startsWith("sample-") -> item.streamUrl.takeIf { safeHttps(it) }.orEmpty()
                                safeHttps(remoteUrl) -> remoteUrl
                                else -> ""
                            }
                        }
                    )
                    state.second != null -> DetailScreen(
                        movie = state.second!!,
                        canPlay = state.second!!.streamUrl.isNotBlank() || state.second!!.id.startsWith("sample-") || state.second!!.id.startsWith("jellyfin:"),
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
                        initial = apiBase, initialTmdbKey = tmdbKey,
                        initialJellyfinServer = jellyfinServer, initialJellyfinUser = jellyfinUser,
                        initialJellyfinToken = jellyfinToken, initialJellyfinUserId = jellyfinUserId,
                        onBack = { settingsOpen = false },
                        onSave = { value, key, jfServer, jfUser, jfToken, jfUserId ->
                            apiBase = value.trim()
                            tmdbKey = key.trim()
                            jellyfinServer = jfServer.trim().trimEnd('/')
                            jellyfinUser = jfUser.trim()
                            jellyfinToken = jfToken
                            jellyfinUserId = jfUserId
                            prefs.edit().putString("api_base", apiBase).putString("tmdb_key", tmdbKey)
                                .putString("jellyfin_server", jellyfinServer).putString("jellyfin_user", jellyfinUser)
                                .putString("jellyfin_token", jellyfinToken).putString("jellyfin_user_id", jellyfinUserId).apply()
                            apiRevision++
                            settingsOpen = false
                        }
                    )
                    else -> Scaffold(
                        containerColor = Ink,
                        bottomBar = {
                            NavigationBar(containerColor = Color(0xFF101116), contentColor = Cream) {
                                NavigationBarItem(
                                    selected = tab == "Home", onClick = { tab = "Home" },
                                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") },
                                    colors = navColors()
                                )
                                NavigationBarItem(
                                    selected = tab == "Search", onClick = { tab = "Search" },
                                    icon = { Icon(Icons.Default.Search, null) }, label = { Text("Search") },
                                    colors = navColors()
                                )
                                NavigationBarItem(
                                    selected = tab == "Saved", onClick = { tab = "Saved" },
                                    icon = { Icon(Icons.Default.Bookmark, null) }, label = { Text("Saved") },
                                    colors = navColors()
                                )
                            }
                        }
                    ) { padding ->
                        when (tab) {
                            "Home" -> HomeScreen(
                                sections = buildList {
                                    val continueItems = playbackProgress.values.sortedByDescending { it.updatedAt }.mapNotNull { progress -> knownMovies[progress.movieId] }
                                    if (continueItems.isNotEmpty()) add(HomeSection("Continue watching", continueItems))
                                    addAll(addonSections)
                                    addAll(sections)
                                }, loading = loading, onOpen = { current = it },
                                onPlay = { playing = it }, onSettings = { settingsOpen = true },
                                onSearch = { tab = "Search" }
                            )
                            "Search" -> SearchScreen(query, { query = it }, results, onOpen = { current = it })
                            else -> MovieGrid(
                                title = "Your watchlist",
                                movies = knownMovies.values.filter { watchlist.contains(it.id) },
                                onOpen = { current = it }
                            )
                        }
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
    sections: List<HomeSection>, loading: Boolean, onOpen: (Movie) -> Unit,
    onPlay: (Movie) -> Unit, onSettings: () -> Unit, onSearch: () -> Unit
) {
    val hero = sections.firstOrNull()?.items?.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Peach), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Ink, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("CineFlow", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Cream)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search", tint = Cream) }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Tune, "Settings", tint = Cream) }
            }
        }
        if (hero != null) {
            item { HeroBanner(hero, onOpen, onPlay) }
        } else if (!loading) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Indian cinema, one place.", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Add your TMDB API key in Settings → Integrations to load Indian movies and series across Hindi, Bengali and other regional languages. Catalog metadata does not confirm whether a title has a Hindi or Bengali dub.",
                        color = Muted, fontSize = 14.sp, lineHeight = 20.sp
                    )
                    OutlinedButton(onClick = onSettings) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp)); Text("Set up catalogue") }
                }
            }
        }
        if (loading) {
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
                Text("FEATURED", Modifier.padding(horizontal = 11.dp, vertical = 5.dp), color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            }
            Text(movie.title, fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${movie.year}  •  ★ ${movie.rating}  •  ${movie.kind}", color = Cream.copy(alpha = .82f), fontSize = 13.sp)
            Text(movie.overview, color = Cream.copy(alpha = .85f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, lineHeight = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onPlay(movie) }, colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = Ink), shape = RoundedCornerShape(13.dp)) {
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
                    Button(onClick = onPlay, enabled = canPlay, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = Ink, disabledContainerColor = Panel2, disabledContentColor = Muted), shape = RoundedCornerShape(13.dp)) {
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
    initial: String, initialTmdbKey: String, initialJellyfinServer: String, initialJellyfinUser: String,
    initialJellyfinToken: String, initialJellyfinUserId: String, onBack: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    var key by remember(initialTmdbKey) { mutableStateOf(initialTmdbKey) }
    var jfServer by remember(initialJellyfinServer) { mutableStateOf(initialJellyfinServer) }
    var jfUser by remember(initialJellyfinUser) { mutableStateOf(initialJellyfinUser) }
    var jfPassword by remember { mutableStateOf("") }
    var jfToken by remember(initialJellyfinToken) { mutableStateOf(initialJellyfinToken) }
    var jfUserId by remember(initialJellyfinUserId) { mutableStateOf(initialJellyfinUserId) }
    var jfStatus by remember { mutableStateOf(if (initialJellyfinToken.isNotBlank()) "Connected session saved" else "Not connected") }
    var jfBusy by remember { mutableStateOf(false) }
    val jfScope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Cream) }
                Text("Integrations", color = Cream, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        item { Text("TMDB metadata", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text("Trending, popular movies and TV, posters, and multi-search. Create your own key in your TMDB account settings. Metadata does not include streaming links.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) }
        item {
            OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("TMDB API key (v3)") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Peach, focusedTextColor = Cream, unfocusedTextColor = Cream, cursorColor = Peach))
        }
        item { HorizontalDivider(color = Panel2) }
        item { Text("Jellyfin personal media server", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text("Connect to your own Jellyfin server or one you are authorized to access. Use an HTTPS server URL for remote connections. Your password is used only for sign-in and is not saved; the session token is stored locally on this device.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) }
        item { OutlinedTextField(value = jfServer, onValueChange = { jfServer = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Jellyfin server HTTPS URL") }, placeholder = { Text("https://jellyfin.example.com") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Peach, focusedTextColor = Cream, unfocusedTextColor = Cream, cursorColor = Peach)) }
        item { OutlinedTextField(value = jfUser, onValueChange = { jfUser = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Peach, focusedTextColor = Cream, unfocusedTextColor = Cream, cursorColor = Peach)) }
        item { OutlinedTextField(value = jfPassword, onValueChange = { jfPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password (not stored)") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Peach, focusedTextColor = Cream, unfocusedTextColor = Cream, cursorColor = Peach)) }
        item { Text(jfStatus, color = if (jfToken.isNotBlank()) Peach else Muted, fontSize = 12.sp) }
        item {
            OutlinedButton(onClick = {
                jfScope.launch {
                    jfBusy = true
                    jfStatus = "Connecting…"
                    try {
                        val base = jfServer.trim().trimEnd('/')
                        if (!safeHttps(base) || jfUser.isBlank() || jfPassword.isBlank()) throw IllegalArgumentException("Enter an HTTPS server URL, username and password.")
                        val response = withContext(Dispatchers.IO) {
                            makeJellyfinApi("$base/").login(jellyfinAuthorizationHeader(), JellyfinLoginRequest(jfUser.trim(), jfPassword))
                        }
                        if (response.AccessToken.isBlank() || response.User.Id.isBlank()) throw IllegalStateException("Server did not return a valid session.")
                        jfToken = response.AccessToken
                        jfUserId = response.User.Id
                        jfPassword = ""
                        jfStatus = "Connected as ${response.User.Name.ifBlank { jfUser }} · tap Save settings"
                    } catch (e: Exception) { jfStatus = "Connection failed: ${e.message?.take(120) ?: "Check server and credentials"}" }
                    jfBusy = false
                }
            }, enabled = !jfBusy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Peach)) {
                if (jfBusy) CircularProgressIndicator(Modifier.size(18.dp), color = Peach, strokeWidth = 2.dp) else Icon(Icons.Default.Cloud, null)
                Spacer(Modifier.width(8.dp)); Text(if (jfBusy) "Connecting…" else "Connect to Jellyfin")
            }
        }
        item { Text("After saving a successful connection, CineFlow loads your Jellyfin libraries on Home. Playback uses your server's stream endpoint; server format and network compatibility may vary.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp) }
        item { HorizontalDivider(color = Panel2) }
        item { Text("Authorized catalogue backend", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text("Optional custom backend for catalogue and playable streams you are authorized to access. Its API contract is documented in README.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) }
        item {
            OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth(), label = { Text("HTTPS API base URL") }, placeholder = { Text("https://api.example.com/") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Peach, focusedTextColor = Cream, unfocusedTextColor = Cream, cursorColor = Peach))
        }
        item { Text("Endpoints: home, search?q=…, detail/{id}, stream/{id}. Keep metadata and stream providers separate; the stream endpoint must return an authorized HTTPS URL.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
        item {
            Button(onClick = { onSave(value, key, jfServer, jfUser, jfToken, jfUserId) }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = Ink), shape = RoundedCornerShape(15.dp)) {
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
    onRequestUrl: suspend () -> String
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var streamUrl by remember(movie.id) { mutableStateOf<String?>(null) }
    var failure by remember(movie.id) { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    DisposableEffect(movie.id) {
        val oldOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = oldOrientation }
    }
    LaunchedEffect(movie.id) {
        try {
            val resolved = onRequestUrl()
            if (safeHttps(resolved)) streamUrl = resolved else failure = true
        } catch (_: Exception) { failure = true }
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
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failure = true }
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
                if (failure) Text("No valid HTTPS stream was returned. Check your authorized media API.", color = Muted, fontSize = 12.sp)
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
