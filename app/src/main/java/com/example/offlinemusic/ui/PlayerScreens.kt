package com.example.offlinemusic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.offlinemusic.BANDS
import com.example.offlinemusic.EqState
import com.example.offlinemusic.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun NowPlayingScreen(vm: MainViewModel) {
    val ui by vm.player.collectAsState()
    val songs by vm.songs.collectAsState()
    val song = songs.find { it.id.toString() == ui.mediaId }

    var pos by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragVal by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ui.mediaId, ui.isPlaying) {
        while (true) { if (!dragging) pos = vm.position(); delay(400) }
    }
    val maxVol = vm.maxVolume()
    var vol by remember { mutableFloatStateOf(vm.volume().toFloat()) }

    if (ui.mediaId == null) {
        Column(
            Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Nothing playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.play(songs, 0) }, enabled = songs.isNotEmpty()) { Text("Play all songs") }
        }
        return
    }

    val dur = max(ui.durationMs, 1L)
    val shown = if (dragging) dragVal.toLong() else pos
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtImage(song, Modifier.fillMaxWidth(0.8f).aspectRatio(1f), 20)
        Spacer(Modifier.height(20.dp))
        Text(
            ui.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(ui.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Slider(
            value = shown.toFloat().coerceIn(0f, dur.toFloat()),
            onValueChange = { dragging = true; dragVal = it },
            onValueChangeFinished = { vm.seek(dragVal.toLong()); pos = dragVal.toLong(); dragging = false },
            valueRange = 0f..dur.toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(shown), style = MaterialTheme.typography.bodySmall)
            Text("-" + fmt(dur - shown), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, "Shuffle", tint = if (ui.shuffle) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.prev() }, Modifier.size(52.dp)) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp)) }
            FilledIconButton(
                onClick = { vm.togglePlay() }, Modifier.size(68.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = Color.Black),
            ) { Icon(if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", Modifier.size(40.dp)) }
            IconButton(onClick = { vm.next() }, Modifier.size(52.dp)) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp)) }
            IconButton(onClick = { vm.cycleRepeat() }) {
                val on = ui.repeat != Player.REPEAT_MODE_OFF
                Icon(
                    if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat", tint = if (on) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeUp, "Volume", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = vol, onValueChange = { vol = it; vm.setVolume(it.roundToInt()) },
                valueRange = 0f..maxVol.toFloat(), steps = (maxVol - 1).coerceAtLeast(0),
            )
        }
        Text(
            when (ui.repeat) {
                Player.REPEAT_MODE_ONE -> "Repeat: One"
                Player.REPEAT_MODE_ALL -> "Repeat: All"
                else -> "Repeat: Off"
            },
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Slider dọc: xoay Slider ngang 270°. */
@Composable
private fun VerticalSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Slider(
        value = value, onValueChange = onChange, valueRange = -12f..12f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
        modifier = modifier
            .graphicsLayer { rotationZ = 270f; transformOrigin = TransformOrigin(0f, 0f) }
            .layout { measurable, c ->
                val p = measurable.measure(
                    Constraints(
                        minWidth = c.minHeight, maxWidth = c.maxHeight,
                        minHeight = c.minWidth, maxHeight = c.maxWidth,
                    )
                )
                layout(p.height, p.width) { p.place(-p.width, 0) }
            },
    )
}

private fun dbLabel(v: Float) = if (abs(v) < 0.05f) "0 dB" else "%+.1f dB".format(v)

@Composable
private fun EqPreview(eq: EqState) {
    val line = Accent
    val grid = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)) {
        val w = size.width; val h = size.height
        fun y(db: Float) = h / 2f - (db / 12f) * (h / 2f - 12f)
        for (d in listOf(-12f, -6f, 0f, 6f, 12f)) drawLine(grid, Offset(0f, y(d)), Offset(w, y(d)), if (d == 0f) 3f else 1f)
        val xs = List(6) { w * (it + 0.5f) / 6f }
        val path = Path()
        eq.gains.forEachIndexed { i, g ->
            if (i == 0) path.moveTo(xs[i], y(g)) else {
                val mx = (xs[i - 1] + xs[i]) / 2f
                path.cubicTo(mx, y(eq.gains[i - 1]), mx, y(g), xs[i], y(g))
            }
        }
        drawPath(path, line, style = Stroke(width = 5f, cap = StrokeCap.Round))
        eq.gains.forEachIndexed { i, g -> drawCircle(line, 8f, Offset(xs[i], y(g))) }
    }
}

@Composable
fun EqualizerScreen(vm: MainViewModel) {
    val eq by vm.eq.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("EQUALIZER", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("My Offline Music Player · OFFLINE", style = MaterialTheme.typography.labelMedium, color = Accent)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(CircleShape).background(if (eq.custom) MaterialTheme.colorScheme.secondary else Accent)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    if (eq.custom) "CUSTOM MODE" else "NORMAL MODE",
                    color = if (eq.custom) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            BANDS.forEachIndexed { i, b ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(b.name, fontSize = 9.sp, maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(dbLabel(eq.gains[i]), fontSize = 11.sp, color = Accent, maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(4.dp))
                    VerticalSlider(eq.gains[i], { vm.setBand(i, it) }, Modifier.height(230.dp).width(48.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        b.range, fontSize = 9.sp, maxLines = 2, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("EQUALIZER PREVIEW", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        EqPreview(eq)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { vm.normalMode() }, Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            ) { Text("NORMAL MODE", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = { vm.resetBands() }, Modifier.weight(1f)) { Text("RESET") }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (eq.custom) "Custom sound · 6-band EQ applied (-12…+12 dB)" else "Original sound · No EQ enhancement",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
    }
}

@UnstableApi
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val songs by vm.songs.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card { Column(Modifier.padding(16.dp)) {
            Text("Library", fontWeight = FontWeight.SemiBold)
            Text("${songs.size} songs found on this device", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.loadSongs() }) { Text("Rescan library") }
        } }
        Card { Column(Modifier.padding(16.dp)) {
            Text("Audio", fontWeight = FontWeight.SemiBold)
            Text("Reset All: đưa Equalizer về NORMAL MODE, 6 dải 0 dB.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.resetAll() }) { Text("Reset All") }
        } }
        Card { Column(Modifier.padding(16.dp)) {
            Text("About", fontWeight = FontWeight.SemiBold)
            Text(
                "My Offline Music Player v1.0\n• 100% offline, không có quyền Internet\n• Không quảng cáo, không đăng nhập, không thu thập dữ liệu\n" +
                    "• Equalizer 6 dải tần (DSP biquad) xử lý trực tiếp trong trình phát",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } }
    }
}
