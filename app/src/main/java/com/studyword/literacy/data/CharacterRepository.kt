package com.studyword.literacy.data

import android.content.Context
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.LearningCharacter
import com.studyword.literacy.util.PinyinConverter
import org.json.JSONObject
import java.io.IOException
import java.util.LinkedHashSet

class CharacterRepository(context: Context) {

    private val allCharacters: List<LearningCharacter>
    private val grouped: Map<Difficulty, List<LearningCharacter>>

    init {
        ensureData(context.applicationContext)
        allCharacters = cachedAll ?: emptyList()
        grouped = cachedGrouped ?: emptyMap()
    }

    fun all(): List<LearningCharacter> = allCharacters

    fun count(): Int = allCharacters.size

    fun byDifficulty(difficulty: Difficulty): List<LearningCharacter> =
        grouped[difficulty] ?: emptyList()

    companion object {
        private const val ASSET_FILE = "character_sets.json"
        @Volatile private var cachedAll: List<LearningCharacter>? = null
        @Volatile private var cachedGrouped: Map<Difficulty, List<LearningCharacter>>? = null

        private fun ensureData(context: Context) {
            if (cachedAll != null && cachedGrouped != null) return
            synchronized(this) {
                if (cachedAll != null && cachedGrouped != null) return
                val (all, grouped) = loadCharacters(context)
                cachedAll = all
                cachedGrouped = grouped
            }
        }

        private fun loadCharacters(context: Context): Pair<List<LearningCharacter>, Map<Difficulty, List<LearningCharacter>>> {
            val jsonText = try {
                context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (io: IOException) {
                throw IllegalStateException("无法读取字库配置文件：$ASSET_FILE", io)
            }
            val root = JSONObject(jsonText)
            val items = mutableListOf<LearningCharacter>()
            val grouped = mutableMapOf<Difficulty, MutableList<LearningCharacter>>()
            val seen = LinkedHashSet<String>()
            var nextId = 0

            val order = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
            for (difficulty in order) {
                val key = difficulty.name.lowercase()
                val array = root.optJSONArray(key)
                    ?: throw IllegalStateException("字库配置缺少键：$key")
                val list = mutableListOf<LearningCharacter>()
                for (i in 0 until array.length()) {
                    val raw = array.optString(i).trim()
                    if (raw.isEmpty()) continue
                    val hanzi = raw.substring(0, 1)
                    if (seen.add(hanzi)) {
                        val pinyin = PinyinConverter.toPinyin(hanzi)
                        val entry = LearningCharacter(
                            id = nextId++,
                            hanzi = hanzi,
                            pinyin = pinyin,
                            difficulty = difficulty
                        )
                        items += entry
                        list += entry
                    }
                }
                grouped[difficulty] = list
            }
            return items to grouped
        }
    }
}
