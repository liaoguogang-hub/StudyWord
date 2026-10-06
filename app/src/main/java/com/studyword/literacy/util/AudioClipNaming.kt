package com.studyword.literacy.util

import java.util.zip.CRC32

/**
 * 内置语音包的 key → 文件名规则。
 *
 * ⚠️ **必须与 `scripts/generate_audio.py` 的 `clip_name()` 完全一致** ——
 * 一边是生成、一边是查找,不一致就会"明明生成了却找不到音频"。
 * 因此这里刻意抽成纯函数,并由 [com.studyword.literacy.AudioClipNamingTest]
 * 用 Python 侧算出的固定值锁死,任何一边改动都会立刻失败。
 *
 * 约定:
 * - key 形如 `c:一` / `w:一起` / `e:<句子>` / `z:<中文>` / `l:A` / `n:Apple` / `s:<英文>`
 * - 文件名 = `k` + CRC32(key 的 UTF-8 字节) 的 8 位小写十六进制 + `.ogg`
 */
object AudioClipNaming {

    const val DIR = "audio"
    const val EXT = "mp3"

    private const val HEX = "0123456789abcdef"

    /**
     * CRC32 的 8 位小写十六进制。
     *
     * 不用 `String.format("%08x")` 是因为它走默认 Locale,
     * 在某些区域设置下可能产生非 ASCII 数字 —— 那样文件名就会与 Python 侧对不上。
     * 这里手工拼十六进制,结果与语言环境无关。
     */
    fun crcHex(key: String): String {
        val crc = CRC32().apply { update(key.toByteArray(Charsets.UTF_8)) }.value
        val sb = StringBuilder(8)
        for (shift in intArrayOf(28, 24, 20, 16, 12, 8, 4, 0)) {
            sb.append(HEX[((crc ushr shift) and 0xFL).toInt()])
        }
        return sb.toString()
    }

    /** 文件名,如 `ke8693da9.ogg` */
    fun fileName(key: String): String = "k${crcHex(key)}.$EXT"

    /** assets 下的相对路径,如 `audio/ke8693da9.ogg` */
    fun assetPath(key: String): String = "$DIR/${fileName(key)}"
}
