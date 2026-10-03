package com.studyword.literacy.game

import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.LearningCharacter

/**
 * 单个题目(4 选 1)
 */
data class GameQuestion(
    val correct: LearningCharacter,
    val options: List<LearningCharacter>  // 长度 = 4,包含 correct
) {
    /** 选项是否包含某个 id */
    fun contains(id: Int): Boolean = options.any { it.id == id }
}

/**
 * 一轮游戏(5 题)。
 *
 * 设计:不持有 ProgressStore 数据,游戏只是"练习",
 * 不会改变 known/unknown,孩子需要在主页做真正掌握判定。
 */
class GameRound(
    val mode: GameMode,
    val difficulty: Difficulty,
    private val questions: List<GameQuestion>
) {
    val totalQuestions: Int get() = questions.size
    var currentIndex: Int = 0
        private set
    var correctCount: Int = 0
        private set
    /** 每题结果(true=答对),长度 = currentIndex */
    val results: MutableList<Boolean> = mutableListOf()

    val currentQuestion: GameQuestion? get() = questions.getOrNull(currentIndex)

    val isFinished: Boolean get() = currentIndex >= questions.size

    fun recordAnswer(isCorrect: Boolean) {
        results.add(isCorrect)
        if (isCorrect) correctCount++
    }

    fun advance() {
        currentIndex++
    }

    /**
     * 根据正确数算星级。
     *  - 5/5 → 3 星
     *  - 4/5 → 2 星
     *  - 3/5 → 1 星
     *  - ≤2  → 0 星(显示"再试一次")
     */
    fun stars(): Int = when (correctCount) {
        totalQuestions -> 3
        totalQuestions - 1 -> 2
        totalQuestions - 2 -> 1
        else -> 0
    }
}
