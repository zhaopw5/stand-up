package com.zhaopw.standup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

data class Track(val uri: String, val title: String) {
    fun mediaItem(): MediaItem = MediaItem.Builder().setMediaId(uri).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("stand up").build()).build()
}
class MusicLibrary(private val context: Context) {
    private val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)
    private val builtIn = Track("android.resource://${context.packageName}/raw/quiet_steps_loop", "慢步 · 内置音乐")
    fun tracks(): List<Track> {
        val array = runCatching { JSONArray(prefs.getString("tracks", "[]")) }.getOrDefault(JSONArray())
        val stored = buildList {
            for (index in 0 until array.length()) runCatching {
                val item = array.getJSONObject(index)
                val uri = item.getString("uri").let { if (it.startsWith("android.resource://")) builtIn.uri else it }
                add(Track(uri, item.getString("title")))
            }
        }
        val tracks = (if (prefs.getInt("playlistSchema", 0) < 1) listOf(builtIn) + stored else stored).distinctBy { it.uri }
        if (prefs.getInt("playlistSchema", 0) < 1) writeTracks(tracks).commit()
        return tracks
    }
    private fun writeTracks(tracks: List<Track>): android.content.SharedPreferences.Editor {
        val array = JSONArray()
        tracks.forEach { array.put(JSONObject().put("uri", it.uri).put("title", it.title)) }
        return prefs.edit().putString("tracks", array.toString()).putInt("playlistSchema", 1)
    }
    private fun save(tracks: List<Track>, replacements: Map<String, String> = pendingReplacements()) {
        writeTracks(tracks).putBoolean("sessionSyncPending", true)
            .putString("pendingReplacements", JSONObject(replacements).toString()).commit()
    }
    fun hasBuiltIn() = tracks().any { it.uri == builtIn.uri }
    fun restoreBuiltIn() { if (!hasBuiltIn()) save(tracks() + builtIn) }
    fun move(uri: String, offset: Int) {
        val list = tracks().toMutableList(); val from = list.indexOfFirst { it.uri == uri }; val to = from + offset
        if (from < 0 || to !in list.indices || offset !in listOf(-1, 1)) return
        list.add(to, list.removeAt(from)); save(list)
    }
    fun import(uri: Uri) {
        val track = readableTrack(uri)
        val existing = tracks()
        save(if (existing.any { it.uri == track.uri }) existing.map { if (it.uri == track.uri) track else it } else existing + track)
    }
    fun replace(oldUri: String, uri: Uri) {
        val existing = tracks()
        require(existing.any { it.uri == oldUri && reselectable(it) })
        // Validate and persist the new grant before changing the library or old grant.
        val replacement = readableTrack(uri)
        save(existing.map { if (it.uri == oldUri) replacement else it }.distinctBy { it.uri }, withReplacement(pendingReplacements(), oldUri, replacement.uri))
        if (oldUri != replacement.uri) {
            error(oldUri, null)
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(oldUri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }
    private fun readableTrack(uri: Uri): Track {
        val resolver = context.contentResolver
        val hadGrant = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { } ?: throw java.io.IOException("无法读取该音频")
            val title = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "导入的音频"
            return Track(uri.toString(), title)
        } catch (failure: Exception) {
            if (!hadGrant) runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            throw failure
        }
    }
    fun remove(track: Track) {
        val existing = tracks(); val index = existing.indexOfFirst { it.uri == track.uri }
        if (index < 0) return
        val selected = selected(); val remaining = existing.filter { it.uri != track.uri }
        save(remaining); error(track.uri, null)
        if (selected == track.uri) {
            prefs.edit().putString("selected", remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.uri)
                .putLong("position", 0).commit()
        }
        if (reselectable(track) && remaining.none { it.uri == track.uri })
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(track.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    fun reselectable(track: Track) = track.uri != builtIn.uri
    var mode: Int
        get() = prefs.getInt("mode", 2)
        set(value) { prefs.edit().putInt("mode", value).apply() }
    fun pendingReplacements(): Map<String, String> {
        val json = runCatching { JSONObject(prefs.getString("pendingReplacements", "{}")!!) }.getOrDefault(JSONObject())
        return buildMap { val keys = json.keys(); while (keys.hasNext()) { val key = keys.next(); put(key, json.getString(key)) } }
    }
    fun needsSessionSync() = prefs.getBoolean("sessionSyncPending", false)
    fun markSessionSynchronized() {
        prefs.edit().remove("pendingReplacements").putBoolean("sessionSyncPending", false).commit()
    }
    fun savePosition(uri: String, position: Long) { if (tracks().none { it.uri == uri }) return; prefs.edit().putString("selected", uri).putLong("position", position).apply() }
    fun selected(): String? {
        val resolved = replacementTarget(prefs.getString("selected", builtIn.uri), pendingReplacements())
            ?.let { if (it.startsWith("android.resource://")) builtIn.uri else it }
        return tracks().firstOrNull { it.uri == resolved }?.uri ?: tracks().firstOrNull()?.uri
    }
    fun position() = if (pendingReplacements().containsKey(prefs.getString("selected", builtIn.uri))) 0L else prefs.getLong("position", 0)
    fun error(message: String?) = error(selected(), message)
    fun error() = trackError(selected())
    fun error(uri: String?, message: String?) { if (uri != null) prefs.edit().putString("error:$uri", message).apply() }
    fun trackError(uri: String?) = if (uri == null) null else prefs.getString("error:$uri", null)
}
