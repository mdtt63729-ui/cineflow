package com.gtx.cineflow.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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

private val AddonInk = Color(0xFF0B0C10)
private val AddonPanel = Color(0xFF171920)
private val AddonPeach = Color(0xFFFFA17A)
private val AddonCream = Color(0xFFF4F0EA)
private val AddonMuted = Color(0xFFA6A8B2)

/** P2 home shelves. Errors are shown per shelf without suppressing healthy addon catalogs. */
@Composable
fun AddonHomeCatalogs(
    rows: List<CatalogResult>,
    loading: Boolean,
    onItemClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        if (loading && rows.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = AddonPeach, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Loading addon catalogs…", color = AddonMuted, fontSize = 13.sp)
            }
        }
        val grouped = rows.groupBy { result ->
            "${result.request.addon.manifest.name} · ${result.request.catalog.name ?: result.request.catalog.id}"
        }
        grouped.forEach { (title, catalogRows) ->
            val result = catalogRows.first()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = AddonCream, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (result.error != null) {
                            Text("This catalog is temporarily unavailable", color = AddonMuted, fontSize = 12.sp)
                        } else {
                            Text("${catalogRows.sumOf { it.items.size }} titles", color = AddonMuted, fontSize = 11.sp)
                        }
                    }
                    if (result.error != null) {
                        Text("Unavailable", color = AddonPeach, fontSize = 11.sp)
                    }
                }
                val items = catalogRows.filter { it.error == null }.flatMap { it.items }
                    .distinctBy { "${it.type}:${it.id}:${it.addonBaseUrl}" }
                if (items.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items.forEach { item ->
                            AddonPosterCard(item = item, onClick = { onItemClick(item) })
                        }
                    }
                }
            }
        }
        if (!loading && rows.isEmpty()) {
            Surface(
                color = AddonPanel,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No addon catalogs yet", color = AddonCream, fontWeight = FontWeight.Bold)
                    Text("Install and enable an addon in the Addons settings to populate these shelves.", color = AddonMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AddonPosterCard(item: CatalogItem, onClick: () -> Unit) {
    Column(
        Modifier.width(118.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(13.dp)).background(AddonPanel),
            contentAlignment = Alignment.Center
        ) {
            if (!item.poster.isNullOrBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(item.name.take(1).uppercase(), color = AddonPeach, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            if (!item.releaseInfo.isNullOrBlank()) {
                Surface(
                    color = AddonInk.copy(alpha = .84f),
                    shape = RoundedCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(item.releaseInfo.take(12), color = AddonCream, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                }
            }
        }
        Text(item.name, color = AddonCream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        val meta = listOfNotNull(item.type.takeIf(String::isNotBlank), item.imdbRating?.takeIf(String::isNotBlank)?.let { "★ $it" }).joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, color = AddonMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Search result cards are intentionally metadata-only in P2; P3 supplies detail navigation. */
@Composable
fun AddonSearchResults(
    items: List<CatalogItem>,
    loading: Boolean,
    onItemClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Add-on results", color = AddonCream, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(color = AddonPeach, modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
            else Text("${items.size}", color = AddonMuted, fontSize = 12.sp)
        }
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AddonPanel)
                    .clickable(onClick = { onItemClick(item) }).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(width = 66.dp, height = 92.dp).clip(RoundedCornerShape(9.dp)).background(AddonInk), contentAlignment = Alignment.Center) {
                    if (!item.poster.isNullOrBlank()) {
                        AsyncImage(model = item.poster, contentDescription = item.name, modifier = Modifier.fillMaxWidth().height(92.dp), contentScale = ContentScale.Crop)
                    } else {
                        Text(item.name.take(1).uppercase(), color = AddonPeach, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, color = AddonCream, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${item.type} · ${item.releaseInfo.orEmpty()}", color = AddonPeach, fontSize = 11.sp)
                    Text(item.description.orEmpty().ifBlank { "No description provided by this catalog." }, color = AddonMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("Source: ${item.addonBaseUrl}", color = AddonMuted.copy(alpha = .8f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!loading && items.isEmpty()) {
            Text("No add-on results. Some catalogs do not support search.", color = AddonMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}
