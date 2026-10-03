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
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.studyword.literacy.R
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.EnglishRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityMainBinding
import com.studyword.literacy.game.GameActivity
import com.studyword.literacy.game.GameMode
import com.studyword.literacy.model.ChineseStudyItem
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.EnglishCategory
import com.studyword.literacy.model.EnglishLetterItem
import com.studyword.literacy.model.EnglishWordItem
import com.studyword.literacy.model.StudyItem
import com.studyword.literacy.model.StudyMode
import com.studyword.literacy.util.TtsManager
import java.util.ArrayDeque
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 主页(v1.4.0):
 * - 右上角抽屉收纳语言/难度/进度/设置
 * - 字卡可点击 → speakCurrentItem()
 * - 英文 mode 增加子模式(字母/单词)+ 单词按难度过滤
 * - 4 个 emoji 按钮(😊/😢/→/🎮),点击带中文 toast 提示
 * - 通过 registerForActivityResult 接 ProgressActivity/CharacterLibraryActivity 的跳转回值
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CharacterRepository
    private lateinit var englishRepository: EnglishRepository
    private lateinit var progressStore: ProgressStore

    // ====== 语种 ======
    private var currentMode: StudyMode = StudyMode.CHINESE

    // ====== 中文 ======
    private val knownIds: MutableSet<Int> = mutableSetOf()
    private val unknownIds: MutableSet<Int> = mutableSetOf()
    private var currentDifficulty: Difficulty = Difficulty.EASY

    // ====== 英文 ======
    private val englishKnownIds: MutableSet<Int> = mutableSetOf()
    private val englishUnknownIds: MutableSet<Int> = mutableSetOf()
    /** v1.4.0:英文 mode 的子模式。LETTERS=只显示字母;WORDS=只显示单词(按难度过滤) */
    private var englishSubMode: EnglishSubMode = EnglishSubMode.LETTERS

    // ====== 队列 ======
    private val pendingItems: ArrayDeque<StudyItem> = ArrayDeque()
    private var currentItem: StudyItem? = null

    private val random = Random(System.currentTimeMillis())
    private var successPlayer: MediaPlayer? = null
    private var encouragePlayer: MediaPlayer? = null
    private val mascotFaces = listOf("🐻", "🦊", "🐼", "🐰", "🦄", "🐨")

    /**
     * 从 ProgressActivity / CharacterLibraryActivity 接 selectedId + language
     */
    private val pageResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val selectedId = data.getIntExtra(EXTRA_SELECTED_ID, -1)
        val lang = data.getStringExtra(EXTRA_SELECTED_LANG) ?: return@registerForActivityResult
        if (selectedId < 0) return@registerForActivityResult
        loadItemById(selectedId, lang)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        englishRepository = EnglishRepository(this)
        progressStore = ProgressStore(this)

        currentMode = StudyMode.fromName(progressStore.loadLanguage())
        reloadAllProgressFromStore()
        progressStore.recordSnapshot(knownIds.size, unknownIds.size)

        TtsManager.init(this)
        setupMenuButton()
        setupDrawerEntries()
        setupLanguageToggle()
        setupDifficultyToggle()
        setupEnglishSubModeToggle()
        setupActions()
        setupCardClick()
        rebuildQueue()
        loadNextItem()
    }

    override fun onDestroy() {
        successPlayer?.release()
        successPlayer = null
        encouragePlayer?.release()
        encouragePlayer = null
        TtsManager.shutdown()
        super.onDestroy()
    }

    // ============================================================
    // TTS 朗读
    // ============================================================

    /**
     * 朗读当前学习项。点卡片触发。
     * - 中文字 → speak(汉字 + 拼音)— 拼音由 PinyinConverter 算得
     * - 英文 letter → speakSequential(letter, exampleWord, exampleWordChinese)
     * - 英文 word → speakSequential(word, chineseMeaning)
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
                val letter = item.letter
                TtsManager.speakSequential(
                    items = buildList {
                        add(letter.uppercase to true)
                        if (letter.exampleWord.isNotBlank()) add(letter.exampleWord to true)
                        if (letter.exampleWordChinese.isNotBlank()) add(letter.exampleWordChinese to false)
                    },
                    delayMs = 250,
                    baseUtteranceId = "main_letter_${item.id}"
                )
            }
            is EnglishWordItem -> {
                val word = item.word
                TtsManager.speakSequential(
                    items = buildList {
                        add(word.word to true)
                        if (word.chineseMeaning.isNotBlank()) add(word.chineseMeaning to false)
                    },
                    delayMs = 250,
                    baseUtteranceId = "main_word_${item.id}"
                )
            }
        }
    }

    /**
     * 朗读一段自定义文本(供词组 chip / 例句 TextView 点击调用)。
     * 中文走 char-by-char 队列(多音字安全),英文走 speakEnglish。
     */
    private fun speakWordOrSentence(text: String, pinyin: String, utteranceId: String, isEnglish: Boolean) {
        if (isEnglish) {
            TtsManager.speakEnglish(text, utteranceId = utteranceId)
        } else {
            // 中文走 char-by-char 队列,引擎用默认读音(避开多音字误读)
            TtsManager.speakPhraseCharByChar(text, utteranceId = utteranceId)
        }
    }

    /** 例句朗读:英文 → 英文 + 中文翻译;中文 → char-by-char */
    private fun speakExampleSentence(item: StudyItem?, sentence: String, utteranceId: String) {
        if (item == null) {
            TtsManager.speakPhraseCharByChar(sentence, utteranceId = utteranceId)
            return
        }
        if (item is EnglishWordItem && item.word.exampleSentenceTranslation.isNotBlank()) {
            TtsManager.speakSequential(
                items = listOf(
                    sentence to true,
                    item.word.exampleSentenceTranslation to false
                ),
                delayMs = 300,
                baseUtteranceId = utteranceId
            )
        } else if (item is EnglishLetterItem) {
            // 字母没有例句;走英文版
            TtsManager.speakEnglish(sentence, utteranceId = utteranceId)
        } else {
            // 中文例句:char-by-char
            TtsManager.speakPhraseCharByChar(sentence, utteranceId = utteranceId)
        }
    }

    // ============================================================
    // 控件初始化
    // ============================================================

    private fun setupMenuButton() {
        binding.menuButton.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }
    }

    /**
     * 抽屉内的导航入口。抽屉中的进度 / 字库 / 设置点击也走 pageResultLauncher,
     * 进度 / 字库可以返回 selectedId 触发跳回主页特定卡片。
     */
    private fun setupDrawerEntries() {
        binding.viewProgressButton.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            pageResultLauncher.launch(Intent(this, ProgressActivity::class.java))
        }
        binding.libraryButton.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            pageResultLauncher.launch(Intent(this, CharacterLibraryActivity::class.java))
        }
        binding.settingsButton.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

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
        when (currentMode) {
            StudyMode.CHINESE -> binding.chipLanguageChinese.isChecked = true
            StudyMode.ENGLISH -> binding.chipLanguageEnglish.isChecked = true
        }
        applyModeUi()
    }

    /** 中文 → 显示"难度",隐藏英文子模式;英文 → 显示"英文子模式",但难度仍按英文模式显示 */
    private fun applyModeUi() {
        val isEnglish = currentMode == StudyMode.ENGLISH
        binding.difficultyLabel.isVisible = true
        binding.difficultyChipGroup.isVisible = true
        binding.englishSubModeGroup.isVisible = isEnglish
        binding.subtitle.text = if (isEnglish) "Letters & words, learn with fun!" else "一起开启有趣的识字冒险！"
        // 英文默认选中 letters
        if (isEnglish && !binding.chipSubLetters.isChecked && !binding.chipSubWords.isChecked) {
            binding.chipSubLetters.isChecked = true
            englishSubMode = EnglishSubMode.LETTERS
        }
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

    private fun setupEnglishSubModeToggle() {
        binding.englishSubModeGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            englishSubMode = when (checkedId) {
                binding.chipSubLetters.id -> EnglishSubMode.LETTERS
                binding.chipSubWords.id -> EnglishSubMode.WORDS
                else -> EnglishSubMode.LETTERS
            }
            pendingItems.clear()
            currentItem = null
            rebuildQueue()
            loadNextItem()
        }
    }

    /**
     * 4 个 emoji 按钮:认识 / 不认识 / 下一个 / 游戏
     * 每个点击有中文 toast 提示
     */
    private fun setupActions() {
        binding.knowButton.setOnClickListener {
            toast("认识啦!")
            handleResult(ItemResult.KNOWN)
        }
        binding.unknownButton.setOnClickListener {
            toast("没关系,下次记住!")
            handleResult(ItemResult.UNKNOWN)
        }
        binding.skipButton.setOnClickListener {
            toast("下一个!")
            loadNextItem(requeueCurrent = true)
        }
        binding.playGameButton.setOnClickListener {
            toast("来玩游戏吧!")
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

    /** 字卡点击 → speakCurrentItem() */
    private fun setupCardClick() {
        binding.currentCharacterCard.setOnClickListener { speakCurrentItem() }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    // ============================================================
    // 跳转回主页特定卡片
    // ============================================================

    /**
     * 从 ProgressActivity / CharacterLibraryActivity 跳转回主页指定卡片。
     *
     * - 如果语言不同,先切语言(切完会 rebuildQueue,然后再把 id 推到队首)
     * - 如果 id 找不到(已删除/不在当前池),保留当前卡片不动
     */
    private fun loadItemById(id: Int, lang: String) {
        val targetMode = StudyMode.fromName(lang)
        if (targetMode != currentMode) {
            currentMode = targetMode
            progressStore.saveLanguage(targetMode.name)
            applyModeUi()
        }
        rebuildQueue()
        // 把 id 推到队首(ArrayDeque 没有 removeAt,改用 toList+重建)
        val matchIdx = pendingItems.indexOfFirst { it.id == id }
        if (matchIdx > 0) {
            val all = pendingItems.toList()
            pendingItems.clear()
            val found = all[matchIdx]
            pendingItems.addLast(found)
            all.forEachIndexed { i, it -> if (i != matchIdx) pendingItems.addLast(it) }
        } else if (matchIdx < 0) {
            // 不在当前 pool — 静默忽略(避免误跳导致空卡)
            return
        }
        loadNextItem()
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
     * 重建题池。
     * - 中文:repository.byDifficulty(currentDifficulty)
     * - 英文 + LETTERS:全部字母
     * - 英文 + WORDS:byCategoryAndDifficulty(WORDS, currentDifficulty)
     */
    private fun rebuildQueue() {
        pendingItems.clear()
        val pool = buildCurrentPool()
        if (pool.isEmpty()) {
            updateCurrentItemView(null)
            val emptyMsg = when (currentMode) {
                StudyMode.CHINESE -> "当前难度暂无字词,请稍后再试"
                StudyMode.ENGLISH -> "暂无内容,请切换子模式或难度"
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
            val rawItems: List<Any> = when (englishSubMode) {
                EnglishSubMode.LETTERS -> englishRepository.byCategory(EnglishCategory.LETTERS)
                EnglishSubMode.WORDS -> englishRepository.byCategoryAndDifficulty(EnglishCategory.WORDS, currentDifficulty)
            }
            rawItems.map { item ->
                when (item) {
                    is com.studyword.literacy.model.EnglishLetter -> EnglishLetterItem(item)
                    is com.studyword.literacy.model.EnglishWord -> EnglishWordItem(item)
                    else -> null
                }
            }.filterNotNull()
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
                StudyMode.CHINESE -> "暂无可测汉字,请调整难度或重置进度"
                StudyMode.ENGLISH -> "暂无可测内容"
            }
            binding.cardEmoji.text = "💤"
            setActionButtonsEnabled(false)
            renderWordsAndExamples(null)
            return
        }

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

        binding.currentPinyin.text = item.secondaryText.ifBlank { "--" }

        binding.currentDifficulty.text = item.category
        binding.currentDifficulty.isVisible = true

        binding.cardEmoji.text = if (item is EnglishWordItem) "🔤" else mascotFaces[random.nextInt(mascotFaces.size)]

        setActionButtonsEnabled(true)
        renderWordsAndExamples(item)
    }

    /**
     * 渲染词组 + 例句。
     * v1.4.0 改进:
     * - 英文 letter 的 exampleWord 后面加上中文意思 chip(不可点,作为展示)
     * - 例句点击用 speakExampleSentence(英文 + 中文翻译)
     */
    private fun renderWordsAndExamples(item: StudyItem?) {
        val wordsGroup = binding.wordsChipGroup
        val exampleView = binding.exampleSentence
        val wordsDivider = binding.wordsDivider
        val wordsLabel = binding.wordsLabel
        val examplesLabel = binding.examplesLabel

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

        if (words.isNotEmpty()) {
            wordsDivider.isVisible = true
            wordsLabel.isVisible = true
            wordsGroup.isVisible = true
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
            // 英文 letter:在示例词 chip 后追加"中文意思"chip(只读,展示)
            if (item is EnglishLetterItem && item.letter.exampleWordChinese.isNotBlank()) {
                val hintChip = inflater.inflate(R.layout.item_word_chip, wordsGroup, false) as Chip
                hintChip.text = item.letter.exampleWordChinese
                hintChip.isClickable = false
                hintChip.isCheckable = false
                wordsGroup.addView(hintChip)
            }
        } else {
            wordsGroup.isVisible = false
            wordsLabel.isVisible = false
            wordsDivider.isVisible = examples.isNotEmpty()
        }

        if (examples.isNotEmpty()) {
            examplesLabel.isVisible = true
            exampleView.isVisible = true
            examplesLabel.text = if (isEnglish) "📖 Example" else "📖 例句"
            val first = examples.first()
            val displayText = if (isEnglish && item?.englishExtra?.isNotBlank() == true) {
                "${first.sentence}\n— ${item.englishExtra}"
            } else {
                examples.joinToString(separator = "\n") { it.sentence }
            }
            exampleView.text = displayText
            exampleView.setOnClickListener {
                speakExampleSentence(item, first.sentence, "main_example_${item?.id ?: 0}")
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
        // 仅在 currentItem 丢失时(首次启动 / 进程被回收)才重建队列 + loadNext。
        // 否则 pageResultLauncher → loadItemById 设置的 currentItem 会被 onResume 的
        // loadNextItem 覆盖,导致跳转失效(显示队列中下一项而非跳转目标)。
        if (currentItem == null) {
            rebuildQueue()
            loadNextItem()
        }
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
        binding.playGameButton.isEnabled = enabled
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

        // v1.4.0:锚点改为 remainingHint(原本是 actionRow,但现在 actionRow 是 emoji 按钮不美观)
        val startX = width / 2f - label.paint.measureText(label.text.toString()) / 2
        val anchorY = binding.remainingHint.y.takeIf { it > 0f } ?: (height / 2f)
        val startY = anchorY - 24f
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
    private enum class EnglishSubMode { LETTERS, WORDS }

    companion object {
        private const val CONFETTI_COUNT = 18
        private const val CONFETTI_DURATION_MS = 300L
        private val CONFETTI_EMOJIS = listOf("🎉", "✨", "🎈", "🎊", "🌟", "💫")

        /** ProgressActivity / CharacterLibraryActivity setResult 时填入的 extras */
        const val EXTRA_SELECTED_ID = "selected_id"
        const val EXTRA_SELECTED_LANG = "selected_lang"  // "CHINESE" / "ENGLISH"
    }
}