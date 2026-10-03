package com.studyword.literacy.game

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.studyword.literacy.R
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.databinding.ActivityGameBinding
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.LearningCharacter
import com.studyword.literacy.util.TtsManager
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 游戏模式主页面(识字闯关)
 *
 * 入口:主页 "🎮 玩游戏" 按钮
 * 模式:LISTEN(听音找字)/ PINYIN(看拼音选字)
 * 流程:5 题一轮,单选 4 选 1,星星评级
 */
class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private lateinit var repository: CharacterRepository

    private var mode: GameMode = GameMode.LISTEN
    private var difficulty: Difficulty = Difficulty.EASY
    private var round: GameRound? = null

    private val random = Random(System.currentTimeMillis())
    private val optionButtons: List<MaterialButton> by lazy {
        listOf(binding.optionA, binding.optionB, binding.optionC, binding.optionD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        TtsManager.init(this)

        // 解析入口参数
        mode = GameMode.fromName(intent.getStringExtra(EXTRA_MODE))
        difficulty = intent.getStringExtra(EXTRA_DIFFICULTY)
            ?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
            ?: Difficulty.EASY

        setupModeChips()
        setupDifficultyChips()
        setupActions()
        startNewRound()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    // ============================================================
    // 顶部控件初始化
    // ============================================================

    private fun setupModeChips() {
        binding.modeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            mode = if (id == binding.chipListen.id) GameMode.LISTEN else GameMode.PINYIN
            startNewRound()
        }
        // 根据入口 mode 设置初始选中
        when (mode) {
            GameMode.LISTEN -> binding.chipListen.isChecked = true
            GameMode.PINYIN -> binding.chipPinyin.isChecked = true
        }
    }

    private fun setupDifficultyChips() {
        binding.difficultyChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            difficulty = when (id) {
                binding.chipEasy.id -> Difficulty.EASY
                binding.chipMedium.id -> Difficulty.MEDIUM
                binding.chipHard.id -> Difficulty.HARD
                else -> Difficulty.EASY
            }
            startNewRound()
        }
        when (difficulty) {
            Difficulty.EASY -> binding.chipEasy.isChecked = true
            Difficulty.MEDIUM -> binding.chipMedium.isChecked = true
            Difficulty.HARD -> binding.chipHard.isChecked = true
        }
    }

    private fun setupActions() {
        binding.backButton.setOnClickListener { finish() }
        binding.skipButton.setOnClickListener { skipCurrent() }
        binding.speakPromptButton.setOnClickListener { speakCurrentPrompt() }
        binding.replayButton.setOnClickListener { startNewRound() }
        binding.exitButton.setOnClickListener { finish() }
        optionButtons.forEach { btn ->
            btn.setOnClickListener { onOptionTapped(btn) }
        }
    }

    // ============================================================
    // 出题与渲染
    // ============================================================

    private fun startNewRound() {
        val pool = repository.byDifficulty(difficulty)
        if (pool.size < 4) {
            Snackbar.make(binding.root, R.string.game_no_chars, Snackbar.LENGTH_SHORT).show()
            return
        }

        val questions = buildQuestions(pool, count = QUESTIONS_PER_ROUND)
        round = GameRound(mode, difficulty, questions)

        binding.resultCard.isVisible = false
        binding.questionCard.isVisible = true
        binding.optionsGrid.isVisible = true
        binding.skipButton.isVisible = true
        binding.modeChipGroup.isVisible = true
        binding.difficultyChipGroup.isVisible = true

        showCurrentQuestion()
    }

    private fun buildQuestions(
        pool: List<LearningCharacter>,
        count: Int
    ): List<GameQuestion> {
        // 同题去重:确保每轮 5 题的 correct 不同
        val shuffled = pool.shuffled(random)
        val picked = shuffled.take(count)
        return picked.map { correct ->
            val distractors = pool.filter { it.id != correct.id }
                .shuffled(random)
                .take(3)
            val options = (listOf(correct) + distractors).shuffled(random)
            GameQuestion(correct, options)
        }
    }

    private fun showCurrentQuestion() {
        val r = round ?: return
        if (r.isFinished) {
            showResult()
            return
        }
        val q = r.currentQuestion ?: run { showResult(); return }

        // 进度文本
        binding.progressText.text = getString(
            R.string.game_progress_format,
            r.currentIndex + 1,
            r.totalQuestions
        )

        // 题目区
        when (mode) {
            GameMode.LISTEN -> {
                binding.speakPromptButton.isVisible = true
                binding.speakPromptHint.isVisible = true
                binding.pinyinPrompt.isVisible = false
                speakCurrentPrompt()  // 自动读一次
            }
            GameMode.PINYIN -> {
                binding.speakPromptButton.isVisible = false
                binding.speakPromptHint.isVisible = false
                binding.pinyinPrompt.isVisible = true
                binding.pinyinPrompt.text = q.correct.pinyin.ifBlank { "?" }
            }
        }

        // 选项区
        optionButtons.forEachIndexed { index, btn ->
            val opt = q.options.getOrNull(index)
            if (opt == null) {
                btn.isVisible = false
            } else {
                btn.isVisible = true
                btn.text = opt.hanzi
                btn.background = ContextCompat.getDrawable(this, R.drawable.bg_option_default)
                btn.isEnabled = true
            }
        }
    }

    private fun speakCurrentPrompt() {
        val q = round?.currentQuestion ?: return
        val text = if (q.correct.pinyin.isNotBlank()) "${q.correct.hanzi}   ${q.correct.pinyin}"
                   else q.correct.hanzi
        TtsManager.speak(text, utteranceId = "game_q_${q.correct.id}")
    }

    // ============================================================
    // 判分
    // ============================================================

    private fun onOptionTapped(btn: MaterialButton) {
        val r = round ?: return
        val q = r.currentQuestion ?: return
        val idx = optionButtons.indexOf(btn)
        val tapped = q.options.getOrNull(idx) ?: return

        // 锁定所有按钮(防多点)
        optionButtons.forEach { it.isEnabled = false }

        val isCorrect = tapped.id == q.correct.id
        r.recordAnswer(isCorrect)

        if (isCorrect) {
            btn.background = ContextCompat.getDrawable(this, R.drawable.bg_option_correct)
            showMiniConfetti()
        } else {
            btn.background = ContextCompat.getDrawable(this, R.drawable.bg_option_wrong)
            // 高亮正确答案 1.5 秒
            val correctBtn = optionButtons.firstOrNull {
                q.options.getOrNull(optionButtons.indexOf(it))?.id == q.correct.id
            }
            correctBtn?.background = ContextCompat.getDrawable(this, R.drawable.bg_option_correct)
            // 答错时主动念一遍正确答案
            TtsManager.speak(q.correct.hanzi, utteranceId = "game_wrong_${q.correct.id}")
        }

        // 800ms 后进入下一题
        binding.root.postDelayed({
            r.advance()
            if (r.isFinished) {
                showResult()
            } else {
                showCurrentQuestion()
            }
        }, 800L)
    }

    private fun skipCurrent() {
        val r = round ?: return
        r.recordAnswer(false)
        r.advance()
        if (r.isFinished) showResult() else showCurrentQuestion()
    }

    // ============================================================
    // 收尾
    // ============================================================

    private fun showResult() {
        val r = round ?: return
        binding.resultCard.isVisible = true
        binding.questionCard.isVisible = false
        binding.optionsGrid.isVisible = false
        binding.skipButton.isVisible = false

        val stars = r.stars()
        binding.resultStars.text = "⭐".repeat(stars).ifEmpty { "💧" }
        binding.resultTitle.text = getString(
            when (stars) {
                3 -> R.string.game_stars_3
                2 -> R.string.game_stars_2
                1 -> R.string.game_stars_1
                else -> R.string.game_stars_0
            }
        )
        binding.resultEmoji.text = getString(
            when (stars) {
                3 -> R.string.game_result_emoji_3
                2 -> R.string.game_result_emoji_2
                1 -> R.string.game_result_emoji_1
                else -> R.string.game_result_emoji_0
            }
        )
        binding.resultScore.text = getString(
            R.string.game_score_format,
            r.correctCount,
            r.totalQuestions
        )

        if (stars >= 2) {
            showBigConfetti()
        }
    }

    // ============================================================
    // 撒花动画(简化版,独立实现避免与 MainActivity 强耦合)
    // ============================================================

    private fun showMiniConfetti() {
        val overlay = binding.confettiOverlay
        val width = overlay.width
        val height = overlay.height
        if (width == 0 || height == 0) {
            overlay.post { showMiniConfetti() }
            return
        }
        val emojis = listOf("✅", "✨", "🌟")
        repeat(6) {
            val tv = TextView(this).apply {
                text = emojis.random()
                textSize = Random.nextInt(20, 32).toFloat()
                alpha = 0f
            }
            overlay.addView(
                tv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            val centerX = width / 2f
            val centerY = height / 2f
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val velocity = Random.nextDouble(0.2, 0.4) * height
            animateParticle(tv, centerX, centerY, angle.toFloat(), velocity.toFloat(), duration = 600L)
        }
    }

    private fun showBigConfetti() {
        val overlay = binding.confettiOverlay
        val width = overlay.width
        val height = overlay.height
        if (width == 0 || height == 0) {
            overlay.post { showBigConfetti() }
            return
        }
        overlay.removeAllViews()
        val emojis = listOf("🎉", "✨", "🎈", "🎊", "🌟", "💫")
        repeat(18) { i ->
            val tv = TextView(this).apply {
                text = emojis[i % emojis.size]
                textSize = Random.nextInt(20, 36).toFloat()
                alpha = 0f
            }
            overlay.addView(
                tv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            val centerX = width / 2f
            val centerY = height / 3f
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val velocity = Random.nextDouble(0.35, 0.7) * height
            animateParticle(
                tv, centerX, centerY, angle.toFloat(), velocity.toFloat(),
                duration = Random.nextLong(900L, 1400L)
            )
        }
    }

    private fun animateParticle(
        tv: TextView,
        centerX: Float,
        centerY: Float,
        angle: Float,
        velocity: Float,
        duration: Long
    ) {
        tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val halfW = tv.measuredWidth / 2f
        val halfH = tv.measuredHeight / 2f
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val f = va.animatedValue as Float
                val dist = velocity * f
                tv.translationX = (centerX + dist * cos(angle.toDouble()).toFloat()) - halfW
                tv.translationY = (centerY + dist * sin(angle.toDouble()).toFloat()) - halfH
                tv.alpha = when {
                    f < 0.2f -> f / 0.2f
                    f > 0.8f -> (1f - f) / 0.2f
                    else -> 1f
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    tv.parent?.let { (it as FrameLayout).removeView(tv) }
                }
            })
            start()
        }
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_DIFFICULTY = "extra_difficulty"
        private const val QUESTIONS_PER_ROUND = 5
    }
}
