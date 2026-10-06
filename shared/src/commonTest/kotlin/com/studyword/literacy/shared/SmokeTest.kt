package com.studyword.literacy.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * v1.7.0 (Phase 0) 占位测试 —— 验证 commonTest 在 JVM 上能跑,Phase 1 起会平移现有
 * [app/src/test] 里的 ~1,200 行 JVM 单元测试到 commonTest,这样 iOS/Android 共享
 * 同一组断言。
 */
class SmokeTest {
    @Test
    fun `phase 0 placeholder passes`() {
        assertEquals(2, 1 + 1)
    }
}
