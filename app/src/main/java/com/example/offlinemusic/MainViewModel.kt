package com.example.offlinemusic

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.offlinemusic.audio.EqEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

data class PlayerUi(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
)

@UnstableApi
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()
    private val _eq = MutableStateFlow(EqState())
    val eq = _eq.asStateFlow()
    private val _player = MutableStateFlow(PlayerUi())
    val player = _player.asStateFlow()

    val favorites = store.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val playlists = store.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var controller: MediaController? = null
    private var restored = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    init {
        viewModelScope.launch {
            val s = store.eq.first()
            _eq.value = s
            EqEngine.apply(s)
        }
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
            sync()
            tryRestore()
        }, ContextCompat.getMainExecutor(app))
    }

    // ---------- Thư viện ----------
    fun loadSongs() {
        viewModelScope.launch {
            _songs.value = withContext(Dispatchers.IO) { MusicLibrary.load(getApplication()) }
            tryRestore()
        }
    }

    private fun tryRestore() {
        val c = controller ?: return
        if (restored || _songs.value.isEmpty()) return
        restored = true
        viewModelScope.launch {
            val q = store.readQueue()
            if (c.mediaItemCount > 0 || q.ids.isEmpty()) return@launch
            val byId = _songs.value.associateBy { it.id }
            val items = q.ids.mapNotNull { byId[it] }
            if (items.isEmpty()) return@launch
            val target = q.ids.getOrNull(q.index)
            val idx = items.indexOfFirst { it.id == target }.coerceAtLeast(0)
            c.setMediaItems(items.map { it.toMediaItem() }, idx, q.positionMs)
            c.prepare() // không tự phát
        }
    }

    // ---------- Phát nhạc ----------
    private fun sync() {
        val c = controller ?: return
        val md = c.currentMediaItem?.mediaMetadata
        _player.value = PlayerUi(
            mediaId = c.currentMediaItem?.mediaId,
            title = md?.title?.toString() ?: "",
            artist = md?.artist?.toString() ?: "",
            isPlaying = c.isPlaying,
            durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
        )
    }

    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        c.prepare(); c.play()
    }

    fun shuffleAll(list: List<Song>) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = true
        play(list, Random.nextInt(list.size))
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPreviousMediaItem() }
    fun seek(ms: Long) { controller?.seekTo(ms) }
    fun position(): Long = controller?.currentPosition ?: 0L
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ---------- Âm lượng (hệ thống) ----------
    fun maxVolume() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    fun volume() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    fun setVolume(v: Int) = audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)

    // ---------- Equalizer ----------
    private fun applyEq(s: EqState) {
        _eq.value = s
        EqEngine.apply(s)
        viewModelScope.launch { store.saveEq(s) }
    }

    /** Kéo thanh trượt => tự chuyển sang CUSTOM MODE. */
    fun setBand(i: Int, dB: Float) {
        val v = ((dB.coerceIn(-12f, 12f)) * 2).roundToInt() / 2f
        val g = _eq.value.gains.toMutableList().also { it[i] = v }
        applyEq(EqState(g, true))
    }

    /** NORMAL MODE: 6 dải về 0 dB + bypass hoàn toàn EQ. */
    fun normalMode() = applyEq(EqState(List(6) { 0f }, false))

    /** RESET: đưa 6 dải về 0 dB (giữ nguyên chế độ hiện tại). */
    fun resetBands() = applyEq(EqState(List(6) { 0f }, _eq.value.custom))

    fun resetAll() = normalMode()

    // ---------- Yêu thích & Playlist ----------
    fun toggleFavorite(id: Long) {
        val s = favorites.value.toMutableSet()
        if (!s.add(id)) s.remove(id)
        viewModelScope.launch { store.saveFavorites(s) }
    }

    private fun savePl(m: Map<String, List<Long>>) = viewModelScope.launch { store.savePlaylists(m) }

    fun createPlaylist(name: String) {
        val n = name.trim()
        if (n.isEmpty() || playlists.value.containsKey(n)) return
        savePl(LinkedHashMap(playlists.value).also { it[n] = emptyList() })
    }

    fun renamePlaylist(old: String, new: String) {
        val n = new.trim()
        if (n.isEmpty() || (n != old && playlists.value.containsKey(n))) return
        val m = LinkedHashMap<String, List<Long>>()
        for ((k, v) in playlists.value) m[if (k == old) n else k] = v
        savePl(m)
    }

    fun deletePlaylist(name: String) = savePl(LinkedHashMap(playlists.value).also { it.remove(name) })

    fun addToPlaylist(name: String, id: Long) {
        val cur = playlists.value[name] ?: return
        if (id in cur) return
        savePl(LinkedHashMap(playlists.value).also { it[name] = cur + id })
    }

    fun removeFromPlaylist(name: String, id: Long) {
        val cur = playlists.value[name] ?: return
        savePl(LinkedHashMap(playlists.value).also { it[name] = cur - id })
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
