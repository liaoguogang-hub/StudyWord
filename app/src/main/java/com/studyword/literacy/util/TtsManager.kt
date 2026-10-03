package com.studyword.literacy.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 全应用共用的 TTS 朗读管理器。
 *
 * 设计要点:
 * - 单例(进程级),避免每个 Activity 单独初始化引擎
 * - 默认中文(SIMPLIFIED_CHINESE),3-5 岁慢速(0.7x)
 * - 内部把 Locale 缺失视为"不支持",调用方在 ready=false 时自行降级
 *
 * 用法:
 * ```
 * TtsManager.init(applicationContext)
 * TtsManager.speak("天 tiān")
 * // onDestroy:
 * TtsManager.shutdown()
 * ```
 */
object TtsManager {

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    var isReady: Boolean = false
        private set

    /** 默认慢速:适合 3-5 岁孩子;范围 0.5(很慢)~1.0(正常) */
    private const val DEFAULT_SPEECH_RATE = 0.7f

    fun init(context: Context) {
        if (tts != null) return
        synchronized(this) {
            if (tts != null) return
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                              result != TextToSpeech.LANG_NOT_SUPPORTED
                    if (isReady) {
                        tts?.setSpeechRate(DEFAULT_SPEECH_RATE)
                    }
                } else {
                    isReady = false
                }
            }
        }
    }

    /**
     * 朗读一段文字。引擎未就绪时静默失败(返回 false)。
     * 多次调用会打断前一次朗读(QUEUE_FLUSH),不会卡队列。
     */
    fun speak(text: String, utteranceId: String? = null): Boolean {
        val engine = tts ?: return false
        if (!isReady || text.isBlank()) return false
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId).let {
            it == TextToSpeech.SUCCESS
        }
    }

    /** 主动调整语速(默认 0.7f) */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.3f, 2.0f))
    }

    /** 释放资源,通常在 Activity onDestroy 调用一次即可(全局单例不需要每次 shutdown) */
    fun shutdown() {
        synchronized(this) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
        }
    }
}
