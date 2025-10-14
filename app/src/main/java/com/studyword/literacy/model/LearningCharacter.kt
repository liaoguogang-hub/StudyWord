package com.studyword.literacy.model

enum class Difficulty(val label: String) {
    EASY("简单"),
    MEDIUM("中等"),
    HARD("困难");
}

data class LearningCharacter(
    val id: Int,
    val hanzi: String,
    val pinyin: String,
    val difficulty: Difficulty
)
