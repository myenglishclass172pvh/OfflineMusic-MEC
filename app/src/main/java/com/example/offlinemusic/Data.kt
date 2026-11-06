package com.example.offlinemusic

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folder: String,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artUri: Uri get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(artUri).build()
        ).build()
}

/** 6 dải EQ, giá trị -12..+12 dB. custom=false => NORMAL MODE (không áp dụng EQ). */
data class EqState(val gains: List<Float> = List(6) { 0f }, val custom: Boolean = false)

data class BandInfo(val name: String, val range: String)

val BANDS = listOf(
    BandInfo("SUB-BASS", "20–50 Hz"),
    BandInfo("BASS", "50–250 Hz"),
    BandInfo("LOW-MID", "200–500 Hz"),
    BandInfo("MID-RANGE", "500 Hz–4 kHz"),
    BandInfo("PRESENCE", "4–6 kHz"),
    BandInfo("TREBLE", "6–20 kHz"),
)

object MusicLibrary {
    fun load(ctx: Context): List<Song> {
        val list = ArrayList<Song>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
        )
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iT = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iAr = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iD = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iP = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val artist = c.getString(iAr)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                val album = c.getString(iAl)?.takeIf { it.isNotBlank() } ?: "Unknown album"
                val path = c.getString(iP) ?: ""
                val folder = File(path).parentFile?.name?.takeIf { it.isNotBlank() } ?: "Unknown folder"
                list += Song(
                    c.getLong(iId), c.getString(iT) ?: "Unknown", artist, album,
                    c.getLong(iAlId), c.getLong(iD), folder
                )
            }
        }
        return list
    }
}

private val Context.ds: DataStore<Preferences> by preferencesDataStore("settings")

class SavedQueue(val ids: List<Long>, val index: Int, val positionMs: Long)

class SettingsStore(private val ctx: Context) {
    private val kGains = stringPreferencesKey("eq_gains")
    private val kCustom = booleanPreferencesKey("eq_custom")
    private val kFavs = stringPreferencesKey("favorites")
    private val kPlaylists = stringPreferencesKey("playlists")
    private val kQueue = stringPreferencesKey("queue")
    private val kIndex = intPreferencesKey("queue_index")
    private val kPos = longPreferencesKey("queue_pos")

    val eq: Flow<EqState> = ctx.ds.data.map { p ->
        val g = p[kGains]?.split(",")?.mapNotNull { it.toFloatOrNull() }
        EqState(
            if (g != null && g.size == 6) g else List(6) { 0f },
            p[kCustom] ?: false
        )
    }

    suspend fun saveEq(s: EqState) {
        ctx.ds.edit { it[kGains] = s.gains.joinToString(","); it[kCustom] = s.custom }
    }

    val favorites: Flow<Set<Long>> = ctx.ds.data.map { p ->
        p[kFavs]?.split(",")?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    suspend fun saveFavorites(s: Set<Long>) { ctx.ds.edit { it[kFavs] = s.joinToString(",") } }

    val playlists: Flow<Map<String, List<Long>>> = ctx.ds.data.map { p ->
        val out = LinkedHashMap<String, List<Long>>()
        try {
            val o = JSONObject(p[kPlaylists] ?: "{}")
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                out[k] = List(a.length()) { a.getLong(it) }
            }
        } catch (_: Exception) { }
        out
    }

    suspend fun savePlaylists(m: Map<String, List<Long>>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, JSONArray(v))
        ctx.ds.edit { it[kPlaylists] = o.toString() }
    }

    suspend fun saveQueue(ids: String, index: Int, pos: Long) {
        ctx.ds.edit { it[kQueue] = ids; it[kIndex] = index; it[kPos] = pos }
    }

    suspend fun readQueue(): SavedQueue {
        val p = ctx.ds.data.first()
        return SavedQueue(
            p[kQueue]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList(),
            p[kIndex] ?: 0, p[kPos] ?: 0L
        )
    }
}
