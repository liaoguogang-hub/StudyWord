package com.studyword.literacy.data

import android.content.Context
import android.content.SharedPreferences

class ProgressStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadKnown(): MutableSet<Int> = prefs.getStringSet(KEY_KNOWN, emptySet())
        ?.mapNotNull { it.toIntOrNull() }
        ?.toMutableSet()
        ?: mutableSetOf()

    fun loadUnknown(): MutableSet<Int> = prefs.getStringSet(KEY_UNKNOWN, emptySet())
        ?.mapNotNull { it.toIntOrNull() }
        ?.toMutableSet()
        ?: mutableSetOf()

    fun save(known: Set<Int>, unknown: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_KNOWN, known.map { it.toString() }.toSet())
            .putStringSet(KEY_UNKNOWN, unknown.map { it.toString() }.toSet())
            .apply()
    }

    fun recordSnapshot(knownCount: Int, unknownCount: Int) {
        val history = loadHistory().toMutableList()
        val last = history.lastOrNull()
        if (last != null &&
            last.knownCount == knownCount &&
            last.unknownCount == unknownCount
        ) {
            return
        }
        history += ProgressSnapshot(
            timestamp = System.currentTimeMillis(),
            knownCount = knownCount,
            unknownCount = unknownCount
        )
        val trimmed = if (history.size > MAX_HISTORY_SIZE) {
            history.takeLast(MAX_HISTORY_SIZE)
        } else {
            history
        }
        val serialized = trimmed.joinToString(HISTORY_DELIMITER) {
            listOf(it.timestamp, it.knownCount, it.unknownCount).joinToString(ENTRY_DELIMITER)
        }
        prefs.edit().putString(KEY_HISTORY, serialized).apply()
    }

    fun loadHistory(): List<ProgressSnapshot> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(HISTORY_DELIMITER).mapNotNull { entry ->
            val parts = entry.split(ENTRY_DELIMITER)
            if (parts.size != 3) return@mapNotNull null
            val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
            val known = parts[1].toIntOrNull() ?: return@mapNotNull null
            val unknown = parts[2].toIntOrNull() ?: return@mapNotNull null
            ProgressSnapshot(timestamp, known, unknown)
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "literacy_progress"
        private const val KEY_KNOWN = "known_ids"
        private const val KEY_UNKNOWN = "unknown_ids"
        private const val KEY_HISTORY = "history_entries"
        private const val HISTORY_DELIMITER = "|"
        private const val ENTRY_DELIMITER = ","
        private const val MAX_HISTORY_SIZE = 60
    }
}

data class ProgressSnapshot(
    val timestamp: Long,
    val knownCount: Int,
    val unknownCount: Int
)
