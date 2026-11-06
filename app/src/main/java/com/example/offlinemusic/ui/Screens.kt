package com.example.offlinemusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.offlinemusic.MainViewModel
import com.example.offlinemusic.Song

@UnstableApi
@Composable
fun MainScaffold(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ui by vm.player.collectAsState()
    val songs by vm.songs.collectAsState()
    val items = listOf(
        "Home" to Icons.Default.Home,
        "Library" to Icons.Default.LibraryMusic,
        "Now Playing" to Icons.Default.PlayCircle,
        "Equalizer" to Icons.Default.Equalizer,
        "Settings" to Icons.Default.Settings,
    )
    Scaffold(
        bottomBar = {
            Column {
                if (ui.mediaId != null && tab != 2) {
                    val song = songs.find { it.id.toString() == ui.mediaId }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable { tab = 2 }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtImage(song, Modifier.size(40.dp), 8)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    ui.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { vm.togglePlay() }) {
                                Icon(if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
                            }
                            IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Next") }
                        }
                    }
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    items.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, label) },
                            label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black, selectedTextColor = Accent,
                                indicatorColor = Accent,
                            ),
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(vm)
                1 -> LibraryScreen(vm)
                2 -> NowPlayingScreen(vm)
                3 -> EqualizerScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}

@UnstableApi
@Composable
fun HomeScreen(vm: MainViewModel) {
    val songs by vm.songs.collectAsState()
    val favs by vm.favorites.collectAsState()
    val favSongs = remember(songs, favs) { songs.filter { it.id in favs } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("My Offline Music Player", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("OFFLINE · NO ADS · ${songs.size} songs", color = Accent, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.play(songs, 0) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play all")
                }
                OutlinedButton(onClick = { vm.shuffleAll(songs) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(4.dp)); Text("Shuffle")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (favSongs.isEmpty()) "Songs" else "Favorites",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        SongList(if (favSongs.isEmpty()) songs else favSongs, vm)
    }
}

@UnstableApi
@Composable
fun LibraryScreen(vm: MainViewModel) {
    val songs by vm.songs.collectAsState()
    val favs by vm.favorites.collectAsState()
    var query by remember { mutableStateOf("") }
    var sub by remember { mutableIntStateOf(0) }
    val titles = listOf("All Songs", "Artists", "Albums", "Folders", "Playlists", "Favorites")
    val filtered = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
        )
        ScrollableTabRow(selectedTabIndex = sub, edgePadding = 8.dp, containerColor = Color.Transparent) {
            titles.forEachIndexed { i, t -> Tab(selected = sub == i, onClick = { sub = i }, text = { Text(t) }) }
        }
        when (sub) {
            0 -> SongList(filtered, vm)
            1 -> GroupTab(remember(filtered) { filtered.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }, vm)
            2 -> GroupTab(remember(filtered) { filtered.groupBy { it.album }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }, vm)
            3 -> GroupTab(remember(filtered) { filtered.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }, vm)
            4 -> PlaylistsTab(vm, songs)
            else -> SongList(filtered.filter { it.id in favs }, vm)
        }
    }
}

@UnstableApi
@Composable
private fun GroupTab(groups: Map<String, List<Song>>, vm: MainViewModel) {
    var open by remember { mutableStateOf<String?>(null) }
    val current = open?.let { groups[it] }
    if (current != null) {
        BackHandler { open = null }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { open = null }) { Icon(Icons.Default.ArrowBack, "Back") }
                Text(open ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SongList(current, vm)
        }
    } else if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Empty") }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups.keys.toList()) { k ->
                ListItem(
                    headlineContent = { Text(k, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${groups[k]?.size ?: 0} songs") },
                    leadingContent = { Icon(Icons.Default.Folder, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { open = k },
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun PlaylistsTab(vm: MainViewModel, all: List<Song>) {
    val pls by vm.playlists.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val byId = remember(all) { all.associateBy { it.id } }
    val ids = open?.let { pls[it] }

    if (open != null && ids != null) {
        val name = open!!
        BackHandler { open = null }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { open = null }) { Icon(Icons.Default.ArrowBack, "Back") }
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
                IconButton(onClick = { vm.deletePlaylist(name); open = null }) { Icon(Icons.Default.Delete, "Delete") }
            }
            SongList(ids.mapNotNull { byId[it] }, vm, onRemove = { vm.removeFromPlaylist(name, it.id) })
        }
        if (renaming) NameDialog("Rename playlist", name, { new ->
            vm.renamePlaylist(name, new); if (new.trim().isNotEmpty()) open = new.trim()
        }, { renaming = false })
    } else {
        Column(Modifier.fillMaxSize()) {
            Button(onClick = { creating = true }, modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("New playlist")
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(pls.keys.toList()) { k ->
                    ListItem(
                        headlineContent = { Text(k) },
                        supportingContent = { Text("${pls[k]?.size ?: 0} songs") },
                        leadingContent = { Icon(Icons.Default.QueueMusic, null) },
                        trailingContent = {
                            IconButton(onClick = { vm.deletePlaylist(k) }) { Icon(Icons.Default.Delete, "Delete") }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { open = k },
                    )
                }
            }
        }
        if (creating) NameDialog("New playlist", "", { vm.createPlaylist(it) }, { creating = false })
    }
}
