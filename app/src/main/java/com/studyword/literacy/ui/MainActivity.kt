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
import android.widget.LinearLayout
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

    /**
     * v1.4.2:跳转来源 — null = 正常启动;/ "progress" = 从 ProgressActivity 跳回;
     * "library" = 从 CharacterLibraryActivity 跳回。用于显示"← 返回进度/字库"按钮。
     */
    private var jumpSource: String? = null

    private val random = Random(System.currentTimeMillis())
    private var successPlayer: MediaPlayer? = null
    private var encouragePlayer: MediaPlayer? = null
    private val mascotFaces = listOf("🐻", "🦊", "🐼", "🐰", "🦄", "🐨")
    private val drawerGreetings = listOf("你好呀!", "欢迎回来!", "今天我们一起学!", "Hi,准备好啦吗?")

    /**
     * 从 ProgressActivity / CharacterLibraryActivity 接 selectedId + language + 来源页
     * 来源页用于显示"← 返回"按钮,让用户能从主页跳回去
     */
    private val pageResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val selectedId = data.getIntExtra(EXTRA_SELECTED_ID, -1)
        val lang = data.getStringExtra(EXTRA_SELECTED_LANG) ?: return@registerForActivityResult
        if (selectedId < 0) return@registerForActivityResult
        // v1.4.2:记录来源,显示"← 返回"按钮
        jumpSource = data.getStringExtra(EXTRA_SOURCE_PAGE)
        refreshBackJumpButton()
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
        setupBackJumpButton()
        setupLanguageToggle()
        setupDifficultyToggle()
        setupEnglishSubModeToggle()
        setupActions()
        setupCardClick()
        refreshDrawerGreeting()
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
     * v1.4.1:letter 模式只读字母名(用户需求);word 模式读 EN + 中文意思(带停顿)
     * v1.4.2:中文字只读一遍,不再把拼音拼到文本里给 TTS(否则会被读成"日 rì")
     *
     * - 中文字 → speak(汉字)— 引擎默认读音,常用字最准
     * - 英文 letter → speakEnglish(uppercase),只读字母名(示例词由 chip 单独触发)
     * - 英文 word → speakSequential(word, chineseMeaning) 500ms 停顿
     */
    private fun speakCurrentItem() {
        val item = currentItem ?: return
        when (item) {
            is ChineseStudyItem -> {
                // v1.4.2:只读汉字一遍,不再 append 拼音(拼音仅作为卡片下方的 visual hint)
                TtsManager.speak(item.character.hanzi, utteranceId = "main_char_${item.id}")
            }
            is EnglishLetterItem -> {
                TtsManager.speakEnglish(item.letter.uppercase, utteranceId = "main_letter_${item.id}")
            }
            is EnglishWordItem -> {
                val word = item.word
                TtsManager.speakSequential(
                    items = buildList {
                        add(word.word to true)
                        if (word.chineseMeaning.isNotBlank()) add(word.chineseMeaning to false)
                    },
                    delayMs = 500,
                    baseUtteranceId = "main_word_${item.id}"
                )
            }
        }
    }

    /**
     * v1.4.1:点击字母行(letterContainer)只读字母,行为同 letter 分支。
     */
    private fun speakLetterOnly() {
        val letter = (currentItem as? EnglishLetterItem)?.letter ?: return
        TtsManager.speakEnglish(letter.uppercase, utteranceId = "main_letter_only_${letter.id}")
    }

    /**
     * 朗读一段自定义文本(供词组 chip / 例句 TextView 点击调用)。
     * v1.4.2:中文走整段 speak(),正常语速(用户反馈"词组例句读得慢")
     */
    private fun speakWordOrSentence(text: String, pinyin: String, utteranceId: String, isEnglish: Boolean) {
        if (isEnglish) {
            TtsManager.speakEnglish(text, utteranceId = utteranceId)
        } else {
            TtsManager.speak(text, utteranceId = utteranceId)
        }
    }

    /**
     * 例句朗读。v1.4.2:全部正常语速一遍发音。
     * - 英文 word:英文 + 中文翻译(500ms 间隔)
     * - 英文 letter:只读英文
     * - 中文:整段读一遍
     */
    private fun speakExampleSentence(item: StudyItem?, sentence: String, utteranceId: String) {
        if (item == null) {
            TtsManager.speak(sentence, utteranceId = utteranceId)
            return
        }
        if (item is EnglishWordItem && item.word.exampleSentenceTranslation.isNotBlank()) {
            TtsManager.speakSequential(
                items = listOf(
                    sentence to true,
                    item.word.exampleSentenceTranslation to false
                ),
                delayMs = 500,
                baseUtteranceId = utteranceId
            )
        } else if (item is EnglishLetterItem) {
            // 字母没有例句;走英文版
            TtsManager.speakEnglish(sentence, utteranceId = utteranceId)
        } else {
            // 中文例句:整段一遍
            TtsManager.speak(sentence, utteranceId = utteranceId)
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
     * v1.4.2:抽屉入口改为 3 张大卡(进度 / 字库 / 设置)。
     * - 进度 / 字库走 pageResultLauncher,可能返回 selectedId 跳回主页特定卡片
     * - 设置走普通 startActivity
     */
    private fun setupDrawerEntries() {
        binding.drawerProgressCard.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            pageResultLauncher.launch(Intent(this, ProgressActivity::class.java))
        }
        binding.drawerLibraryCard.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            pageResultLauncher.launch(Intent(this, CharacterLibraryActivity::class.java))
        }
        binding.drawerSettingsCard.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /**
     * v1.4.2:从进度 / 字库跳转回来后,显示"← 返回进度"或"← 返回字库"按钮。
     * 点击 → 重启源 Activity + 关闭主页。
     */
    private fun setupBackJumpButton() {
        binding.backJumpButton.setOnClickListener {
            val intent = when (jumpSource) {
                SOURCE_PROGRESS -> Intent(this, ProgressActivity::class.java)
                SOURCE_LIBRARY -> Intent(this, CharacterLibraryActivity::class.java)
                else -> null
            }
            if (intent != null) {
                startActivity(intent)
                finish()
            }
        }
    }

    private fun refreshBackJumpButton() {
        val source = jumpSource
        if (source == null) {
            binding.backJumpButton.visibility = View.GONE
            return
        }
        binding.backJumpButton.visibility = View.VISIBLE
        binding.backJumpButton.text = when (source) {
            SOURCE_PROGRESS -> "← 返回进度"
            SOURCE_LIBRARY -> "← 返回字库"
            else -> "← 返回"
        }
    }

    /**
     * v1.4.2:每次启动随机挑一句问候,放在抽屉顶部
     */
    private fun refreshDrawerGreeting() {
        binding.drawerGreeting.text = drawerGreetings[random.nextInt(drawerGreetings.size)]
        binding.drawerAvatar.text = mascotFaces[random.nextInt(mascotFaces.size)]
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
        // v1.4.2:删 subtitle TextView,问候文案移到抽屉 greeting 区
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

    /**
     * v1.4.1 字卡点击逻辑:
     * - 卡片整体(内部 LinearLayout)→ speakCurrentItem()
     *   - letter 模式:只读字母名
     *   - word 模式:读 EN + 中文意思(500ms 停顿)
     *   - 中文模式:读汉字 + 拼音
     * - letterContainer(字母行 Aa)→ speakLetterOnly(),覆盖卡片整体事件
     *   行为同 letter 分支,但确保即使在 card 整体被 clickable 时点击字母行也只读字母
     *
     * 注意:card 的 MaterialCardView 本体 clickable=false,真正的点击事件源是
     * 内部的 LinearLayout(android:foreground="?attr/selectableItemBackground")。
     */
    private fun setupCardClick() {
        val cardContent = (binding.currentCharacterCard.getChildAt(0) as? LinearLayout)
            ?: binding.currentCharacterCard
        cardContent.setOnClickListener { speakCurrentItem() }
        binding.letterContainer.setOnClickListener { speakLetterOnly() }
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
     * - 如果语言不同,切语言
     * - 如果是英文 id,自动切子模式(letter / word)和难度,保证跳转目标一定在 pool 内
     *   - id < EnglishRepository.WORD_ID_OFFSET (1000):字母 → 子模式 LETTERS
     *   - id >= 1000:单词 → 子模式 WORDS + 该单词的 difficulty
     * - 把 id 推到队首,loadNextItem 拉出来显示
     * - 如果 id 不在当前 pool(理论上不应发生),静默忽略
     */
    private fun loadItemById(id: Int, lang: String) {
        val targetMode = StudyMode.fromName(lang)
        if (targetMode != currentMode) {
            currentMode = targetMode
            progressStore.saveLanguage(targetMode.name)
            applyModeUi()
        }

        // v1.4.1:英文 id → 同步切子模式 + 难度,避免跳转后 id 不在 pool 静默失败
        if (targetMode == StudyMode.ENGLISH) {
            if (id >= EnglishRepository.WORD_ID_OFFSET) {
                englishSubMode = EnglishSubMode.WORDS
                binding.chipSubWords.isChecked = true
                // 从仓库反查单词的难度
                val word = englishRepository.findByWordById(id)
                if (word != null) {
                    currentDifficulty = word.difficulty
                    when (currentDifficulty) {
                        Difficulty.EASY -> binding.chipEasy.isChecked = true
                        Difficulty.MEDIUM -> binding.chipMedium.isChecked = true
                        Difficulty.HARD -> binding.chipHard.isChecked = true
                    }
                }
            } else {
                englishSubMode = EnglishSubMode.LETTERS
                binding.chipSubLetters.isChecked = true
            }
        }

        rebuildQueue()
        // 把 id 推到队首(ArrayDeque 没有 removeAt,改用 toList+重建)
        val matchIdx = pendingItems.indexOfFirst { it.id == id }
        if (matchIdx > 0) {
            val all = pendingItems.toList()
            pendingItems.clear()
            val found = all[matchIdx]
            pendingQueueAddFirst(found, all, matchIdx)
        } else if (matchIdx < 0) {
            // 不在当前 pool — 静默忽略(避免误跳导致空卡)
            return
        }
        loadNextItem()
    }

    /**
     * 把 all[matchIdx] 推到队首,其余顺序保持
     */
    private fun pendingQueueAddFirst(found: StudyItem, all: List<StudyItem>, matchIdx: Int) {
        pendingItems.addLast(found)
        all.forEachIndexed { i, it -> if (i != matchIdx) pendingItems.addLast(it) }
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
            binding.letterContainer.isVisible = false
            binding.uppercaseText.text = ""
            binding.lowercaseText.text = ""
            binding.wordMeaning.isVisible = false
            binding.wordMeaning.text = ""
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
        val isWord = item is EnglishWordItem
        binding.currentCharacter.isVisible = !isLetter
        binding.letterContainer.isVisible = isLetter
        binding.wordMeaning.isVisible = isWord
        if (isLetter) {
            val letter = (item as EnglishLetterItem).letter
            binding.uppercaseText.text = letter.uppercase
            binding.lowercaseText.text = letter.lowercase
        } else {
            binding.currentCharacter.text = item.primaryText
            if (isWord) {
                // 单词模式下显示中文意思,字号比 currentPinyin 略大,作为显眼释义
                binding.wordMeaning.text = (item as EnglishWordItem).word.chineseMeaning
            }
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
                    // v1.4.1:letter 模式下示例词 chip 点 → 英文示例词 + 中文意思(500ms 停顿)
                    // 其他情况保持原行为(英文 speakEnglish / 中文 char-by-char)
                    if (item is EnglishLetterItem && item.letter.exampleWord.isNotBlank()) {
                        TtsManager.speakSequential(
                            items = buildList {
                                add(entry.word to true)
                                if (item.letter.exampleWordChinese.isNotBlank()) {
                                    add(item.letter.exampleWordChinese to false)
                                }
                            },
                            delayMs = 500,
                            baseUtteranceId = "main_letter_example_${item.id}"
                        )
                    } else {
                        speakWordOrSentence(
                            text = entry.word,
                            pinyin = entry.pinyin,
                            utteranceId = "main_word_${item?.id ?: 0}_$index",
                            isEnglish = isEnglish
                        )
                    }
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
        /** v1.4.2:跳转来源 — 主页收到后可显示"← 返回"按钮跳回源 Activity */
        const val EXTRA_SOURCE_PAGE = "source_page"
        const val SOURCE_PROGRESS = "progress"
        const val SOURCE_LIBRARY = "library"
    }
}