package com.studyword.literacy.game

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.util.TypedValue
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
import com.studyword.literacy.data.EnglishRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityGameBinding
import com.studyword.literacy.model.ChineseStudyItem
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.EnglishLetterItem
import com.studyword.literacy.model.EnglishWordItem
import com.studyword.literacy.model.StudyItem
import com.studyword.literacy.model.StudyMode
import com.studyword.literacy.util.TtsManager
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 游戏模式主页面(中英文识字闯关)
 *
 * v1.3.0 起支持英文题型:
 * - LISTEN_LETTER:听字母名,4 个 Aa 字母卡里选
 * - LISTEN_WORD:听单词,4 个英文单词里选
 *
 * 入口:主页 "🎮 玩游戏" 按钮
 * 流程:5 题一轮,单选 4 选 1,星星评级
 */
class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private lateinit var repository: CharacterRepository
    private lateinit var englishRepository: EnglishRepository
    private lateinit var progressStore: ProgressStore

    private var mode: GameMode = GameMode.LISTEN
    private var language: StudyMode = StudyMode.CHINESE
    private var difficulty: Difficulty = Difficulty.EASY
    private var round: GameRound? = null

    private val random = Random(System.currentTimeMillis())
    private val optionButtons: List<MaterialButton> by lazy {
        listOf(binding.optionA, binding.optionB, binding.optionC, binding.optionD)
    }

    /** v1.4.0:答错时短震动反馈(API 31+ 用 VibratorManager) */
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        englishRepository = EnglishRepository(this)
        progressStore = ProgressStore(this)
        TtsManager.init(this)

        // 解析入口参数
        mode = GameMode.fromName(intent.getStringExtra(EXTRA_MODE))
        language = mode.language
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
            mode = when (id) {
                binding.chipListen.id -> GameMode.LISTEN
                binding.chipPinyin.id -> GameMode.PINYIN
                binding.chipListenLetter.id -> GameMode.LISTEN_LETTER
                binding.chipListenWord.id -> GameMode.LISTEN_WORD
                else -> GameMode.LISTEN
            }
            language = mode.language
            startNewRound()
        }
        // 根据入口 mode 设置初始选中,并按语种显隐 chip
        when (mode) {
            GameMode.LISTEN -> binding.chipListen.isChecked = true
            GameMode.PINYIN -> binding.chipPinyin.isChecked = true
            GameMode.LISTEN_LETTER -> binding.chipListenLetter.isChecked = true
            GameMode.LISTEN_WORD -> binding.chipListenWord.isChecked = true
        }
        applyLanguageVisibility()
    }

    private fun setupDifficultyChips() {
        // 英文模式没有难度,隐藏 chip 组
        binding.difficultyChipGroup.isVisible = language == StudyMode.CHINESE
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

    private fun applyLanguageVisibility() {
        val isEnglish = language == StudyMode.ENGLISH
        binding.chipListen.isVisible = !isEnglish
        binding.chipPinyin.isVisible = !isEnglish
        binding.chipListenLetter.isVisible = isEnglish
        binding.chipListenWord.isVisible = isEnglish
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
        val pool = buildStudiedPool()
        if (pool.size < 4) {
            showEmptyState()
            return
        }

        val questions = buildQuestions(pool, count = QUESTIONS_PER_ROUND)
        round = GameRound(mode, difficulty.takeIf { language == StudyMode.CHINESE }, questions)

        binding.resultCard.isVisible = false
        binding.questionCard.isVisible = true
        binding.optionsGrid.isVisible = true
        binding.skipButton.isVisible = true
        binding.modeChipGroup.isVisible = true

        showCurrentQuestion()
    }

    /**
     * 题池 = (当前语言 ∩ 已学过的项)。
     * 中文:byDifficulty ∩ (known + unknown)
     * 英文:letters + words ∩ (known + unknown),按 mode 进一步过滤 letter / word
     */
    private fun buildStudiedPool(): List<StudyItem> {
        val known: Set<Int>
        val unknown: Set<Int>
        return when (language) {
            StudyMode.CHINESE -> {
                known = progressStore.loadKnown()
                unknown = progressStore.loadUnknown()
                val studiedIds = known + unknown
                repository.byDifficulty(difficulty)
                    .filter { it.id in studiedIds }
                    .map { ChineseStudyItem(it) }
            }
            StudyMode.ENGLISH -> {
                known = progressStore.loadEnglishKnown()
                unknown = progressStore.loadEnglishUnknown()
                val studiedIds = known + unknown
                val allItems = mutableListOf<StudyItem>()
                if (mode == GameMode.LISTEN_LETTER) {
                    allItems += englishRepository.letters()
                        .filter { it.id in studiedIds }
                        .map { EnglishLetterItem(it) }
                } else if (mode == GameMode.LISTEN_WORD) {
                    allItems += englishRepository.words()
                        .filter { it.id in studiedIds }
                        .map { EnglishWordItem(it) }
                }
                allItems
            }
        }
    }

    private fun showEmptyState() {
        round = null
        binding.resultCard.isVisible = true
        binding.questionCard.isVisible = false
        binding.optionsGrid.isVisible = false
        binding.skipButton.isVisible = false
        binding.modeChipGroup.isVisible = false
        binding.difficultyChipGroup.isVisible = false

        binding.resultEmoji.text = getString(R.string.game_empty_emoji)
        binding.resultStars.text = ""
        binding.resultTitle.text = getString(R.string.game_empty_title)
        binding.resultScore.text = getString(
            if (language == StudyMode.ENGLISH) R.string.game_empty_message_en
            else R.string.game_empty_message
        )
        binding.replayButton.isVisible = false
    }

    private fun buildQuestions(
        pool: List<StudyItem>,
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

        // 题目区(按 mode 与语种分支)
        when (mode) {
            GameMode.LISTEN -> {
                binding.speakPromptButton.isVisible = true
                binding.speakPromptHint.isVisible = true
                binding.pinyinPrompt.isVisible = false
                binding.speakPromptHint.text = "听一听,再选"
                // v1.4.3:0.7s 延迟后才发音,让用户先看清楚当前界面再听
                binding.root.postDelayed({ speakCurrentPrompt() }, 700L)
            }
            GameMode.PINYIN -> {
                binding.speakPromptButton.isVisible = false
                binding.speakPromptHint.isVisible = false
                binding.pinyinPrompt.isVisible = true
                binding.pinyinPrompt.text = q.correct.secondaryText.ifBlank { "?" }
            }
            GameMode.LISTEN_LETTER -> {
                binding.speakPromptButton.isVisible = true
                binding.speakPromptHint.isVisible = true
                binding.pinyinPrompt.isVisible = false
                binding.speakPromptHint.text = "Listen and pick the letter"
                binding.root.postDelayed({ speakCurrentPrompt() }, 700L)
            }
            GameMode.LISTEN_WORD -> {
                binding.speakPromptButton.isVisible = true
                binding.speakPromptHint.isVisible = true
                binding.pinyinPrompt.isVisible = false
                binding.speakPromptHint.text = "Listen and pick the word"
                binding.root.postDelayed({ speakCurrentPrompt() }, 700L)
            }
        }

        // 选项区(中英文用同一个按钮,只是 text 不同)
        optionButtons.forEachIndexed { index, btn ->
            val opt = q.options.getOrNull(index)
            if (opt == null) {
                btn.isVisible = false
            } else {
                btn.isVisible = true
                btn.text = renderOptionLabel(opt)
                applyOptionAutoSize(btn, opt)
                btn.background = ContextCompat.getDrawable(this, R.drawable.bg_option_default)
                btn.isEnabled = true
            }
        }
    }

    /**
     * v1.4.0:根据 item 类型给选项按钮启用合适的字号策略
     * - 中文:固定 44sp(单字刚好)
     * - 英文 letter:由 renderOptionLabel 的 SpannableString 控制,关闭 auto-size
     * - 英文 word:启用 auto-size 14-44sp,长单词自动缩字
     */
    private fun applyOptionAutoSize(btn: MaterialButton, item: StudyItem) {
        when (item) {
            is EnglishWordItem -> {
                btn.setAutoSizeTextTypeUniformWithConfiguration(
                    14, 44, 1, TypedValue.COMPLEX_UNIT_SP
                )
                btn.maxLines = 1
                btn.ellipsize = null
            }
            else -> {
                btn.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
                btn.maxLines = 1
            }
        }
    }

    /**
     * 渲染选项按钮文字:
     * - 中文:汉字(44sp)
     * - 英文 letter:uppercase 60sp + 间隔 + lowercase 36sp(SpannableString 左右分开)
     * - 英文 word:单词(由 auto-size 处理)
     */
    private fun renderOptionLabel(item: StudyItem): CharSequence = when (item) {
        is ChineseStudyItem -> item.character.hanzi
        is EnglishLetterItem -> buildLetterLabel(item.letter.uppercase, item.letter.lowercase)
        is EnglishWordItem -> item.word.word
    }

    /**
     * 大写 60sp 在左、小写 36sp 在右,中间用 2 个空格拉开距离。
     * gap 太大(4 空格)时,某些宽字母(如 W、M)的 "Aa" 会越出按钮右沿,
     * 所以使用紧凑的 2 空格,确保各种字母宽度都能容纳。
     */
    private fun buildLetterLabel(uppercase: String, lowercase: String): CharSequence {
        val gap = "  "
        val text = "$uppercase$gap$lowercase"
        val spannable = SpannableString(text)
        spannable.setSpan(
            AbsoluteSizeSpan(60, true),
            0,
            1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            AbsoluteSizeSpan(36, true),
            uppercase.length + gap.length,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private fun speakCurrentPrompt() {
        val q = round?.currentQuestion ?: return
        when (q.correct) {
            is ChineseStudyItem -> {
                // v1.4.3:只读汉字,不再 append 拼音(会被 TTS 读成"日 rì")
                TtsManager.speak(q.correct.character.hanzi, utteranceId = "game_q_${q.correct.id}")
            }
            is EnglishLetterItem -> {
                TtsManager.speakEnglish(q.correct.letter.uppercase, utteranceId = "game_q_en_${q.correct.id}")
            }
            is EnglishWordItem -> {
                TtsManager.speakEnglish(q.correct.word.word, utteranceId = "game_q_en_${q.correct.id}")
            }
        }
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
            shakeButton(btn)
            vibrateWrong()
            // 高亮正确答案 1.5 秒
            val correctBtn = optionButtons.firstOrNull {
                q.options.getOrNull(optionButtons.indexOf(it))?.id == q.correct.id
            }
            correctBtn?.background = ContextCompat.getDrawable(this, R.drawable.bg_option_correct)
            // 答错时主动念一遍正确答案
            when (q.correct) {
                is ChineseStudyItem -> TtsManager.speak(q.correct.character.hanzi, utteranceId = "game_wrong_${q.correct.id}")
                is EnglishLetterItem -> TtsManager.speakEnglish(q.correct.letter.uppercase, utteranceId = "game_wrong_en_${q.correct.id}")
                is EnglishWordItem -> TtsManager.speakEnglish(q.correct.word.word, utteranceId = "game_wrong_en_${q.correct.id}")
            }
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
        binding.replayButton.isVisible = true

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

    /**
     * v1.4.0:答错时按钮左右抖动
     */
    private fun shakeButton(btn: View) {
        ObjectAnimator.ofFloat(
            btn, "translationX",
            0f, -24f, 24f, -18f, 18f, -10f, 10f, 0f
        ).apply {
            duration = 380L
            start()
        }
    }

    /**
     * v1.4.0:答错时短震动(80ms,Android 8+ 用 VibrationEffect,旧版本 fallback long)
     */
    private fun vibrateWrong() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(80L)
        }
    }

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
