package com.studyword.literacy.game

/**
 * 游戏模式。
 *
 * - LISTEN:听音找字 —— TTS 朗读汉字,孩子从 4 个汉字里选出听到的字
 * - PINYIN:看拼音选字 —— 屏幕显示拼音,孩子从 4 个汉字里选出对应的字
 */
enum class GameMode(val label: String, val description: String) {
    LISTEN("听音找字", "听声音,选出对应的汉字"),
    PINYIN("看拼音选字", "看拼音,选出对应的汉字");

    companion object {
        fun fromName(name: String?): GameMode =
            values().firstOrNull { it.name == name } ?: LISTEN
    }
}
