package com.studyword.literacy.model

/**
 * 英文学习内容的类型标签。
 *
 * 区别于中文的 [Difficulty](EASY/MEDIUM/HARD)三级,英文维度只有两类:
 * - [LETTERS] 英文字母 A-Z(26 个),作为书写与发音启蒙
 * - [WORDS]  常见高频词(如 cat/dog/apple),作为初步词汇积累
 *
 * 之所以不复用 [Difficulty],是因为英文"难度"在卡片粒度上更适合用 LETTERS / WORDS 来组织
 * 进度与题池,而非具体字符的出现频率。
 */
enum class EnglishCategory(val label: String) {
    LETTERS("字母"),
    WORDS("单词");
}

/**
 * 一个英文字母(同时含大小写形态)。
 *
 * 字段:
 * - [id] 内部序号,与 ProgressStore 中的 known/unknown 集合对应
 * - [letter] 大写字母形式(A-Z),作为卡片主显
 * - [uppercase] 大写字母串(冗余字段,便于 UI 直接取用)
 * - [lowercase] 小写字母串
 * - [phonetic] 字母音标(IPA 简化),如 /æ/ /biː/
 * - [exampleWord] 以该字母为首的示例单词(如 A -> "Apple")
 */
data class EnglishLetter(
    val id: Int,
    val letter: String,
    val uppercase: String,
    val lowercase: String,
    val phonetic: String,
    val exampleWord: String,
    val category: EnglishCategory = EnglishCategory.LETTERS
)

/**
 * 一个英文单词。
 *
 * 字段:
 * - [id] 内部序号,与 ProgressStore 中的 known/unknown 集合对应
 * - [word] 单词字符串(如 "cat")
 * - [phonetic] 单词音标(IPA 简化),如 /kæt/
 * - [chineseMeaning] 单词的中文释义,如 "猫"
 * - [exampleSentence] 单词的英文例句
 * - [category] 始终为 [EnglishCategory.WORDS](用于查询时统一过滤)
 */
data class EnglishWord(
    val id: Int,
    val word: String,
    val phonetic: String,
    val chineseMeaning: String,
    val exampleSentence: String,
    val category: EnglishCategory = EnglishCategory.WORDS
)
