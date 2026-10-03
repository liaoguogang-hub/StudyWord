package com.studyword.literacy.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class ProgressStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========================= 中文(汉字)维度 =========================

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

    // ========================= 英文(字母+单词)维度 =========================
    // 共用一个 Int id 空间(EnglishRepository 中字母 0~25 + 单词 0~29 通过 letterCount()/wordCount() 区分),
    // 但持久化用独立 key,与中文进度互不干扰。

    fun loadEnglishKnown(): MutableSet<Int> = prefs.getStringSet(KEY_KNOWN_EN, emptySet())
        ?.mapNotNull { it.toIntOrNull() }
        ?.toMutableSet()
        ?: mutableSetOf()

    fun loadEnglishUnknown(): MutableSet<Int> = prefs.getStringSet(KEY_UNKNOWN_EN, emptySet())
        ?.mapNotNull { it.toIntOrNull() }
        ?.toMutableSet()
        ?: mutableSetOf()

    fun saveEnglish(known: Set<Int>, unknown: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_KNOWN_EN, known.map { it.toString() }.toSet())
            .putStringSet(KEY_UNKNOWN_EN, unknown.map { it.toString() }.toSet())
            .apply()
    }

    /**
     * 把某个英文 id 标记为"认识":
     * - 同步从 unknown 集合移除(以 known 优先)
     * - 写回 known / unknown 两个集合
     */
    fun markEnglishKnown(id: Int) {
        val known = loadEnglishKnown()
        val unknown = loadEnglishUnknown()
        if (known.add(id)) {
            unknown.remove(id)
            saveEnglish(known, unknown)
        }
    }

    /**
     * 把某个英文 id 标记为"不认识":
     * - 同步从 known 集合移除(以 unknown 优先)
     * - 写回 known / unknown 两个集合
     */
    fun markEnglishUnknown(id: Int) {
        val known = loadEnglishKnown()
        val unknown = loadEnglishUnknown()
        if (unknown.add(id)) {
            known.remove(id)
            saveEnglish(known, unknown)
        }
    }

    /** 返回已知或学过的英文 id 集合(known + unknown,等价于已接触过的项) */
    fun studiedEnglishIds(): Set<Int> = loadEnglishKnown() + loadEnglishUnknown()

    // ========================= 通用重置 =========================

    /**
     * 重置全部进度,包括中文 known/unknown/history 与英文 known/unknown。
     * 历史曲线同步清空,与 v1.2.1 的 reset() 行为保持一致。
     */
    fun reset() {
        prefs.edit().clear().apply()
    }

    /**
     * 仅重置英文进度,中文 known/unknown/history 保留。
     * 用于"重置英文学习"按钮(若后续 Settings 页接入)。
     */
    fun resetEnglish() {
        prefs.edit()
            .remove(KEY_KNOWN_EN)
            .remove(KEY_UNKNOWN_EN)
            .apply()
    }

    // ========================= 历史曲线(中文维度,保持原样) =========================

    fun recordSnapshot(knownCount: Int, unknownCount: Int) {
        val history = loadHistory().toMutableList()
        val last = history.lastOrNull()
        Log.d(TAG, "recordSnapshot before -> size=${history.size}, lastKnown=${last?.knownCount}, lastUnknown=${last?.unknownCount}")
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
        trimmed.lastOrNull()?.let {
            Log.d(TAG, "recordSnapshot after -> size=${trimmed.size}, latestKnown=${it.knownCount}, latestUnknown=${it.unknownCount}, ts=${it.timestamp}")
        }
        Log.d(TAG, "recordSnapshot after -> size=${trimmed.size}, latestKnown=${trimmed.last().knownCount}, latestUnknown=${trimmed.last().unknownCount}")
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

    companion object {
        private const val TAG = "ProgressStore"
        private const val PREFS_NAME = "literacy_progress"
        // 中文维度 key
        private const val KEY_KNOWN = "known_ids"
        private const val KEY_UNKNOWN = "unknown_ids"
        private const val KEY_HISTORY = "history_entries"
        // 英文维度 key(独立,与中文不冲突)
        private const val KEY_KNOWN_EN = "known_english_ids"
        private const val KEY_UNKNOWN_EN = "unknown_english_ids"
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
