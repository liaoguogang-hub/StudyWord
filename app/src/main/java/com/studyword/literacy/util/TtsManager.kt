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
 * - 英文朗读通过 [speakEnglish] 走 en-US Locale,与中文共用同一个引擎实例
 * - 内部把 Locale 缺失视为"不支持",调用方在 ready=false 时自行降级
 *
 * 用法:
 * ```
 * TtsManager.init(applicationContext)
 * TtsManager.speak("天 tiān")        // 中文
 * TtsManager.speakEnglish("Apple")   // 英文
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

    /** 英文 Locale 是否可用;init 时同步尝试 setLanguage,后续 speakEnglish 据此决定是否朗读 */
    @Volatile
    var isEnglishReady: Boolean = false
        private set

    /** 默认慢速:适合 3-5 岁孩子;范围 0.5(很慢)~1.0(正常) */
    private const val DEFAULT_SPEECH_RATE = 0.7f

    fun init(context: Context) {
        if (tts != null) return
        synchronized(this) {
            if (tts != null) return
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    // 默认中文 Locale(保持 v1.2.1 行为不变,所有现有调用方不受影响)
                    val cnResult = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    isReady = cnResult != TextToSpeech.LANG_MISSING_DATA &&
                              cnResult != TextToSpeech.LANG_NOT_SUPPORTED
                    if (isReady) {
                        tts?.setSpeechRate(DEFAULT_SPEECH_RATE)
                    }
                    // 同时探测 en-US 是否就绪(不修改当前语言,只检测可用性)
                    val enResult = tts?.isLanguageAvailable(Locale.US)
                    isEnglishReady = enResult == TextToSpeech.LANG_AVAILABLE ||
                                     enResult == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                                     enResult == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                } else {
                    isReady = false
                    isEnglishReady = false
                }
            }
        }
    }

    /**
     * 朗读一段中文文字。引擎未就绪时静默失败(返回 false)。
     * 多次调用会打断前一次朗读(QUEUE_FLUSH),不会卡队列。
     *
     * 实现细节:
     * - 每次调用都显式把引擎切回 SIMPLIFIED_CHINESE,避免 [speakEnglish] 之后
     *   残留 en-US 状态导致中文被按英文规则朗读
     */
    fun speak(text: String, utteranceId: String? = null): Boolean {
        val engine = tts ?: return false
        if (!isReady || text.isBlank()) return false
        engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId).let {
            it == TextToSpeech.SUCCESS
        }
    }

    /**
     * 朗读一段英文文字(单词/字母/例句均可)。
     *
     * 实现细节:
     * - 把引擎切到 en-US 后朗读;setLanguage() 是幂等的,调用开销可忽略
     * - 与 [speak] 互不串扰:下一次中文朗读时 [speak] 会自己再切回 SIMPLIFIED_CHINESE
     * - 引擎未就绪或 en-US 不可用时,静默返回 false(不崩)
     */
    fun speakEnglish(text: String, utteranceId: String? = null): Boolean {
        val engine = tts ?: return false
        if (!isEnglishReady || text.isBlank()) return false
        // 切到 en-US;若设备不支持,这里会返回 LANG_MISSING_DATA/LANG_NOT_SUPPORTED,直接放弃
        val switchResult = engine.setLanguage(Locale.US)
        if (switchResult == TextToSpeech.LANG_MISSING_DATA ||
            switchResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return false
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    /**
     * 朗读完毕后,由 TTS 引擎回调把语言切回中文,避免下一句中文也被读成英文。
     *
     * 注意:此方法需要在 TTS 引擎的 UtteranceProgressListener.onDone 中调用;
     * 当前 v1.3.0 暂未在 Activity 接入该监听,故此函数作为未来扩展的入口保留。
     */
    @Suppress("unused")
    fun resetToChinese() {
        tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
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
            isEnglishReady = false
        }
    }
}
