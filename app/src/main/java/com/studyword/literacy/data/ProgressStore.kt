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

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "literacy_progress"
        private const val KEY_KNOWN = "known_ids"
        private const val KEY_UNKNOWN = "unknown_ids"
    }
}
