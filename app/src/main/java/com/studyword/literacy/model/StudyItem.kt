package com.studyword.literacy.model

/**
 * Phase 2:统一的"学习卡片"抽象。
 *
 * 把 [LearningCharacter] / [EnglishLetter] / [EnglishWord] 包装成同一形态,
 * 让主页/游戏/字库 UI 只需面向 [StudyItem] 编程,不再关心是中文还是英文。
 *
 * 设计原则:
 * - [primaryText]   卡片正中显示的大字(中文=汉字 / 英文letter=大写 / 英文word=单词)
 * - [secondaryText] 拼音或音标(对应卡片中的小字)
 * - [tertiaryText]  仅英文 letter 模式需要(小写字母),其他两种模式为空串
 * - [category]      难度/类别标签(简单/中等/困难/字母/单词)
 * - [words]         词组 chip 列表(仅中文有内容;英文 letter 把示例词当 chip)
 * - [examples]      例句列表(中:汉字例句 / 英 word:英文例句;英文 letter 无例句)
 * - [exampleTranslation] 仅英文 word 模式有值(中文释义),显示在例句下方
 *
 * TTS 朗读:
 * - [ttsText]   朗读时的实际文本(中文 = primaryText;英文 = primaryText)
 * - [ttsLocale] 朗读使用的 Locale 分支("cn" / "en"),由 MainActivity 路由
 */
sealed interface StudyItem {
    val id: Int
    val primaryText: String
    val secondaryText: String
    val tertiaryText: String
    val category: String
    val words: List<WordEntry>
    val examples: List<ExampleSentence>
    val exampleTranslation: String
    val ttsLocale: String  // "cn" 或 "en"
}

/** 中文汉字 */
data class ChineseStudyItem(val character: LearningCharacter) : StudyItem {
    override val id: Int get() = character.id
    override val primaryText: String get() = character.hanzi
    override val secondaryText: String get() = character.pinyin
    override val tertiaryText: String get() = ""
    override val category: String get() = character.difficulty.label
    override val words: List<WordEntry> get() = character.words
    override val examples: List<ExampleSentence> get() = character.examples
    override val exampleTranslation: String get() = ""
    override val ttsLocale: String get() = "cn"
}

/** 英文字母 A-Z */
data class EnglishLetterItem(val letter: EnglishLetter) : StudyItem {
    override val id: Int get() = letter.id
    override val primaryText: String get() = letter.uppercase
    override val secondaryText: String get() = letter.phonetic
    override val tertiaryText: String get() = letter.lowercase
    override val category: String get() = "字母"
    override val words: List<WordEntry>
        get() = if (letter.exampleWord.isNotBlank())
            listOf(WordEntry(word = letter.exampleWord, pinyin = ""))
        else emptyList()
    override val examples: List<ExampleSentence> get() = emptyList()
    override val exampleTranslation: String get() = ""
    override val ttsLocale: String get() = "en"
}

/** 英文单词 */
data class EnglishWordItem(val word: EnglishWord) : StudyItem {
    override val id: Int get() = word.id
    override val primaryText: String get() = word.word
    override val secondaryText: String get() = word.phonetic
    override val tertiaryText: String get() = ""
    override val category: String get() = "单词"
    override val words: List<WordEntry> get() = emptyList()
    override val examples: List<ExampleSentence>
        get() = if (word.exampleSentence.isNotBlank())
            listOf(ExampleSentence(sentence = word.exampleSentence, pinyin = ""))
        else emptyList()
    override val exampleTranslation: String get() = word.chineseMeaning
    override val ttsLocale: String get() = "en"
}
