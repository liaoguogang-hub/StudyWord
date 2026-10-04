package com.studyword.literacy.ui

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studyword.literacy.R
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.EnglishRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityLibraryBinding
import com.studyword.literacy.model.ChineseStudyItem
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.EnglishLetterItem
import com.studyword.literacy.model.EnglishWordItem
import com.studyword.literacy.model.StudyItem
import com.studyword.literacy.model.StudyMode
import kotlin.math.abs

/**
 * 字库浏览(v1.4.0):
 * - 短按 item → setResult(selectedId, lang) + finish,跳回主页对应卡片
 * - 长按 item → 弹状态对话框(原行为,标记 known/unknown/unseen)
 * - 通过 pageResultLauncher(在 MainActivity 中)接住跳转回值
 */
class CharacterLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var repository: CharacterRepository
    private lateinit var englishRepository: EnglishRepository
    private lateinit var progressStore: ProgressStore

    private var currentLanguage: StudyMode = StudyMode.CHINESE
    private var statusFilter: StatusFilter = StatusFilter.ALL
    private var difficultyFilter: DifficultyFilter = DifficultyFilter.ALL

    // ====== 中文维度 ======
    private var chineseKnownIds: MutableSet<Int> = mutableSetOf()
    private var chineseUnknownIds: MutableSet<Int> = mutableSetOf()
    private lateinit var chineseAdapter: AllCharactersAdapter

    // ====== 英文维度 ======
    private var englishKnownIds: MutableSet<Int> = mutableSetOf()
    private var englishUnknownIds: MutableSet<Int> = mutableSetOf()
    private lateinit var englishAdapter: AllEnglishItemsAdapter

    private val allChineseItems: List<StudyItem> by lazy {
        repository.all().map { ChineseStudyItem(it) }
    }
    private val allEnglishItems: List<StudyItem> by lazy {
        englishRepository.letters().map { EnglishLetterItem(it) } +
            englishRepository.words().map { EnglishWordItem(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        englishRepository = EnglishRepository(this)
        progressStore = ProgressStore(this)

        currentLanguage = StudyMode.fromName(progressStore.loadLanguage())

        chineseKnownIds = progressStore.loadKnown()
        chineseUnknownIds = progressStore.loadUnknown()
        englishKnownIds = progressStore.loadEnglishKnown()
        englishUnknownIds = progressStore.loadEnglishUnknown()

        setupToolbar()
        setupSwipeBack()
        setupRecycler()
        setupLanguageToggle()
        setupFilters()
        renderList()
    }

    private fun setupToolbar() {
        binding.topBar.setNavigationOnClickListener { finish() }
    }

    /**
     * v1.4.1:从屏幕左边缘向右滑动超过 120dp 且横向速度 > 纵向速度 2 倍 → finish()
     * 不影响 RecyclerView 上下滚动、chip 点击等其他手势。
     */
    private fun setupSwipeBack() {
        val density = resources.displayMetrics.density
        val edgePx = EDGE_THRESHOLD_DP * density
        val distancePx = SWIPE_DISTANCE_THRESHOLD_DP * density
        swipeBackDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val startsAtLeftEdge = e1.x <= edgePx
                val longEnough = dx >= distancePx
                val mostlyHorizontal = abs(dx) > abs(dy) * 2
                val fastEnough = velocityX > SWIPE_VELOCITY_THRESHOLD
                if (startsAtLeftEdge && longEnough && mostlyHorizontal && fastEnough) {
                    finish()
                    return true
                }
                return false
            }
        })
    }

    private var swipeBackDetector: GestureDetector? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeBackDetector?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun setupLanguageToggle() {
        binding.languageChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentLanguage = when (id) {
                binding.chipLanguageChinese.id -> StudyMode.CHINESE
                binding.chipLanguageEnglish.id -> StudyMode.ENGLISH
                else -> StudyMode.CHINESE
            }
            swapAdapterForLanguage()
            // v1.4.3:难度 filter 始终显示 — 英文 mode 下按 word 难度过滤(letter 始终展示)
            binding.difficultyFilterGroup.isVisible = true
            statusFilter = StatusFilter.ALL
            binding.chipFilterAll.isChecked = true
            renderList()
        }
        when (currentLanguage) {
            StudyMode.CHINESE -> binding.chipLanguageChinese.isChecked = true
            StudyMode.ENGLISH -> binding.chipLanguageEnglish.isChecked = true
        }
        binding.difficultyFilterGroup.isVisible = true
    }

    private fun swapAdapterForLanguage() {
        when (currentLanguage) {
            StudyMode.CHINESE -> {
                binding.characterRecycler.adapter = chineseAdapter
                binding.characterRecycler.layoutManager = GridLayoutManager(this, 4)
            }
            StudyMode.ENGLISH -> {
                binding.characterRecycler.adapter = englishAdapter
                binding.characterRecycler.layoutManager = GridLayoutManager(this, 4)
            }
        }
    }

    private fun setupFilters() {
        binding.statusFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.chipFilterKnown.id -> statusFilter = StatusFilter.KNOWN
                binding.chipFilterUnknown.id -> statusFilter = StatusFilter.UNKNOWN
                binding.chipFilterUnseen.id -> statusFilter = StatusFilter.UNSEEN
                else -> statusFilter = StatusFilter.ALL
            }
            renderList()
        }
        binding.chipFilterAll.isChecked = true

        binding.difficultyFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.chipDifficultyEasy.id -> difficultyFilter = DifficultyFilter.EASY
                binding.chipDifficultyMedium.id -> difficultyFilter = DifficultyFilter.MEDIUM
                binding.chipDifficultyHard.id -> difficultyFilter = DifficultyFilter.HARD
                else -> difficultyFilter = DifficultyFilter.ALL
            }
            renderList()
        }
        binding.chipDifficultyAll.isChecked = true
    }

    private fun setupRecycler() {
        chineseAdapter = AllCharactersAdapter(
            statusProvider = { character -> chineseStatusOf(character.id) },
            onItemClicked = { character -> jumpBackToHome(ChineseStudyItem(character)) },
            onItemLongClicked = { character -> showLongPressDialog(ChineseStudyItem(character)) }
        )
        englishAdapter = AllEnglishItemsAdapter(
            statusProvider = { englishStatusOf(it) },
            onItemClicked = { item -> jumpBackToHome(item) },
            onItemLongClicked = { item -> showLongPressDialog(item) }
        )
        swapAdapterForLanguage()
    }

    /**
     * v1.4.0:短按 item → setResult + finish,跳回主页对应卡片
     * v1.4.2:同时把 source=library 发回去,主页可显示"← 返回字库"按钮
     * v1.4.4:同时把 clear_source=true 发回去 — 这是用户主动从源页短按 chip 跳回主页,
     * 主页接收后清掉 jumpSource,不显示"← 返回字库"按钮
     */
    private fun jumpBackToHome(item: StudyItem) {
        val lang = when (item) {
            is ChineseStudyItem -> StudyMode.CHINESE.name
            is EnglishLetterItem, is EnglishWordItem -> StudyMode.ENGLISH.name
        }
        val data = Intent().apply {
            putExtra(MainActivity.EXTRA_SELECTED_ID, item.id)
            putExtra(MainActivity.EXTRA_SELECTED_LANG, lang)
            putExtra(MainActivity.EXTRA_SOURCE_PAGE, MainActivity.SOURCE_LIBRARY)
            putExtra(MainActivity.EXTRA_CLEAR_SOURCE, true)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    /**
     * 长按 item → 弹状态对话框
     */
    private fun showLongPressDialog(item: StudyItem) {
        when (item) {
            is ChineseStudyItem -> showChineseStatusDialog(item)
            else -> showEnglishStatusDialog(item)
        }
    }

    // ============================================================
    // 渲染
    // ============================================================

    private fun renderList() {
        when (currentLanguage) {
            StudyMode.CHINESE -> {
                val filtered = allChineseItems
                    .filter { item -> matchesDifficulty(item) && matchesStatus(item) }
                    .map { (it as ChineseStudyItem).character }
                chineseAdapter.submitList(filtered)
            }
            StudyMode.ENGLISH -> {
                // v1.4.3:难度 filter 也应用到英文 — letter 不过滤(始终),word 按 difficulty 过滤
                val filtered = allEnglishItems.filter { item ->
                    matchesDifficultyEnglish(item) && matchesStatusEnglish(item)
                }
                englishAdapter.submitList(filtered)
            }
        }
    }

    private fun matchesDifficulty(item: StudyItem): Boolean {
        if (item !is ChineseStudyItem) return true
        return when (difficultyFilter) {
            DifficultyFilter.ALL -> true
            DifficultyFilter.EASY -> item.character.difficulty == Difficulty.EASY
            DifficultyFilter.MEDIUM -> item.character.difficulty == Difficulty.MEDIUM
            DifficultyFilter.HARD -> item.character.difficulty == Difficulty.HARD
        }
    }

    /**
     * v1.4.3:难度 filter 也用于英文 mode。
     * - EnglishLetterItem:letter 始终显示(难度对它无意义)
     * - EnglishWordItem:按 word.difficulty 过滤
     * - 其他(理论不应出现):全量通过
     */
    private fun matchesDifficultyEnglish(item: StudyItem): Boolean {
        return when (item) {
            is EnglishLetterItem -> true
            is EnglishWordItem -> when (difficultyFilter) {
                DifficultyFilter.ALL -> true
                DifficultyFilter.EASY -> item.word.difficulty == Difficulty.EASY
                DifficultyFilter.MEDIUM -> item.word.difficulty == Difficulty.MEDIUM
                DifficultyFilter.HARD -> item.word.difficulty == Difficulty.HARD
            }
            else -> true
        }
    }

    private fun matchesStatus(item: StudyItem): Boolean {
        val isKnown = chineseKnownIds.contains(item.id)
        val isUnknown = chineseUnknownIds.contains(item.id)
        return when (statusFilter) {
            StatusFilter.ALL -> true
            StatusFilter.KNOWN -> isKnown
            StatusFilter.UNKNOWN -> isUnknown
            StatusFilter.UNSEEN -> !isKnown && !isUnknown
        }
    }

    private fun matchesStatusEnglish(item: StudyItem): Boolean {
        val isKnown = englishKnownIds.contains(item.id)
        val isUnknown = englishUnknownIds.contains(item.id)
        return when (statusFilter) {
            StatusFilter.ALL -> true
            StatusFilter.KNOWN -> isKnown
            StatusFilter.UNKNOWN -> isUnknown
            StatusFilter.UNSEEN -> !isKnown && !isUnknown
        }
    }

    private fun chineseStatusOf(id: Int): CharacterStatus = when {
        chineseKnownIds.contains(id) -> CharacterStatus.KNOWN
        chineseUnknownIds.contains(id) -> CharacterStatus.UNKNOWN
        else -> CharacterStatus.UNSEEN
    }

    private fun englishStatusOf(item: StudyItem): CharacterStatus = when {
        englishKnownIds.contains(item.id) -> CharacterStatus.KNOWN
        englishUnknownIds.contains(item.id) -> CharacterStatus.UNKNOWN
        else -> CharacterStatus.UNSEEN
    }

    // ============================================================
    // 状态变更 Dialog(长按)
    // ============================================================

    private fun showChineseStatusDialog(item: ChineseStudyItem) {
        val options = arrayOf(
            getString(R.string.status_known),
            getString(R.string.status_unknown),
            getString(R.string.status_unseen)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("修改状态:${item.character.hanzi}")
            .setItems(options) { dialog, which ->
                val status = when (which) {
                    0 -> CharacterStatus.KNOWN
                    1 -> CharacterStatus.UNKNOWN
                    else -> CharacterStatus.UNSEEN
                }
                updateChineseStatus(item, status)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEnglishStatusDialog(item: StudyItem) {
        val label = when (item) {
            is EnglishLetterItem -> "${item.letter.uppercase}${item.letter.lowercase}"
            is EnglishWordItem -> item.word.word
            else -> item.primaryText
        }
        val options = arrayOf(
            getString(R.string.status_known),
            getString(R.string.status_unknown),
            getString(R.string.status_unseen)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Update: $label")
            .setItems(options) { dialog, which ->
                val status = when (which) {
                    0 -> CharacterStatus.KNOWN
                    1 -> CharacterStatus.UNKNOWN
                    else -> CharacterStatus.UNSEEN
                }
                updateEnglishStatus(item.id, status)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateChineseStatus(item: ChineseStudyItem, status: CharacterStatus) {
        when (status) {
            CharacterStatus.KNOWN -> {
                chineseKnownIds.add(item.id)
                chineseUnknownIds.remove(item.id)
            }
            CharacterStatus.UNKNOWN -> {
                chineseUnknownIds.add(item.id)
                chineseKnownIds.remove(item.id)
            }
            CharacterStatus.UNSEEN -> {
                chineseKnownIds.remove(item.id)
                chineseUnknownIds.remove(item.id)
            }
        }
        progressStore.save(chineseKnownIds, chineseUnknownIds)
        progressStore.recordSnapshot(chineseKnownIds.size, chineseUnknownIds.size)
        Toast.makeText(this, "已更新 ${item.character.hanzi} 的状态", Toast.LENGTH_SHORT).show()
        renderList()
    }

    private fun updateEnglishStatus(id: Int, status: CharacterStatus) {
        when (status) {
            CharacterStatus.KNOWN -> {
                englishKnownIds.add(id)
                englishUnknownIds.remove(id)
            }
            CharacterStatus.UNKNOWN -> {
                englishUnknownIds.add(id)
                englishKnownIds.remove(id)
            }
            CharacterStatus.UNSEEN -> {
                englishKnownIds.remove(id)
                englishUnknownIds.remove(id)
            }
        }
        progressStore.saveEnglish(englishKnownIds, englishUnknownIds)
        Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show()
        renderList()
    }

    private enum class StatusFilter {
        ALL, KNOWN, UNKNOWN, UNSEEN
    }

    private enum class DifficultyFilter {
        ALL, EASY, MEDIUM, HARD
    }

    companion object {
        /** 边缘触发区宽度(dp) */
        private const val EDGE_THRESHOLD_DP = 24f
        /** 最小滑动距离(dp) */
        private const val SWIPE_DISTANCE_THRESHOLD_DP = 120f
        /** 最小横向速度(px/s) */
        private const val SWIPE_VELOCITY_THRESHOLD = 400f
    }
}