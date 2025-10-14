package com.studyword.literacy.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityProgressBinding

class ProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressBinding
    private val repository = CharacterRepository()
    private lateinit var progressStore: ProgressStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressStore = ProgressStore(this)
        binding.topBar.setNavigationOnClickListener { finish() }

        renderProgress()
    }

    private fun renderProgress() {
        val knownIds = progressStore.loadKnown()
        val unknownIds = progressStore.loadUnknown()
        val characters = repository.all().associateBy { it.id }

        val total = repository.count()
        val known = knownIds.size
        val unknown = unknownIds.size
        val masteryRate = if (total == 0) 0.0 else known * 100.0 / total

        binding.totalCountLabel.text = "字库总数：$total"
        binding.knownCountLabel.text = "认识：$known"
        binding.unknownCountLabel.text = "待巩固：$unknown"
        binding.masteryRateLabel.text = String.format("掌握率：%.1f%%", masteryRate)

        populateChipGroup(
            binding.knownChipGroup,
            binding.knownEmptyHint,
            knownIds.mapNotNull { characters[it]?.hanzi }
        )
        populateChipGroup(
            binding.unknownChipGroup,
            binding.unknownEmptyHint,
            unknownIds.mapNotNull { characters[it]?.hanzi }
        )
    }

    private fun populateChipGroup(
        group: com.google.android.material.chip.ChipGroup,
        emptyHint: View,
        data: List<String>
    ) {
        group.removeAllViews()
        if (data.isEmpty()) {
            emptyHint.isVisible = true
            group.isVisible = false
            return
        }
        emptyHint.isVisible = false
        group.isVisible = true

        data.sorted().forEach { text ->
            group.addView(createChip(text))
        }
    }

    private fun createChip(text: String): Chip = Chip(this).apply {
        this.text = text
        isCheckable = false
        isClickable = false
        isCloseIconVisible = false
        setEnsureMinTouchTargetSize(false)
    }
}
