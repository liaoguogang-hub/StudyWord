package com.studyword.literacy

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.studyword.literacy.databinding.ActivitySplashBinding
import com.studyword.literacy.ui.MainActivity

/**
 * v1.4.0:开机启动页
 *
 * 5 张 splash_*.xml 自动翻页,每张停留 600ms,共 3 秒后启动 MainActivity。
 * splash_*.xml 当前是纯色占位 drawable,用户将提供真实卡通图片(PNG/JPG)替换即可,
 * 无需改代码。
 *
 * 翻页效果:200ms 淡出 + 200ms 淡入,5 张图依次切换。
 * 显示 emoji + 标题的 overlay 让占位图也有视觉信息,真实图片上线后此 overlay
 * 可保留(动物 emoji 与图片呼应)或由设计决定是否删除。
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val splashDrawables = intArrayOf(
        R.drawable.splash_1,
        R.drawable.splash_2,
        R.drawable.splash_3,
        R.drawable.splash_4,
        R.drawable.splash_5
    )
    private val splashEmojis = arrayOf("🦁", "🐰", "🐻", "🐼", "🦊")

    private val handler = Handler(Looper.getMainLooper())
    private var index = 0

    private val flipRunnable = object : Runnable {
        override fun run() {
            index++
            if (index >= splashDrawables.size) {
                goToMain()
                return
            }
            showSplash(index)
            handler.postDelayed(this, SPLASH_DURATION_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 第一张直接显示,不做淡入
        showSplash(0, animate = false)
        // 600ms 后翻到下一张,循环直至 5 张翻完启动主页
        handler.postDelayed(flipRunnable, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun showSplash(position: Int, animate: Boolean = true) {
        binding.splashImage.setImageResource(splashDrawables[position])
        binding.splashEmoji.text = splashEmojis[position]
        if (animate) {
            val fadeOut = ObjectAnimator.ofFloat(binding.splashImage, "alpha", 1f, 0f)
            val fadeIn = ObjectAnimator.ofFloat(binding.splashImage, "alpha", 0f, 1f)
            fadeOut.duration = FADE_DURATION_MS
            fadeIn.duration = FADE_DURATION_MS
            fadeOut.start()
            fadeIn.startDelay = FADE_DURATION_MS
            fadeIn.start()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        /** 单张停留时间(ms) */
        private const val SPLASH_DURATION_MS = 600L
        /** 切换淡入淡出时间(ms) */
        private const val FADE_DURATION_MS = 200L
    }
}
