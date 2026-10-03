package com.studyword.literacy.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.studyword.literacy.R
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityMainBinding
import com.studyword.literacy.game.GameActivity
import com.studyword.literacy.game.GameMode
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.LearningCharacter
import com.studyword.literacy.util.TtsManager
import java.util.ArrayDeque
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CharacterRepository
    private lateinit var progressStore: ProgressStore

    private val knownIds: MutableSet<Int> = mutableSetOf()
    private val unknownIds: MutableSet<Int> = mutableSetOf()
    private var currentDifficulty: Difficulty = Difficulty.EASY
    private val pendingCharacters: ArrayDeque<LearningCharacter> = ArrayDeque()
    private var currentCharacter: LearningCharacter? = null
    private val random = Random(System.currentTimeMillis())
    private var successPlayer: MediaPlayer? = null
    private var encouragePlayer: MediaPlayer? = null
    private val mascotFaces = listOf("🐻", "🦊", "🐼", "🐰", "🦄", "🐨")

    // TTS: 使用全局单例 TtsManager,避免重复初始化引擎
    // 主页只调 init,不负责 shutdown(进程退出时自动释放)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        progressStore = ProgressStore(this)
        reloadProgressFromStore()
        progressStore.recordSnapshot(knownIds.size, unknownIds.size)

        TtsManager.init(this)
        setupDifficultyToggle()
        setupActions()
        rebuildQueue()
        loadNextCharacter()
    }

    override fun onDestroy() {
        successPlayer?.release()
        successPlayer = null
        encouragePlayer?.release()
        encouragePlayer = null
        super.onDestroy()
    }

    /**
     * 朗读当前汉字。读"字 + 拼音",孩子可点击"听一听"按钮重复播放。
     */
    private fun speakCurrentCharacter() {
        val character = currentCharacter ?: return
        val pinyin = character.pinyin.ifBlank { "" }
        val text = if (pinyin.isNotEmpty()) "${character.hanzi}   $pinyin" else character.hanzi
        TtsManager.speak(text, utteranceId = "main_char_${character.id}")
    }

    /**
     * 朗读一段自定义文本(供词组 chip / 例句 TextView 点击调用)。
     * 如果同时给了拼音,TTS 会先读字、再读拼音(用两个空格分隔,TTS 自然停顿)。
     */
    private fun speakWordOrSentence(text: String, pinyin: String, utteranceId: String) {
        val combined = if (pinyin.isNotBlank()) "$text   $pinyin" else text
        TtsManager.speak(combined, utteranceId = utteranceId)
    }

    private fun setupDifficultyToggle() {
        binding.difficultyChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentDifficulty = when (checkedId) {
                binding.chipEasy.id -> Difficulty.EASY
                binding.chipMedium.id -> Difficulty.MEDIUM
                binding.chipHard.id -> Difficulty.HARD
                else -> Difficulty.EASY
            }
            pendingCharacters.clear()
            currentCharacter = null
            rebuildQueue()
            loadNextCharacter()
        }
        binding.chipEasy.isChecked = true
    }

    private fun setupActions() {
        binding.knowButton.setOnClickListener { handleResult(CharacterResult.KNOWN) }
        binding.unknownButton.setOnClickListener { handleResult(CharacterResult.UNKNOWN) }
        binding.skipButton.setOnClickListener { loadNextCharacter(requeueCurrent = true) }
        binding.listenButton.setOnClickListener { speakCurrentCharacter() }
        binding.viewProgressButton.setOnClickListener {
            startActivity(Intent(this, ProgressActivity::class.java))
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.playGameButton.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java).apply {
                putExtra(GameActivity.EXTRA_MODE, GameMode.LISTEN.name)
                putExtra(GameActivity.EXTRA_DIFFICULTY, currentDifficulty.name)
            }
            startActivity(intent)
        }
    }

    private fun handleResult(result: CharacterResult) {
        val character = currentCharacter ?: return
        when (result) {
            CharacterResult.KNOWN -> {
                knownIds.add(character.id)
                unknownIds.remove(character.id)
                celebrate()
            }
            CharacterResult.UNKNOWN -> {
                unknownIds.add(character.id)
                knownIds.remove(character.id)
                pendingCharacters.addLast(character)
                showEncourageSparkle()
                playEncourageSound()
                loadNextCharacter()
            }
        }
        progressStore.save(knownIds, unknownIds)
        progressStore.recordSnapshot(knownIds.size, unknownIds.size)
        updateSummaryHint()
    }

    private fun celebrate() {
        setActionButtonsEnabled(false)
        playSuccessSound()
        showConfetti {
            loadNextCharacter()
            setActionButtonsEnabled(true)
        }
    }

    private fun rebuildQueue() {
        pendingCharacters.clear()
        val pool = repository.byDifficulty(currentDifficulty)
        if (pool.isEmpty()) {
            updateCurrentCharacterView(null)
            Snackbar.make(binding.root, "当前难度暂无字词，请稍后再试", Snackbar.LENGTH_SHORT).show()
            return
        }

        val needReview = pool.filter { unknownIds.contains(it.id) }
        val untested = pool.filter { it.id !in knownIds && it.id !in unknownIds }
        val mastered = pool.filter { knownIds.contains(it.id) && it.id !in unknownIds }

        pendingCharacters.addAll(needReview)
        pendingCharacters.addAll(untested)
        pendingCharacters.addAll(mastered)
    }

    private fun loadNextCharacter(requeueCurrent: Boolean = false) {
        val previous = currentCharacter
        if (requeueCurrent && previous != null) {
            pendingCharacters.addLast(previous)
        }

        if (pendingCharacters.isEmpty()) {
            rebuildQueue()
        }

        currentCharacter = if (pendingCharacters.isEmpty()) {
            null
        } else {
            pendingCharacters.removeFirst()
        }

        updateCurrentCharacterView(currentCharacter)
        updateSummaryHint()
    }

    private fun updateCurrentCharacterView(character: LearningCharacter?) {
        if (character == null) {
            binding.currentCharacter.text = "——"
            binding.currentPinyin.text = "暂无汉字"
            binding.currentDifficulty.isVisible = false
            binding.remainingHint.text = "暂无可测汉字，请调整难度或重置进度"
            binding.cardEmoji.text = "💤"
            setActionButtonsEnabled(false)
            renderWordsAndExamples(null)
            return
        }

        binding.currentCharacter.text = character.hanzi
        binding.currentPinyin.text = character.pinyin.ifBlank { "(暂无拼音)" }
        binding.currentDifficulty.text = character.difficulty.label
        binding.currentDifficulty.isVisible = true
        binding.cardEmoji.text = mascotFaces[random.nextInt(mascotFaces.size)]
        setActionButtonsEnabled(true)
        renderWordsAndExamples(character)
        // 不自动朗读,等孩子主动点 "🔊 听一听" 按钮
    }

    /**
     * 把当前汉字的词组与例句渲染到字卡下方。
     *
     * 数据缺失(空列表)时:
     * - 词组/例句两块区域全部隐藏,不显示空标签
     * - 字卡恢复原来的紧凑外观
     *
     * 数据存在时:
     * - 词组 chip 点击即朗读该词组(带拼音)
     * - 例句 TextView 整体可点,点击即朗读该例句
     */
    private fun renderWordsAndExamples(character: LearningCharacter?) {
        val wordsGroup = binding.wordsChipGroup
        val exampleView = binding.exampleSentence
        val wordsDivider = binding.wordsDivider
        val wordsLabel = binding.wordsLabel
        val examplesLabel = binding.examplesLabel

        // 先清掉旧的 chip,避免切换汉字时残留
        wordsGroup.removeAllViews()
        exampleView.setOnClickListener(null)
        exampleView.text = ""

        val words = character?.words.orEmpty()
        val examples = character?.examples.orEmpty()

        if (words.isEmpty() && examples.isEmpty()) {
            wordsGroup.isVisible = false
            exampleView.isVisible = false
            wordsDivider.isVisible = false
            wordsLabel.isVisible = false
            examplesLabel.isVisible = false
            return
        }

        // 词组区
        if (words.isNotEmpty()) {
            wordsDivider.isVisible = true
            wordsLabel.isVisible = true
            wordsGroup.isVisible = true
            val inflater = LayoutInflater.from(this)
            words.forEachIndexed { index, entry ->
                val chip = inflater.inflate(R.layout.item_word_chip, wordsGroup, false) as Chip
                chip.text = entry.word
                chip.setOnClickListener {
                    speakWordOrSentence(
                        text = entry.word,
                        pinyin = entry.pinyin,
                        utteranceId = "main_word_${character?.id ?: 0}_$index"
                    )
                }
                wordsGroup.addView(chip)
            }
        } else {
            wordsGroup.isVisible = false
            wordsLabel.isVisible = false
            wordsDivider.isVisible = examples.isNotEmpty()  // 有例句时仍显示分隔线
        }

        // 例句区:取第一条,若有多条则用换行连接展示
        if (examples.isNotEmpty()) {
            examplesLabel.isVisible = true
            exampleView.isVisible = true
            val combinedText = examples.joinToString(separator = "\n") { it.sentence }
            exampleView.text = combinedText
            exampleView.setOnClickListener {
                val first = examples.first()
                speakWordOrSentence(
                    text = first.sentence,
                    pinyin = first.pinyin,
                    utteranceId = "main_example_${character?.id ?: 0}"
                )
            }
        } else {
            examplesLabel.isVisible = false
            exampleView.isVisible = false
        }
    }

    private fun updateSummaryHint() {
        val total = repository.count()
        val known = knownIds.size
        val unknown = unknownIds.size
        val untested = total - known - unknown
        binding.remainingHint.text =
            "已认识 $known / $total · 待巩固 $unknown · 未测 ${untested.coerceAtLeast(0)}"
    }

    override fun onResume() {
        super.onResume()
        reloadProgressFromStore()
        updateSummaryHint()
        rebuildQueue()
        loadNextCharacter()
    }

    private fun reloadProgressFromStore() {
        knownIds.clear()
        knownIds.addAll(progressStore.loadKnown())
        unknownIds.clear()
        unknownIds.addAll(progressStore.loadUnknown())
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        binding.knowButton.isEnabled = enabled
        binding.unknownButton.isEnabled = enabled
        binding.skipButton.isEnabled = enabled
    }

    private fun showEncourageSparkle() {
        val overlay = binding.confettiOverlay
        val width = overlay.width
        val height = overlay.height
        if (width == 0 || height == 0) {
            overlay.post { showEncourageSparkle() }
            return
        }

        val messages = listOf("继续加油！", "还差一点点", "我们一起努力")
        val label = TextView(this).apply {
            text = messages[random.nextInt(messages.size)]
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.deep_blue))
            setBackgroundResource(R.drawable.bg_status_unseen)
            setPadding(28, 12, 28, 12)
            alpha = 0f
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        overlay.addView(label, params)

        val startX = width / 2f - label.paint.measureText(label.text.toString()) / 2
        val startY = binding.actionRow.y - 24f
        label.translationX = startX
        label.translationY = startY

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                label.translationY = startY - 90 * fraction
                label.alpha = when {
                    fraction < 0.25f -> fraction / 0.25f
                    fraction > 0.8f -> (1f - fraction) / 0.2f
                    else -> 1f
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlay.removeView(label)
                }
            })
            start()
        }
    }

    private fun showConfetti(onEnd: () -> Unit) {
        val overlay = binding.confettiOverlay
        val width = overlay.width
        val height = overlay.height
        if (width == 0 || height == 0) {
            overlay.post { showConfetti(onEnd) }
            return
        }

        overlay.removeAllViews()

        val card = binding.currentCharacterCard
        val centerX = if (card.width > 0) card.x + card.width / 2f else width / 2f
        val centerY = if (card.height > 0) card.y + card.height / 2f else height / 2f
        val particles = mutableListOf<ValueAnimator>()

        repeat(CONFETTI_COUNT) { index ->
            val emoji = CONFETTI_EMOJIS[index % CONFETTI_EMOJIS.size]
            val textView = TextView(this).apply {
                text = emoji
                textSize = random.nextInt(18, 34).toFloat()
                alpha = 0f
            }
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            overlay.addView(textView, params)

            textView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val halfWidth = textView.measuredWidth / 2f
            val halfHeight = textView.measuredHeight / 2f

            val angle = random.nextDouble(0.0, Math.PI * 2)
            val velocity = random.nextDouble(0.35, 0.75) * height
            val rotationDirection = if (random.nextBoolean()) 1 else -1
            val rotationRange = random.nextInt(120, 300)

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = random.nextLong(900L, 1400L)
                interpolator = DecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    val fraction = valueAnimator.animatedValue as Float
                    val distance = velocity * fraction
                    val x = centerX + (distance * cos(angle)).toFloat()
                    val y = centerY + (distance * sin(angle)).toFloat()
                    textView.translationX = x - halfWidth
                    textView.translationY = y - halfHeight
                    textView.rotation = rotationDirection * rotationRange * fraction
                    textView.alpha = when {
                        fraction < 0.2f -> fraction / 0.2f
                        fraction > 0.8f -> (1f - fraction) / 0.2f
                        else -> 1f
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        overlay.removeView(textView)
                    }
                })
            }
            animator.start()
            particles.add(animator)
        }

        overlay.postDelayed({
            particles.forEach { it.cancel() }
            overlay.removeAllViews()
            onEnd()
        }, CONFETTI_DURATION_MS)
    }

    private fun playSuccessSound() {
        var player = successPlayer
        if (player == null) {
            player = MediaPlayer.create(this, R.raw.success)
            player?.setOnCompletionListener { mp -> mp.seekTo(0) }
            successPlayer = player
        }
        player?.let {
            if (it.isPlaying) {
                it.seekTo(0)
            }
            it.start()
        }
    }

    private fun playEncourageSound() {
        var player = encouragePlayer
        if (player == null) {
            player = MediaPlayer.create(this, R.raw.fail)
            player?.setOnCompletionListener { mp -> mp.seekTo(0) }
            encouragePlayer = player
        }
        player?.let {
            if (it.isPlaying) {
                it.seekTo(0)
            }
            it.start()
        }
    }

    companion object {
        private const val CONFETTI_COUNT = 18
        private const val CONFETTI_DURATION_MS = 300L
        private val CONFETTI_EMOJIS = listOf("🎉", "✨", "🎈", "🎊", "🌟", "💫")
    }
}
