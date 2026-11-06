package com.example.offlinemusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.example.offlinemusic.MainViewModel
import com.example.offlinemusic.Song

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun ArtImage(song: Song?, modifier: Modifier = Modifier, corner: Int = 10) {
    Box(
        modifier.clip(RoundedCornerShape(corner.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (song != null) {
            AsyncImage(
                model = song.artUri, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onOk(text); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@UnstableApi
@Composable
fun SongList(
    songs: List<Song>,
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onRemove: ((Song) -> Unit)? = null,
) {
    val ui by vm.player.collectAsState()
    val favs by vm.favorites.collectAsState()
    val pls by vm.playlists.collectAsState()
    var pickFor by remember { mutableStateOf<Song?>(null) }

    if (songs.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                var menu by remember { mutableStateOf(false) }
                val current = ui.mediaId == s.id.toString()
                Row(
                    Modifier.fillMaxWidth().clickable { vm.play(songs, i) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtImage(s, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (current) Accent else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${s.artist} · ${fmt(s.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.toggleFavorite(s.id) }) {
                        val f = s.id in favs
                        Icon(
                            if (f) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite",
                            tint = if (f) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Add to playlist") },
                                onClick = { menu = false; pickFor = s },
                            )
                            if (onRemove != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove from playlist") },
                                    onClick = { menu = false; onRemove(s) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pickFor?.let { song ->
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    if (pls.isEmpty()) Text("No playlists yet. Create one in Library › Playlists.")
                    pls.keys.forEach { name ->
                        TextButton(onClick = { vm.addToPlaylist(name, song.id); pickFor = null }) { Text(name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickFor = null }) { Text("Close") } },
        )
    }
}
