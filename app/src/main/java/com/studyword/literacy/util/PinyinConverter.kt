package com.studyword.literacy.util

import android.icu.text.Transliterator
import java.util.Locale

object PinyinConverter {
    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Han-Latin/Names; Latin-ASCII")
    }

    fun toPinyin(hanzi: String): String {
        if (hanzi.isEmpty()) return ""
        val raw = transliterator.transliterate(hanzi)
        if (raw.isBlank()) return ""
        val cleaned = raw
            .lowercase(Locale.CHINA)
            .replace("[^a-z\\s]".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
        return cleaned.split(" ").firstOrNull { it.isNotBlank() } ?: ""
    }
}
