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
import com.studyword.literacy.data.EnglishRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityMainBinding
import com.studyword.literacy.game.GameActivity
import com.studyword.literacy.game.GameMode
import com.studyword.literacy.model.ChineseStudyItem
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.EnglishLetterItem
import com.studyword.literacy.model.EnglishWordItem
import com.studyword.literacy.model.StudyItem
import com.studyword.literacy.model.StudyMode
import com.studyword.literacy.util.TtsManager
import java.util.ArrayDeque
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CharacterRepository
    private lateinit var englishRepository: EnglishRepository
    private lateinit var progressStore: ProgressStore

    // ====== Phase 2 状态:当前学习语种 ======
    private var currentMode: StudyMode = StudyMode.CHINESE

    // ====== 中文维度 ======
    private val knownIds: MutableSet<Int> = mutableSetOf()
    private val unknownIds: MutableSet<Int> = mutableSetOf()
    private var currentDifficulty: Difficulty = Difficulty.EASY

    // ====== 英文维度 ======
    private val englishKnownIds: MutableSet<Int> = mutableSetOf()
    private val englishUnknownIds: MutableSet<Int> = mutableSetOf()

    // ====== 统一学习队列(StudyItem 抽象,中英文共用) ======
    private val pendingItems: ArrayDeque<StudyItem> = ArrayDeque()
    private var currentItem: StudyItem? = null

    private val random = Random(System.currentTimeMillis())
    private var successPlayer: MediaPlayer? = null
    private var encouragePlayer: MediaPlayer? = null
    private val mascotFaces = listOf("🐻", "🦊", "🐼", "🐰", "🦄", "🐨")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        englishRepository = EnglishRepository(this)
        progressStore = ProgressStore(this)

        // 读取上次语种并恢复
        currentMode = StudyMode.fromName(progressStore.loadLanguage())

        reloadAllProgressFromStore()
        progressStore.recordSnapshot(knownIds.size, unknownIds.size)

        TtsManager.init(this)
        setupLanguageToggle()
        setupDifficultyToggle()
        setupActions()
        rebuildQueue()
        loadNextItem()
    }

    override fun onDestroy() {
        successPlayer?.release()
        successPlayer = null
        encouragePlayer?.release()
        encouragePlayer = null
        super.onDestroy()
    }

    // ============================================================
    // TTS 朗读:按语种路由
    // ============================================================

    /**
     * 朗读当前学习项。
     * 中文:汉字 + 拼音(走 TtsManager.speak,中文 Locale)
     * 英文 letter:大写 + 小写(走 speakEnglish)
     * 英文 word:单词 + 音标(走 speakEnglish)
     */
    private fun speakCurrentItem() {
        val item = currentItem ?: return
        when (item) {
            is ChineseStudyItem -> {
                val pinyin = item.character.pinyin.ifBlank { "" }
                val text = if (pinyin.isNotEmpty()) "${item.character.hanzi}   $pinyin" else item.character.hanzi
                TtsManager.speak(text, utteranceId = "main_char_${item.id}")
            }
            is EnglishLetterItem -> {
                TtsManager.speakEnglish(item.letter.uppercase, utteranceId = "main_letter_${item.id}")
            }
            is EnglishWordItem -> {
                TtsManager.speakEnglish(item.word.word, utteranceId = "main_word_${item.id}")
            }
        }
    }

    /**
     * 朗读一段自定义文本(供词组 chip / 例句 TextView 点击调用)。
     * 中文走 [TtsManager.speak],英文走 [TtsManager.speakEnglish]。
     */
    private fun speakWordOrSentence(text: String, pinyin: String, utteranceId: String, isEnglish: Boolean) {
        if (isEnglish) {
            TtsManager.speakEnglish(text, utteranceId = utteranceId)
        } else {
            val combined = if (pinyin.isNotBlank()) "$text   $pinyin" else text
            TtsManager.speak(combined, utteranceId = utteranceId)
        }
    }

    // ============================================================
    // 顶部控件初始化
    // ============================================================

    private fun setupLanguageToggle() {
        binding.languageChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val newMode = when (checkedId) {
                binding.chipLanguageChinese.id -> StudyMode.CHINESE
                binding.chipLanguageEnglish.id -> StudyMode.ENGLISH
                else -> StudyMode.CHINESE
            }
            if (newMode != currentMode) {
                currentMode = newMode
                progressStore.saveLanguage(newMode.name)
                pendingItems.clear()
                currentItem = null
                applyModeUi()
                rebuildQueue()
                loadNextItem()
            }
        }
        // 初始化选中状态
        when (currentMode) {
            StudyMode.CHINESE -> binding.chipLanguageChinese.isChecked = true
            StudyMode.ENGLISH -> binding.chipLanguageEnglish.isChecked = true
        }
        applyModeUi()
    }

    /**
     * 切换语言时,根据当前 mode 调整 UI:
     * - 中文模式显示"选择任务难度"+ 简单/中等/困难
     * - 英文模式隐藏难度 chip(英文只有 LETTERS / WORDS 两类,但通过 Pool 决定,无需用户选)
     */
    private fun applyModeUi() {
        val isEnglish = currentMode == StudyMode.ENGLISH
        binding.difficultyLabel.isVisible = !isEnglish
        binding.difficultyChipGroup.isVisible = !isEnglish
        // 英文模式标题文案微调
        binding.subtitle.text = if (isEnglish) "Letters & words, learn with fun!" else "一起开启有趣的识字冒险！"
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
            pendingItems.clear()
            currentItem = null
            rebuildQueue()
            loadNextItem()
        }
        binding.chipEasy.isChecked = true
    }

    private fun setupActions() {
        binding.knowButton.setOnClickListener { handleResult(ItemResult.KNOWN) }
        binding.unknownButton.setOnClickListener { handleResult(ItemResult.UNKNOWN) }
        binding.skipButton.setOnClickListener { loadNextItem(requeueCurrent = true) }
        binding.listenButton.setOnClickListener { speakCurrentItem() }
        binding.viewProgressButton.setOnClickListener {
            startActivity(Intent(this, ProgressActivity::class.java))
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.playGameButton.setOnClickListener {
            // 根据当前 mode 选择默认游戏类型
            val defaultMode = when (currentMode) {
                StudyMode.CHINESE -> GameMode.LISTEN
                StudyMode.ENGLISH -> GameMode.LISTEN_LETTER
            }
            val intent = Intent(this, GameActivity::class.java).apply {
                putExtra(GameActivity.EXTRA_MODE, defaultMode.name)
                putExtra(GameActivity.EXTRA_DIFFICULTY, currentDifficulty.name)
            }
            startActivity(intent)
        }
    }

    // ============================================================
    // 判定与队列
    // ============================================================

    private fun handleResult(result: ItemResult) {
        val item = currentItem ?: return
        when (currentMode) {
            StudyMode.CHINESE -> recordChineseResult(item.id, result)
            StudyMode.ENGLISH -> recordEnglishResult(item.id, result)
        }
        when (result) {
            ItemResult.KNOWN -> celebrate()
            ItemResult.UNKNOWN -> {
                pendingItems.addLast(item)
                showEncourageSparkle()
                playEncourageSound()
                loadNextItem()
            }
        }
        persistProgress()
        updateSummaryHint()
    }

    private fun recordChineseResult(id: Int, result: ItemResult) {
        when (result) {
            ItemResult.KNOWN -> {
                knownIds.add(id)
                unknownIds.remove(id)
            }
            ItemResult.UNKNOWN -> {
                unknownIds.add(id)
                knownIds.remove(id)
            }
        }
    }

    private fun recordEnglishResult(id: Int, result: ItemResult) {
        when (result) {
            ItemResult.KNOWN -> {
                englishKnownIds.add(id)
                englishUnknownIds.remove(id)
            }
            ItemResult.UNKNOWN -> {
                englishUnknownIds.add(id)
                englishKnownIds.remove(id)
            }
        }
    }

    private fun persistProgress() {
        progressStore.save(knownIds, unknownIds)
        progressStore.saveEnglish(englishKnownIds, englishUnknownIds)
        progressStore.recordSnapshot(knownIds.size, unknownIds.size)
    }

    private fun celebrate() {
        setActionButtonsEnabled(false)
        playSuccessSound()
        showConfetti {
            loadNextItem()
            setActionButtonsEnabled(true)
        }
    }

    /**
     * 按当前 mode 与难度/状态,重建题池。
     * - 中文:[Difficulty] × (known + unknown + 未测),unknown 优先复习
     * - 英文:letters(26) + words(30) ∩ (known + unknown + 未测),unknown 优先复习
     */
    private fun rebuildQueue() {
        pendingItems.clear()
        val pool = buildCurrentPool()
        if (pool.isEmpty()) {
            updateCurrentItemView(null)
            val emptyMsg = when (currentMode) {
                StudyMode.CHINESE -> "当前难度暂无字词，请稍后再试"
                StudyMode.ENGLISH -> "No items yet, please check your data"
            }
            Snackbar.make(binding.root, emptyMsg, Snackbar.LENGTH_SHORT).show()
            return
        }

        val (known, unknown) = currentKnownUnknown()
        val needReview = pool.filter { unknown.contains(it.id) }
        val untested = pool.filter { it.id !in known && it.id !in unknown }
        val mastered = pool.filter { known.contains(it.id) && it.id !in unknown }

        pendingItems.addAll(needReview)
        pendingItems.addAll(untested)
        pendingItems.addAll(mastered)
    }

    private fun buildCurrentPool(): List<StudyItem> = when (currentMode) {
        StudyMode.CHINESE -> repository.byDifficulty(currentDifficulty).map { ChineseStudyItem(it) }
        StudyMode.ENGLISH -> {
            val letters = englishRepository.letters().map { EnglishLetterItem(it) }
            val words = englishRepository.words().map { EnglishWordItem(it) }
            letters + words
        }
    }

    private fun currentKnownUnknown(): Pair<Set<Int>, Set<Int>> = when (currentMode) {
        StudyMode.CHINESE -> knownIds to unknownIds
        StudyMode.ENGLISH -> englishKnownIds to englishUnknownIds
    }

    private fun loadNextItem(requeueCurrent: Boolean = false) {
        val previous = currentItem
        if (requeueCurrent && previous != null) {
            pendingItems.addLast(previous)
        }

        if (pendingItems.isEmpty()) {
            rebuildQueue()
        }

        currentItem = if (pendingItems.isEmpty()) {
            null
        } else {
            pendingItems.removeFirst()
        }

        updateCurrentItemView(currentItem)
        updateSummaryHint()
    }

    // ============================================================
    // 字卡渲染(StudyItem 统一)
    // ============================================================

    private fun updateCurrentItemView(item: StudyItem?) {
        if (item == null) {
            binding.currentCharacter.isVisible = false
            binding.uppercaseText.isVisible = false
            binding.lowercaseText.isVisible = false
            binding.currentPinyin.text = ""
            binding.currentDifficulty.isVisible = false
            binding.remainingHint.text = when (currentMode) {
                StudyMode.CHINESE -> "暂无可测汉字，请调整难度或重置进度"
                StudyMode.ENGLISH -> "暂无可测内容"
            }
            binding.cardEmoji.text = "💤"
            setActionButtonsEnabled(false)
            renderWordsAndExamples(null)
            return
        }

        // 主显:中文字 / 英文 word → currentCharacter;英文 letter → uppercaseText + lowercaseText
        val isLetter = item is EnglishLetterItem
        binding.currentCharacter.isVisible = !isLetter
        binding.uppercaseText.isVisible = isLetter
        binding.lowercaseText.isVisible = isLetter
        if (isLetter) {
            val letter = (item as EnglishLetterItem).letter
            binding.uppercaseText.text = letter.uppercase
            binding.lowercaseText.text = letter.lowercase
        } else {
            binding.currentCharacter.text = item.primaryText
        }

        // 拼音 / 音标
        binding.currentPinyin.text = item.secondaryText.ifBlank { "--" }

        // 难度/类别徽章
        binding.currentDifficulty.text = item.category
        binding.currentDifficulty.isVisible = true

        // 顶部表情
        binding.cardEmoji.text = if (item is EnglishWordItem) {
            // 英文单词用统一的 emoji(没有 mascot 对应)
            "🔤"
        } else {
            mascotFaces[random.nextInt(mascotFaces.size)]
        }

        setActionButtonsEnabled(true)
        renderWordsAndExamples(item)
        // 不自动朗读,等孩子主动点 "🔊 听一听" 按钮
    }

    /**
     * 把当前学习项的词组与例句渲染到字卡下方。
     * 设计完全复用 v1.2.0 逻辑,仅 TTS 路由改为按 ttsLocale 区分。
     */
    private fun renderWordsAndExamples(item: StudyItem?) {
        val wordsGroup = binding.wordsChipGroup
        val exampleView = binding.exampleSentence
        val wordsDivider = binding.wordsDivider
        val wordsLabel = binding.wordsLabel
        val examplesLabel = binding.examplesLabel

        // 先清掉旧的 chip,避免切换字时残留
        wordsGroup.removeAllViews()
        exampleView.setOnClickListener(null)
        exampleView.text = ""

        val words = item?.words.orEmpty()
        val examples = item?.examples.orEmpty()
        val isEnglish = item?.ttsLocale == "en"

        if (words.isEmpty() && examples.isEmpty()) {
            wordsGroup.isVisible = false
            exampleView.isVisible = false
            wordsDivider.isVisible = false
            wordsLabel.isVisible = false
            examplesLabel.isVisible = false
            return
        }

        // 词组区(英文 letter 的 exampleWord 也走这里,作为单个 chip)
        if (words.isNotEmpty()) {
            wordsDivider.isVisible = true
            wordsLabel.isVisible = true
            wordsGroup.isVisible = true
            // 英文 letter 用 "🌟 示例词",中文用 "🧩 词组"
            wordsLabel.text = if (isEnglish) "🌟 示例词" else "🧩 词组"
            val inflater = LayoutInflater.from(this)
            words.forEachIndexed { index, entry ->
                val chip = inflater.inflate(R.layout.item_word_chip, wordsGroup, false) as Chip
                chip.text = entry.word
                chip.setOnClickListener {
                    speakWordOrSentence(
                        text = entry.word,
                        pinyin = entry.pinyin,
                        utteranceId = "main_word_${item?.id ?: 0}_$index",
                        isEnglish = isEnglish
                    )
                }
                wordsGroup.addView(chip)
            }
        } else {
            wordsGroup.isVisible = false
            wordsLabel.isVisible = false
            wordsDivider.isVisible = examples.isNotEmpty()
        }

        // 例句区(英文 word 的 exampleSentence 走这里,中文字也走这里)
        if (examples.isNotEmpty()) {
            examplesLabel.isVisible = true
            exampleView.isVisible = true
            examplesLabel.text = if (isEnglish) "📖 Example" else "📖 例句"
            val first = examples.first()
            // 英文 word 拼接中文释义(显示在例句下方),点击只朗读英文例句
            val displayText = if (isEnglish && item?.exampleTranslation?.isNotBlank() == true) {
                "${first.sentence}\n— ${item.exampleTranslation}"
            } else {
                examples.joinToString(separator = "\n") { it.sentence }
            }
            exampleView.text = displayText
            exampleView.setOnClickListener {
                speakWordOrSentence(
                    text = first.sentence,
                    pinyin = first.pinyin,
                    utteranceId = "main_example_${item?.id ?: 0}",
                    isEnglish = isEnglish
                )
            }
        } else {
            examplesLabel.isVisible = false
            exampleView.isVisible = false
        }
    }

    private fun updateSummaryHint() {
        when (currentMode) {
            StudyMode.CHINESE -> {
                val total = repository.count()
                val known = knownIds.size
                val unknown = unknownIds.size
                val untested = total - known - unknown
                binding.remainingHint.text =
                    "已认识 $known / $total · 待巩固 $unknown · 未测 ${untested.coerceAtLeast(0)}"
            }
            StudyMode.ENGLISH -> {
                val total = englishRepository.count()
                val known = englishKnownIds.size
                val unknown = englishUnknownIds.size
                val untested = total - known - unknown
                binding.remainingHint.text =
                    "Known $known / $total · Review $unknown · New ${untested.coerceAtLeast(0)}"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reloadAllProgressFromStore()
        updateSummaryHint()
        rebuildQueue()
        loadNextItem()
    }

    private fun reloadAllProgressFromStore() {
        knownIds.clear()
        knownIds.addAll(progressStore.loadKnown())
        unknownIds.clear()
        unknownIds.addAll(progressStore.loadUnknown())
        englishKnownIds.clear()
        englishKnownIds.addAll(progressStore.loadEnglishKnown())
        englishUnknownIds.clear()
        englishUnknownIds.addAll(progressStore.loadEnglishUnknown())
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

        val messages = listOf("继续加油！", "还差一点点", "我们一起努力", "Try again!")
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

    private enum class ItemResult { KNOWN, UNKNOWN }

    companion object {
        private const val CONFETTI_COUNT = 18
        private const val CONFETTI_DURATION_MS = 300L
        private val CONFETTI_EMOJIS = listOf("🎉", "✨", "🎈", "🎊", "🌟", "💫")
    }
}
