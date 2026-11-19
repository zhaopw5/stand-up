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
    fun tracks(): List<Track> = buildList {
        add(builtIn)
        val array = runCatching { JSONArray(prefs.getString("tracks", "[]")) }.getOrDefault(JSONArray())
        for (index in 0 until array.length()) runCatching {
            val item = array.getJSONObject(index)
            add(Track(item.getString("uri"), item.getString("title")))
        }
    }.distinctBy { it.uri }
    private fun save(tracks: List<Track>, replacements: Map<String, String> = pendingReplacements()) {
        val array = JSONArray()
        tracks.filter { it.uri != builtIn.uri }.forEach { array.put(JSONObject().put("uri", it.uri).put("title", it.title)) }
        prefs.edit().putString("tracks", array.toString()).putBoolean("sessionSyncPending", true)
            .putString("pendingReplacements", JSONObject(replacements).toString()).commit()
    }
    fun import(uri: Uri) {
        val track = readableTrack(uri)
        val existing = tracks()
        save(if (existing.any { it.uri == track.uri }) existing.map { if (it.uri == track.uri) track else it } else existing + track)
    }
    fun replace(oldUri: String, uri: Uri) {
        val existing = tracks()
        require(existing.any { it.uri == oldUri && removable(it) })
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
        if (track.uri == builtIn.uri) return
        save(tracks().filter { it.uri != track.uri }); error(track.uri, null)
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(track.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    fun removable(track: Track) = track.uri != builtIn.uri
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
    fun savePosition(uri: String, position: Long) { prefs.edit().putString("selected", uri).putLong("position", position).apply() }
    fun selected() = replacementTarget(prefs.getString("selected", builtIn.uri), pendingReplacements())?.let { if (it.startsWith("android.resource://")) builtIn.uri else it }
    fun position() = if (pendingReplacements().containsKey(prefs.getString("selected", builtIn.uri))) 0L else prefs.getLong("position", 0)
    fun error(message: String?) = error(selected(), message)
    fun error() = trackError(selected())
    fun error(uri: String?, message: String?) { if (uri != null) prefs.edit().putString("error:$uri", message).apply() }
    fun trackError(uri: String?) = if (uri == null) null else prefs.getString("error:$uri", null)
}
