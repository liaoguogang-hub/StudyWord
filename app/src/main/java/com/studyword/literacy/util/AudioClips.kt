package com.studyword.literacy.util

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.util.Log

/**
 * 内置语音包播放器。
 *
 * 为什么需要它
 * ------------
 * 部分设备(实测 HarmonyOS 的安卓兼容层)不提供 TTS 引擎:
 * `TextToSpeech.getEngines()` 返回空,系统"文本转语音"设置页也卡在"正在检查…"。
 * 鸿蒙自己的语音是 ArkTS API,安卓 APK 调不到 —— 这是平台边界,代码绕不过去。
 *
 * 但实测那台设备 **MediaPlayer 播放音频文件是正常的**(答题音效有声),
 * 所以把语音预生成成 Opus 文件打进 assets,就能在**任何设备**上都有发音。
 *
 * 与 `scripts/generate_audio.py` 的约定(改动必须两边同步)
 * -----------------------------------------------------
 *   c:<汉字>   中文单字        w:<中文>   中文词组
 *   e:<中文>   中文例句        z:<中文>   其它中文(意思/例词中文/翻译)
 *   l:<字母>   英文字母        n:<英文>   英文单词或例词
 *   s:<英文>   英文例句
 *
 * 文件名 = `k{CRC32(key):08x}.ogg`,生成脚本会断言无哈希碰撞。
 *
 * 依赖:build.gradle 里必须对 ogg 关闭压缩,否则 `openFd` 拿不到可 seek 的 FD
 * (MediaPlayer 无法直接播放被压缩进 APK 的音频)。
 */
object AudioClips {

    private const val TAG = "AudioClips"

    /** 池大小:一次只播一条为主,留几个余量给"点得很快"的情况 */
    private const val POOL_SIZE = 3

    private var appContext: Context? = null

    /** 已确认**不存在**的 key,避免每次都去 assets 里探一遍(IO 不便宜) */
    private val missing = HashSet<String>()

    private val pool = ArrayDeque<MediaPlayer>()
    private var poolIndex = 0

    /** 当前正在播放的实例,用于 stop() */
    private var current: MediaPlayer? = null

    private var enabled = true

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 临时关掉内置音频(调试时对比 TTS 用) */
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) stop()
    }

    /**
     * key 对应的 asset 路径。
     * 规则见 [AudioClipNaming] —— 抽出去是为了有**跨语言契约测试**锁定,
     * 避免与 Python 生成脚本悄悄分叉(症状是"生成了却播不出声"且无报错)。
     */
    private fun assetPath(key: String): String = AudioClipNaming.assetPath(key)

    /** 是否存在该条音频(结果会被缓存) */
    fun has(key: String): Boolean {
        if (!enabled) return false
        val ctx = appContext ?: return false
        if (missing.contains(key)) return false
        val path = assetPath(key)
        return try {
            ctx.assets.openFd(path).use { true }
        } catch (e: Exception) {
            missing.add(key)
            false
        }
    }

    /**
     * 播放一条音频。
     * @return true = 找到并开始播放;false = 没有这条音频,调用方应回退到 TTS
     */
    fun play(key: String): Boolean {
        if (!enabled) return false
        val ctx = appContext ?: return false
        if (missing.contains(key)) return false
        val path = assetPath(key)
        val fd: AssetFileDescriptor = try {
            ctx.assets.openFd(path)
        } catch (e: Exception) {
            missing.add(key)
            return false
        }
        return try {
            stopCurrent()
            val mp = obtain()
            mp.reset()
            fd.use {
                mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            mp.setOnCompletionListener { /* 播完就停着,下次复用 */ }
            mp.prepare()
            mp.start()
            current = mp
            true
        } catch (e: Exception) {
            Log.w(TAG, "播放失败 key=$key path=$path: ${e.javaClass.simpleName} ${e.message}")
            // 播放失败也算不可用,避免反复尝试
            missing.add(key)
            releaseCurrent()
            false
        }
    }

    /**
     * 依次播放多条(如英文单词卡的"单词 → 中文意思")。
     * 任一条缺失就用 [onMissing] 回调交给调用方回退 TTS;全部存在则顺序播完。
     *
     * @return true = 已接管播放;false = 有缺失,调用方应自行处理(例如走 TTS)
     */
    fun playSequence(keys: List<String>): Boolean {
        if (!enabled) return false
        val ctx = appContext ?: return false
        if (keys.isEmpty()) return false
        // 先整体检查,避免播了一半才发现缺
        val paths = keys.map { key ->
            if (missing.contains(key)) return false
            val p = assetPath(key)
            try {
                ctx.assets.openFd(p).use { }
                p
            } catch (e: Exception) {
                missing.add(key)
                return false
            }
        }
        return try {
            stopCurrent()
            playAt(ctx, paths, 0)
            true
        } catch (e: Exception) {
            Log.w(TAG, "顺序播放失败: ${e.javaClass.simpleName} ${e.message}")
            releaseCurrent()
            false
        }
    }

    private fun playAt(ctx: Context, paths: List<String>, index: Int) {
        if (index >= paths.size) {
            releaseCurrent()
            return
        }
        val mp = obtain()
        mp.reset()
        ctx.assets.openFd(paths[index]).use {
            mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
        }
        mp.setOnCompletionListener {
            current = null
            release(mp)
            playAt(ctx, paths, index + 1)
        }
        mp.prepare()
        mp.start()
        current = mp
    }

    /** 停止当前播放(切卡片/离开页面时调用) */
    fun stop() {
        stopCurrent()
    }

    fun release() {
        stopCurrent()
        pool.forEach { runCatching { it.release() } }
        pool.clear()
    }

    // ============================================================
    // 内部
    // ============================================================

    private fun stopCurrent() {
        current?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.reset() }
            release(mp)
        }
        current = null
    }

    /** 从池里取一个实例(轮换),池满则复用最旧的那个 */
    private fun obtain(): MediaPlayer {
        if (pool.isNotEmpty()) {
            val mp = pool.removeFirst()
            pool.addLast(mp)
            return mp
        }
        return MediaPlayer()
    }

    private fun release(mp: MediaPlayer) {
        runCatching { mp.reset() }
        if (pool.size < POOL_SIZE && !pool.contains(mp)) {
            pool.addLast(mp)
        } else {
            runCatching { mp.release() }
        }
    }

    private fun releaseCurrent() {
        current = null
    }
}
