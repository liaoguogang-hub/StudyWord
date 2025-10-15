package com.studyword.literacy.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studyword.literacy.R
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityLibraryBinding
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.LearningCharacter

class CharacterLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var repository: CharacterRepository
    private lateinit var progressStore: ProgressStore
    private lateinit var adapter: AllCharactersAdapter

    private val knownIds: MutableSet<Int> = mutableSetOf()
    private val unknownIds: MutableSet<Int> = mutableSetOf()
    private var statusFilter: StatusFilter = StatusFilter.ALL
    private var difficultyFilter: DifficultyFilter = DifficultyFilter.ALL

    private val allCharacters: List<LearningCharacter> by lazy { repository.all() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        progressStore = ProgressStore(this)
        knownIds.addAll(progressStore.loadKnown())
        unknownIds.addAll(progressStore.loadUnknown())

        setupToolbar()
        setupFilters()
        setupRecycler()
        renderList()
    }

    private fun setupToolbar() {
        binding.topBar.setNavigationOnClickListener { finish() }
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
        adapter = AllCharactersAdapter(
            statusProvider = { characterStatus(it) },
            onItemClicked = { character -> showStatusDialog(character) }
        )
        binding.characterRecycler.layoutManager = GridLayoutManager(this, 4)
        binding.characterRecycler.adapter = adapter
    }

    private fun renderList() {
        val filtered = allCharacters.filter { character ->
            matchesDifficulty(character) && matchesStatus(character)
        }
        adapter.submitList(filtered)
    }

    private fun matchesDifficulty(character: LearningCharacter): Boolean = when (difficultyFilter) {
        DifficultyFilter.ALL -> true
        DifficultyFilter.EASY -> character.difficulty == Difficulty.EASY
        DifficultyFilter.MEDIUM -> character.difficulty == Difficulty.MEDIUM
        DifficultyFilter.HARD -> character.difficulty == Difficulty.HARD
    }

    private fun matchesStatus(character: LearningCharacter): Boolean = when (statusFilter) {
        StatusFilter.ALL -> true
        StatusFilter.KNOWN -> knownIds.contains(character.id)
        StatusFilter.UNKNOWN -> unknownIds.contains(character.id)
        StatusFilter.UNSEEN -> !knownIds.contains(character.id) && !unknownIds.contains(character.id)
    }

    private fun characterStatus(character: LearningCharacter): CharacterStatus = when {
        knownIds.contains(character.id) -> CharacterStatus.KNOWN
        unknownIds.contains(character.id) -> CharacterStatus.UNKNOWN
        else -> CharacterStatus.UNSEEN
    }

    private fun showStatusDialog(character: LearningCharacter) {
        val options = arrayOf(
            getString(R.string.status_known),
            getString(R.string.status_unknown),
            getString(R.string.status_unseen)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("修改状态：${character.hanzi}")
            .setItems(options) { dialog, which ->
                val status = when (which) {
                    0 -> CharacterStatus.KNOWN
                    1 -> CharacterStatus.UNKNOWN
                    else -> CharacterStatus.UNSEEN
                }
                updateStatus(character, status)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateStatus(character: LearningCharacter, status: CharacterStatus) {
        when (status) {
            CharacterStatus.KNOWN -> {
                knownIds.add(character.id)
                unknownIds.remove(character.id)
            }
            CharacterStatus.UNKNOWN -> {
                unknownIds.add(character.id)
                knownIds.remove(character.id)
            }
            CharacterStatus.UNSEEN -> {
                knownIds.remove(character.id)
                unknownIds.remove(character.id)
            }
        }
        progressStore.save(knownIds, unknownIds)
        progressStore.recordSnapshot(knownIds.size, unknownIds.size)
        Toast.makeText(this, "已更新 ${character.hanzi} 的状态", Toast.LENGTH_SHORT).show()
        renderList()
    }

    private enum class StatusFilter {
        ALL, KNOWN, UNKNOWN, UNSEEN
    }

    private enum class DifficultyFilter {
        ALL, EASY, MEDIUM, HARD
    }
}
