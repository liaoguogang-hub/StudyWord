package com.studyword.literacy.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        setupLanguageToggle()
        setupFilters()
        setupRecycler()
        renderList()
    }

    private fun setupToolbar() {
        binding.topBar.setNavigationOnClickListener { finish() }
    }

    private fun setupLanguageToggle() {
        binding.languageChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentLanguage = when (id) {
                binding.chipLanguageChinese.id -> StudyMode.CHINESE
                binding.chipLanguageEnglish.id -> StudyMode.ENGLISH
                else -> StudyMode.CHINESE
            }
            // 切换 RecyclerView adapter
            swapAdapterForLanguage()
            // 中文模式下显示难度筛选,英文模式隐藏
            binding.difficultyFilterGroup.isVisible = currentLanguage == StudyMode.CHINESE
            // 切语言后默认回到"全部"
            statusFilter = StatusFilter.ALL
            binding.chipFilterAll.isChecked = true
            renderList()
        }
        when (currentLanguage) {
            StudyMode.CHINESE -> binding.chipLanguageChinese.isChecked = true
            StudyMode.ENGLISH -> binding.chipLanguageEnglish.isChecked = true
        }
        binding.difficultyFilterGroup.isVisible = currentLanguage == StudyMode.CHINESE
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
            onItemClicked = { character -> showChineseStatusDialog(ChineseStudyItem(character)) }
        )
        englishAdapter = AllEnglishItemsAdapter(
            statusProvider = { englishStatusOf(it) },
            onItemClicked = { item -> showEnglishStatusDialog(item) }
        )
        // 初始按当前语言装载 adapter
        swapAdapterForLanguage()
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
                val filtered = allEnglishItems.filter { item ->
                    matchesStatusEnglish(item)
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
    // 状态变更 Dialog
    // ============================================================

    private fun showChineseStatusDialog(item: ChineseStudyItem) {
        val options = arrayOf(
            getString(R.string.status_known),
            getString(R.string.status_unknown),
            getString(R.string.status_unseen)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("修改状态：${item.character.hanzi}")
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
}
