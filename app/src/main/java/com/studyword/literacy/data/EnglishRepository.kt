package com.studyword.literacy.data

import android.content.Context
import com.studyword.literacy.model.EnglishCategory
import com.studyword.literacy.model.EnglishLetter
import com.studyword.literacy.model.EnglishWord
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 英文字母与单词的数据仓库。
 *
 * 设计要点:
 * - 与 [CharacterRepository] 同款的进程级单例(双检锁 + @Volatile)
 * - 数据来源:assets/english_sets.json,内含 letters(26)+ words(30)两个数组
 * - 同时维护字母与单词两个 [List],并按 [EnglishCategory] 分组,便于 UI / 题池直接过滤
 *
 * 用法:
 * ```
 * val repo = EnglishRepository(applicationContext)
 * repo.letters()        // List<EnglishLetter>
 * repo.words()          // List<EnglishWord>
 * repo.byCategory(EnglishCategory.WORDS)
 * ```
 */
class EnglishRepository(context: Context) {

    private val allLetters: List<EnglishLetter>
    private val allWords: List<EnglishWord>
    private val grouped: Map<EnglishCategory, List<Any>>

    init {
        ensureData(context.applicationContext)
        allLetters = cachedLetters ?: emptyList()
        allWords = cachedWords ?: emptyList()
        grouped = mapOf(
            EnglishCategory.LETTERS to (cachedLetters ?: emptyList<EnglishLetter>()),
            EnglishCategory.WORDS to (cachedWords ?: emptyList<EnglishWord>())
        )
    }

    /** 返回所有英文字母(A-Z,26 个,按字母表顺序) */
    fun letters(): List<EnglishLetter> = allLetters

    /** 返回所有英文单词(目前 30 个高频词) */
    fun words(): List<EnglishWord> = allWords

    /** 返回字母与单词的合并视图(字母在前,单词在后) */
    fun all(): List<Any> = allLetters + allWords

    /** 按 [EnglishCategory] 过滤;c=LETTERS 返回 [EnglishLetter],c=WORDS 返回 [EnglishWord](类型擦除为 Any) */
    fun byCategory(category: EnglishCategory): List<Any> = grouped[category] ?: emptyList()

    /** 字母数量(应为 26) */
    fun letterCount(): Int = allLetters.size

    /** 单词数量(目前 30) */
    fun wordCount(): Int = allWords.size

    /** 字母 + 单词总数 */
    fun count(): Int = allLetters.size + allWords.size

    /**
     * 按大写字母查找 [EnglishLetter](O(1) 哈希查询)。
     *
     * - 命中:[EnglishLetter]
     * - 未命中或输入不合法:null
     */
    fun findByLetter(letter: String): EnglishLetter? {
        if (letter.isBlank()) return null
        val upper = letter.trim().uppercase()
        return letterIndex[upper]
    }

    /**
     * 按小写单词查找 [EnglishWord](O(1) 哈希查询,大小写不敏感)。
     *
     * - 命中:[EnglishWord]
     * - 未命中或输入不合法:null
     */
    fun findByWord(word: String): EnglishWord? {
        if (word.isBlank()) return null
        val key = word.trim().lowercase()
        return wordIndex[key]
    }

    companion object {
        private const val ASSET_FILE = "english_sets.json"

        @Volatile private var cachedLetters: List<EnglishLetter>? = null
        @Volatile private var cachedWords: List<EnglishWord>? = null
        @Volatile private var letterIndex: Map<String, EnglishLetter> = emptyMap()
        @Volatile private var wordIndex: Map<String, EnglishWord> = emptyMap()

        private fun ensureData(context: Context) {
            if (cachedLetters != null && cachedWords != null) return
            synchronized(this) {
                if (cachedLetters != null && cachedWords != null) return
                val (letters, words) = loadAll(context)
                cachedLetters = letters
                cachedWords = words
                letterIndex = letters.associateBy { it.uppercase }
                wordIndex = words.associateBy { it.word.lowercase() }
            }
        }

        /**
         * 从 assets 一次性读入 letters + words。
         *
         * JSON 结构(根对象):
         * ```
         * {
         *   "letters": [ { "letter":"A", "uppercase":"A", "lowercase":"a", "phonetic":"/eɪ/", "exampleWord":"Apple" } ],
         *   "words":   [ { "word":"cat", "phonetic":"/kæt/", "chineseMeaning":"猫", "exampleSentence":"I love my cat." } ]
         * }
         * ```
         *
         * 解析容错:
         * - 任意一项缺关键字段(letter/word)则跳过,不抛异常
         * - 顶层缺少 letters 或 words 键时,对应列表视为空(便于后续扩展,例如只加 LETTERS)
         */
        private fun loadAll(context: Context): Pair<List<EnglishLetter>, List<EnglishWord>> {
            val jsonText = try {
                context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (io: IOException) {
                throw IllegalStateException("无法读取英文配置文件：$ASSET_FILE", io)
            }
            val root = JSONObject(jsonText)
            val letters = parseLetters(root.optJSONArray("letters"))
            val words = parseWords(root.optJSONArray("words"))
            return letters to words
        }

        private fun parseLetters(array: JSONArray?): List<EnglishLetter> {
            if (array == null || array.length() == 0) return emptyList()
            val result = mutableListOf<EnglishLetter>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val letter = obj.optString("letter", "").trim()
                if (letter.isEmpty()) continue
                val upper = obj.optString("uppercase", letter).trim().ifEmpty { letter.uppercase() }
                val lower = obj.optString("lowercase", letter.lowercase()).trim().ifEmpty { letter.lowercase() }
                val phonetic = obj.optString("phonetic", "").trim()
                val example = obj.optString("exampleWord", "").trim()
                result += EnglishLetter(
                    id = result.size,
                    letter = upper,
                    uppercase = upper,
                    lowercase = lower,
                    phonetic = phonetic,
                    exampleWord = example
                )
            }
            return result
        }

        private fun parseWords(array: JSONArray?): List<EnglishWord> {
            if (array == null || array.length() == 0) return emptyList()
            val result = mutableListOf<EnglishWord>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val word = obj.optString("word", "").trim()
                if (word.isEmpty()) continue
                val phonetic = obj.optString("phonetic", "").trim()
                val meaning = obj.optString("chineseMeaning", "").trim()
                val sentence = obj.optString("exampleSentence", "").trim()
                result += EnglishWord(
                    id = result.size,
                    word = word,
                    phonetic = phonetic,
                    chineseMeaning = meaning,
                    exampleSentence = sentence
                )
            }
            return result
        }
    }
}
