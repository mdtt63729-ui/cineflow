package com.gtx.cineflow.addons.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gtx.cineflow.Cream
import com.gtx.cineflow.Ink
import com.gtx.cineflow.Muted
import com.gtx.cineflow.OnAccent
import com.gtx.cineflow.Panel
import com.gtx.cineflow.Panel2
import com.gtx.cineflow.Peach
import kotlinx.coroutines.launch

/** CloudStream-style extension repository browser. Entries come from real repositories. */
@Composable
fun ExtensionsScreen(repo: ExtensionRepository, onBack: () -> Unit) {
    val repos by repo.repos.collectAsState()
    val plugins by repo.plugins.collectAsState()
    val installed by repo.installed.collectAsState()
    val loading by repo.isLoading.collectAsState()
    val status by repo.status.collectAsState()
    val busy by repo.busyUrl.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var repoUrl by remember { mutableStateOf("") }
    var showRepos by remember { mutableStateOf(false) }

    LaunchedEffect(repos) { runCatching { repo.refresh() } }

    val tabs = listOf("All", "Movies", "TV Series", "Anime", "Asian Dramas")
    val visible = plugins.filter { plugin ->
        val inCategory = category == "All" || plugin.categories.contains(category)
        val matches = query.isBlank() ||
            plugin.displayName.contains(query, ignoreCase = true) ||
            (plugin.description ?: "").contains(query, ignoreCase = true)
        inCategory && matches
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Cream) }
                Text("Extensions", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { runCatching { repo.refresh() } } }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Cream)
                }
                IconButton(onClick = { showRepos = !showRepos }) {
                    Icon(Icons.Default.Settings, "Repositories", tint = if (showRepos) Peach else Cream)
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search extensions") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Peach, focusedTextColor = Cream,
                    unfocusedTextColor = Cream, cursorColor = Peach
                )
            )
        }
        if (showRepos) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Repositories", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        repos.forEach { url ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    url, color = Muted, fontSize = 11.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    repo.removeRepo(url)
                                    scope.launch { runCatching { repo.refresh() } }
                                }) { Icon(Icons.Default.Delete, "Remove", tint = Peach) }
                            }
                        }
                        OutlinedTextField(
                            value = repoUrl, onValueChange = { repoUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Add repository URL") },
                            placeholder = { Text("cloudstreamrepo://…/repo.json") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Peach, focusedTextColor = Cream,
                                unfocusedTextColor = Cream, cursorColor = Peach
                            )
                        )
                        Button(
                            onClick = {
                                repo.addRepo(repoUrl)
                                repoUrl = ""
                                scope.launch { runCatching { repo.refresh() } }
                            },
                            enabled = repoUrl.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = OnAccent),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Add repository") }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { tab ->
                    val selected = tab == category
                    Surface(
                        color = if (selected) Peach else Panel2,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { category = tab }
                    ) {
                        Text(
                            tab,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            color = if (selected) OnAccent else Cream,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
        if (loading || status != null) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Peach, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        if (loading) "Loading extensions…" else status.orEmpty(),
                        color = Muted, fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Text(
                "${visible.size} extensions",
                color = Muted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
        items(visible) { plugin ->
            ExtensionCard(
                plugin = plugin,
                installed = installed.contains(plugin.url),
                busy = busy == plugin.url,
                onInstall = { scope.launch { runCatching { repo.install(plugin) } } },
                onUninstall = { scope.launch { runCatching { repo.uninstall(plugin) } } },
                onSettings = {
                    val target = plugin.repositoryUrl ?: plugin.url
                    if (!target.isNullOrBlank()) runCatching { uriHandler.openUri(target) }
                }
            )
        }
    }
}

@Composable
private fun ExtensionCard(
    plugin: ExtensionPlugin,
    installed: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Panel2),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = plugin.iconUrl
                    if (!icon.isNullOrBlank()) {
                        AsyncImage(
                            model = icon, contentDescription = plugin.displayName,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            plugin.displayName.take(1).uppercase(),
                            color = Peach, fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        plugin.displayName, color = Cream, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${ExtensionFormat.flag(plugin.language)} ${ExtensionFormat.languageName(plugin.language)}" +
                            (plugin.version?.let { "  •  v$it" } ?: "") +
                            (ExtensionFormat.size(plugin.fileSize).takeIf { it.isNotEmpty() }?.let { "  •  $it" } ?: ""),
                        color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (plugin.statusLabel.isNotEmpty()) {
                        Text(
                            plugin.statusLabel,
                            color = if (plugin.isBroken) androidx.compose.ui.graphics.Color(0xFFE5484D) else Peach,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (installed) {
                    Surface(color = Peach, shape = RoundedCornerShape(50)) {
                        Text(
                            "Installed", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = OnAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (!plugin.description.isNullOrBlank()) {
                Text(
                    plugin.description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = Panel2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (installed) {
                    OutlinedButton(
                        onClick = onUninstall, enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color(0xFFFF6B6B))
                    ) { Text("Uninstall", fontSize = 12.sp) }
                } else {
                    Button(
                        onClick = onInstall, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = OnAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = OnAccent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Download", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onSettings) { Text("Settings", color = Cream, fontSize = 12.sp) }
                Spacer(Modifier.weight(1f))
                Text(
                    plugin.authors?.firstOrNull().orEmpty(),
                    color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
