package com.gtx.cineflow.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val DetailInk = Color(0xFF0B0C10)
private val DetailPanel = Color(0xFF171920)
private val DetailPeach = Color(0xFFFFA17A)
private val DetailCream = Color(0xFFF4F0EA)
private val DetailMuted = Color(0xFFA6A8B2)

/** Additive detail component. Integrators own navigation and repository state collection. */
@Composable
fun AddonDetailScreen(
    detail: AddonMetaDetail?,
    loading: Boolean,
    error: String?,
    onLoadSources: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(DetailInk), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = DetailPanel, contentColor = DetailCream)) {
                Text("Back")
            }
            Spacer(Modifier.width(12.dp))
            Text("Title details", color = DetailCream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (loading && detail == null) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DetailPeach)
            }
        } else if (detail == null) {
            Text(error ?: "Metadata is unavailable.", color = DetailMuted, modifier = Modifier.padding(20.dp))
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)
            ) {
                item {
                    Box(Modifier.fillMaxWidth().height(220.dp).background(DetailPanel)) {
                        val image = detail.item.background ?: detail.item.poster
                        if (!image.isNullOrBlank()) AsyncImage(
                            model = image, contentDescription = detail.item.name,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(detail.item.name, color = DetailCream, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
                        val facts = listOfNotNull(
                            detail.item.releaseInfo?.takeIf(String::isNotBlank),
                            detail.runtime?.takeIf(String::isNotBlank),
                            detail.item.imdbRating?.takeIf(String::isNotBlank)?.let { "★ $it" },
                            detail.item.genres.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                        ).joinToString("  •  ")
                        if (facts.isNotBlank()) Text(facts, color = DetailPeach, fontSize = 12.sp)
                        Text(detail.item.description ?: "No synopsis is available.", color = DetailMuted, fontSize = 14.sp, lineHeight = 21.sp)
                        if (detail.director.isNotEmpty()) Text("Director  ·  ${detail.director.joinToString()}", color = DetailMuted, fontSize = 12.sp)
                        if (detail.cast.isNotEmpty()) Text("Cast  ·  ${detail.cast.take(8).joinToString()}", color = DetailMuted, fontSize = 12.sp)
                        if (!detail.awards.isNullOrBlank()) Text(detail.awards, color = DetailMuted, fontSize = 12.sp)
                        Text("Metadata source: ${detail.providerName}", color = DetailMuted, fontSize = 10.sp)
                        Button(
                            onClick = onLoadSources,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DetailPeach, contentColor = DetailInk),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("View available sources", fontWeight = FontWeight.Bold) }
                    }
                }
                if (detail.videos.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Episodes / videos", color = DetailCream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            detail.videos.forEach { video ->
                                Surface(color = DetailPanel, shape = RoundedCornerShape(12.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(video.title, color = DetailCream, fontWeight = FontWeight.SemiBold)
                                        val episode = listOfNotNull(video.season?.let { "S$it" }, video.episode?.let { "E$it" }).joinToString("")
                                        if (episode.isNotBlank() || !video.released.isNullOrBlank()) Text(listOf(episode, video.released.orEmpty()).filter(String::isNotBlank).joinToString(" · "), color = DetailPeach, fontSize = 11.sp)
                                        if (!video.overview.isNullOrBlank()) Text(video.overview, color = DetailMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Informational source list. P4 will own ranking, URL validation and playback handoff. */
@Composable
fun AddonSourceList(
    sources: List<AddonSource>,
    loading: Boolean,
    error: String?,
    onSourceSelected: (AddonSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Available sources", color = DetailCream, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(color = DetailPeach, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (!error.isNullOrBlank()) Text(error, color = DetailMuted, fontSize = 12.sp)
        sources.forEach { source ->
            Surface(color = DetailPanel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(source.displayName, color = DetailCream, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${source.type.uppercase()} · ${source.addonName}", color = DetailPeach, fontSize = 11.sp)
                        if (source.isTorrent) Text("Torrent descriptor · playback handled in a later phase", color = DetailMuted, fontSize = 11.sp)
                        else Text("Direct source descriptor · playback handled in a later phase", color = DetailMuted, fontSize = 11.sp)
                    }
                    Button(onClick = { onSourceSelected(source) }, colors = ButtonDefaults.buttonColors(containerColor = DetailPeach, contentColor = DetailInk)) {
                        Text("Select")
                    }
                }
            }
        }
        if (!loading && sources.isEmpty() && error.isNullOrBlank()) {
            Text("No sources returned by enabled addons.", color = DetailMuted, fontSize = 12.sp)
        }
    }
}
