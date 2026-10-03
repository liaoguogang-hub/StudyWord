package com.studyword.literacy.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
 * v1.4.0 新增:
 * - [speakPhraseCharByChar] 把中文词组/例句逐字朗读,避开多音字问题
 *   (引擎默认读音对 rì/yuè/lè 等最准,直接把拼音字符串塞给引擎反而错)
 * - [speakSequential] 串发多段(英文 + 中文 + 例句),200ms 间隔,用于
 *   英文卡片"点字母 → 听 letter + 示例词 + 中文意思"
 *
 * 用法:
 * ```
 * TtsManager.init(applicationContext)
 * TtsManager.speak("天 tiān")            // 中文
 * TtsManager.speakEnglish("Apple")       // 英文
 * TtsManager.speakPhraseCharByChar("日子","rì zi","main_word_5") // 多音字安全
 * TtsManager.speakSequential(listOf("Apple" to true, "苹果" to false), 200) // 复合
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

    // ========== v1.4.0:char-by-char 队列 ==========
    private val mainHandler = Handler(Looper.getMainLooper())
    private val charQueue: ArrayDeque<CharJob> = ArrayDeque()
    private var charQueueActive = false

    /** char-by-char 队列里的一项 */
    private data class CharJob(val char: String, val isEnglish: Boolean, val baseId: String)

    init {
        // 安装 utterance 监听器(在 init() 实际 engine 创建后会再次注册,但此处先占位)
    }

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
                    // 注册 utterance 监听器驱动 char-by-char 队列
                    tts?.setOnUtteranceProgressListener(buildProgressListener())
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
        return speakInternal(text, isEnglish = false, utteranceId = utteranceId, flush = true)
    }

    /**
     * 朗读一段英文文字(单词/字母/例句均可)。
     */
    fun speakEnglish(text: String, utteranceId: String? = null): Boolean {
        return speakInternal(text, isEnglish = true, utteranceId = utteranceId, flush = true)
    }

    /**
     * 内部核心朗读方法。
     * - [flush]=true:QUEUE_FLUSH,打断当前引擎中的所有朗读(独立朗读某段时使用)
     * - [flush]=false:QUEUE_ADD,追加到引擎当前朗读队列末尾,前一段自然结束才读这一段
     * 复合发音(如 "Apple 苹果")用 speakSequential,首项 flush=true、其余 flush=false,
     * 引擎会按顺序串读且自然带停顿,避免前一段被后一段打断。
     */
    private fun speakInternal(
        text: String,
        isEnglish: Boolean,
        utteranceId: String?,
        flush: Boolean
    ): Boolean {
        val engine = tts ?: return false
        if (text.isBlank()) return false
        if (isEnglish) {
            if (!isEnglishReady) return false
            val switchResult = engine.setLanguage(Locale.US)
            if (switchResult == TextToSpeech.LANG_MISSING_DATA ||
                switchResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                return false
            }
        } else {
            if (!isReady) return false
            engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        }
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val id = utteranceId ?: "tts_${System.nanoTime()}"
        return engine.speak(text, mode, null, id) == TextToSpeech.SUCCESS
    }

    /**
     * v1.4.0:逐字朗读一段中文(用于词组 / 例句)。
     *
     * 解决问题:
     * - 把 `日子 + 空格 + rì zi` 直接塞给华为 xiaoyi 引擎,引擎对"rì zi"按英文处理,中文"日"被误读
     * - 改为按字符拆开,每个字单独 speak(),引擎使用默认读音(对常用字最准)
     * - 汉字之间用 QUEUE_ADD,引擎会按顺序朗读(中间有微小停顿,但不卡)
     *
     * 实现:
     * - 引擎回调 UtteranceProgressListener.onDone 时,从 [charQueue] 取下一个字符
     * - 监听器无法回调时(fallback),用 mainHandler 每 200ms 轮询 engine.isSpeaking
     */
    fun speakPhraseCharByChar(text: String, utteranceId: String) {
        if (!isReady) return
        // 清空旧队列,打断之前任何朗读
        charQueue.clear()
        engineFlush()
        // 把 text 中所有汉字放入队列(过滤掉标点 / 空格 / ASCII 拉丁字母)
        text.forEach { c ->
            if (c.isLetter() && c.code > 127) {
                charQueue.addLast(CharJob(c.toString(), isEnglish = false, baseId = utteranceId))
            }
        }
        if (charQueue.isEmpty()) return
        charQueueActive = true
        // 启动首字;后续字由 UtteranceProgressListener.onDone 接力
        speakNextInQueue()
    }

    /**
     * 按顺序串发多段文字,英文 + 中文混合,默认 500ms 间隔。
     *
     * v1.4.1 修复:之前每段都 QUEUE_FLUSH 导致后一段打断前一段
     * (用户听到中英文重叠 / 前段被截断)。改为"首项 FLUSH + 后续 ADD",
     * 引擎自然等前一段读完再读下一段,delayMs 作为最小间隔。
     *
     * 用法:
     * ```
     * speakSequential(
     *   items = listOf(
     *     "Apple"   to true,   // speakEnglish
     *     "苹果"    to false,  // speak
     *     "I love my cat."  to true,
     *     "我爱我的猫。"  to false
     *   ),
     *   delayMs = 500,
     *   baseUtteranceId = "main_seq_5"
     * )
     * ```
     */
    fun speakSequential(
        items: List<Pair<String, Boolean>>,
        delayMs: Long = 500,
        baseUtteranceId: String = "seq_${System.nanoTime()}"
    ) {
        if (items.isEmpty()) return
        charQueue.clear()
        engineFlush()
        items.forEachIndexed { idx, (text, isEnglish) ->
            mainHandler.postDelayed({
                speakInternal(
                    text = text,
                    isEnglish = isEnglish,
                    utteranceId = "${baseUtteranceId}_${idx}",
                    flush = (idx == 0)
                )
            }, delayMs * idx)
        }
    }

    /** 中断当前所有朗读(char 队列 + sequential 都清空) */
    fun stop() {
        charQueue.clear()
        charQueueActive = false
        engineFlush()
    }

    // ============================================================
    // 内部
    // ============================================================

    private fun engineFlush() {
        tts?.stop()
    }

    /** 从 char 队列中拉下一个字朗读 */
    private fun speakNextInQueue() {
        if (!charQueueActive) return
        val engine = tts ?: run { charQueueActive = false; return }
        if (!isReady) { charQueueActive = false; return }
        val next = charQueue.removeFirstOrNull()
        if (next == null) {
            charQueueActive = false
            return
        }
        // 切语言
        if (next.isEnglish) {
            if (!isEnglishReady) {
                speakNextInQueue()
                return
            }
            engine.setLanguage(Locale.US)
        } else {
            engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        }
        val id = "${next.baseId}_${System.nanoTime()}"
        val result = engine.speak(next.char, TextToSpeech.QUEUE_ADD, null, id)
        if (result != TextToSpeech.SUCCESS) {
            // 引擎失败 → 跳到下一个(避免卡死)
            mainHandler.postDelayed({ speakNextInQueue() }, 200)
        }
    }

    private fun buildProgressListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // 引擎开始朗读某字 — 不需要处理
            }
            override fun onDone(utteranceId: String?) {
                // 引擎读完一字 → 拉下一个
                mainHandler.post { speakNextInQueue() }
            }
            @Deprecated("Required override for older Android versions")
            override fun onError(utteranceId: String?) {
                mainHandler.post { speakNextInQueue() }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { speakNextInQueue() }
            }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                // 引擎被打断(stop/shutdown) → 不再继续
                charQueueActive = false
            }
        }
    }

    /**
     * 朗读完毕后,由 TTS 引擎回调把语言切回中文,避免下一句中文也被读成英文。
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
            charQueue.clear()
            charQueueActive = false
            tts?.setOnUtteranceProgressListener(null)
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
            isEnglishReady = false
        }
    }
}