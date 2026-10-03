package com.studyword.literacy.util

import android.icu.text.Transliterator
import java.util.Locale

/**
 * 汉字 → 拼音。
 *
 * 基于 ICU 的 Han-Latin/Names transliterator,但该规则集对少数常用字返回错误的占位符
 * (如 "了" → "p"),需要手动覆盖。
 *
 * v1.4.0 新增 overrides 表:覆盖 ICU 输出错误或期望不同的字。
 *
 * 用法:从 [LearningCharacter] 构建时调用一次,把结果缓存到字段。
 */
object PinyinConverter {

    /**
     * ICU 输出有误的字 → 期望读音的覆盖表。
     *
     * 选用读音规则:
     * - 默认走 (常规单独读音),与卡片显示一致
     * - 例:"乐"卡片显示 "lè",但单词 "音乐" 内读 "yuè";Phase 3 内卡片展示保持 "lè"
     *   (英文学单词时由 speakPhraseCharByChar 接管,不依赖拼音字段)
     */
    private val overrides: Map<String, String> = mapOf(
        "了" to "le",
        "地" to "dì",
        "么" to "me",
        "还" to "hái",
        "便" to "pián",
        "长" to "cháng",
        "行" to "xíng",
        "得" to "dé",
        "种" to "zhǒng",
        "只" to "zhǐ",
        "觉" to "jué",
        "空" to "kōng",
    )

    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Han-Latin/Names")
    }

    /**
     * 输入一个汉字,返回其拼音字符串(默认读音)。
     *
     * 行为:
     * - 输入为空 → ""
     * - 命中 overrides → 直接返回(不走 ICU)
     * - 多字输入 → 只取第一个字的读音(主用场景:卡片展示一个字)
     */
    fun toPinyin(hanzi: String): String {
        if (hanzi.isEmpty()) return ""
        val first = hanzi.first().toString()
        overrides[first]?.let { return it }
        val raw = transliterator.transliterate(first)
        if (raw.isBlank()) return ""
        val cleaned = raw
            .lowercase(Locale.CHINA)
            .replace("[^\\p{L}\\s]".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
        return cleaned.split(" ").firstOrNull { it.isNotBlank() } ?: ""
    }

    /**
     * 输入一段汉字,返回逐字拼音数组(逗号分隔)。
     *
     * 用法:把 "日" + "子" 拆成 ["rì", "zi"] → "rì zi"。
     * 实现:每个字先查 overrides 表(避免 ICU 的 "了" → "p" 错误),其余走 ICU。
     */
    fun toPinyinSequence(hanzi: String): List<String> {
        if (hanzi.isEmpty()) return emptyList()
        return hanzi.map { c ->
            val ch = c.toString()
            overrides[ch] ?: run {
                val raw = transliterator.transliterate(ch)
                if (raw.isBlank()) ""
                else raw.lowercase(Locale.CHINA)
                    .replace("[^\\p{L}\\s]".toRegex(), " ")
                    .trim()
                    .split(" ")
                    .firstOrNull { it.isNotBlank() } ?: ""
            }
        }.filter { it.isNotBlank() }
    }
}