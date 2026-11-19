package com.zhaopw.standup

/** Resolve deferred replacements without depending on an Activity or media connection. */
internal fun replacementTarget(uri: String?, replacements: Map<String, String>): String? {
    var current = uri
    val visited = mutableSetOf<String>()
    while (current != null && visited.add(current)) {
        val next = replacements[current] ?: break
        if (next == current) break
        current = next
    }
    return current
}
internal data class PlaylistSyncPlan(val index: Int, val position: Long, val playWhenReady: Boolean)
internal fun planPlaylistSync(
    uris: List<String>, selected: String?, position: Long, playWhenReady: Boolean,
    replacements: Map<String, String>,
): PlaylistSyncPlan {
    val index = uris.indexOf(replacementTarget(selected, replacements))
    return PlaylistSyncPlan(if (uris.isEmpty()) -1 else index.coerceAtLeast(0),
        if (index < 0 || replacements.containsKey(selected)) 0 else position, playWhenReady && index >= 0)
}

/** Collapse ancestors so choosing an earlier file again cannot form A → B → A. */
internal fun withReplacement(replacements: Map<String, String>, oldUri: String, newUri: String): Map<String, String> {
    val next = replacements.mapValues { (source, target) ->
        if (replacementTarget(source, replacements) == oldUri) newUri else target
    }.toMutableMap()
    next[oldUri] = newUri
    if (next.containsKey(newUri)) next[newUri] = newUri
    return next
}
