package com.studyword.literacy.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * v1.7.0 (Phase 0) 占位 composable —— 验证 Compose Multiplatform 在 iOS 上能渲染。
 *
 * Phase 2 起会被 [com.studyword.literacy.ui.MainScreen] 等真正的屏幕替换掉。
 */
@Composable
fun StudyWordSplash(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFFE4B5)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 28.sp, color = Color(0xFF663300))
    }
}
