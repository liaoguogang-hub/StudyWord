package com.studyword.literacy.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityMainBinding
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.LearningCharacter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repository = CharacterRepository()
    private lateinit var adapter: CharacterAdapter
    private lateinit var progressStore: ProgressStore

    private val knownIds: MutableSet<Int> = mutableSetOf()
    private val unknownIds: MutableSet<Int> = mutableSetOf()
    private val currentSelections: MutableMap<Int, CharacterResult> = mutableMapOf()
    private var currentDifficulty: Difficulty = Difficulty.EASY
    private var currentBatch: List<LearningCharacter> = emptyList()
    private val random = Random(System.currentTimeMillis())

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { exportProgress(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressStore = ProgressStore(this)
        knownIds.addAll(progressStore.loadKnown())
        unknownIds.addAll(progressStore.loadUnknown())

        adapter = CharacterAdapter { character, result ->
            currentSelections[character.id] = result
        }

        binding.characterRecycler.layoutManager = GridLayoutManager(this, 2)
        binding.characterRecycler.adapter = adapter

        setupDifficultyToggle()
        setupActions()
        updateStats()
        loadNextBatch(resetSelections = true)
    }

    private fun setupDifficultyToggle() {
        binding.difficultyToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentDifficulty = when (checkedId) {
                binding.easyButton.id -> Difficulty.EASY
                binding.mediumButton.id -> Difficulty.MEDIUM
                binding.hardButton.id -> Difficulty.HARD
                else -> Difficulty.EASY
            }
            loadNextBatch(resetSelections = true)
        }
        binding.difficultyToggle.check(binding.easyButton.id)
    }

    private fun setupActions() {
        binding.nextBatchButton.setOnClickListener {
            if (currentBatch.isEmpty()) {
                loadNextBatch(resetSelections = true)
                return@setOnClickListener
            }
            if (currentSelections.keys.containsAll(currentBatch.map { it.id })) {
                persistSelections()
                loadNextBatch(resetSelections = true)
            } else {
                Snackbar.make(binding.root, "请先为本组的每个汉字选择结果", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.resetButton.setOnClickListener {
            knownIds.clear()
            unknownIds.clear()
            progressStore.reset()
            updateStats()
            loadNextBatch(resetSelections = true)
            Snackbar.make(binding.root, "进度已重置", Snackbar.LENGTH_SHORT).show()
        }

        binding.exportButton.setOnClickListener {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            exportLauncher.launch("识字进度_$date.csv")
        }
    }

    private fun loadNextBatch(resetSelections: Boolean) {
        val pool = repository.byDifficulty(currentDifficulty)
        if (pool.isEmpty()) {
            Snackbar.make(binding.root, "当前难度暂无字词，请稍后再试", Snackbar.LENGTH_SHORT).show()
            return
        }

        val notMastered = pool.filter { it.id !in knownIds }
        val candidates = if (notMastered.size >= BATCH_SIZE) {
            notMastered.shuffled(random).take(BATCH_SIZE)
        } else {
            val remaining = (pool - notMastered.toSet()).shuffled(random)
            (notMastered + remaining).take(BATCH_SIZE)
        }

        currentBatch = candidates
        if (resetSelections) {
            currentSelections.clear()
            currentBatch.forEach { character ->
                when {
                    knownIds.contains(character.id) -> currentSelections[character.id] = CharacterResult.KNOWN
                    unknownIds.contains(character.id) -> currentSelections[character.id] = CharacterResult.UNKNOWN
                }
            }
            adapter.updateSelections(currentSelections)
        } else {
            adapter.updateSelections(currentSelections)
        }
        adapter.submitList(currentBatch)
        binding.batchHint.text = if (currentBatch.size == BATCH_SIZE) {
            "每组展示 $BATCH_SIZE 个汉字"
        } else {
            "本组仅有 ${currentBatch.size} 个汉字，已全部呈现"
        }
    }

    private fun persistSelections() {
        currentBatch.forEach { character ->
            when (currentSelections[character.id]) {
                CharacterResult.KNOWN -> {
                    knownIds.add(character.id)
                    unknownIds.remove(character.id)
                }
                CharacterResult.UNKNOWN -> {
                    unknownIds.add(character.id)
                    knownIds.remove(character.id)
                }
                else -> {}
            }
        }
        progressStore.save(knownIds, unknownIds)
        updateStats()
    }

    private fun updateStats() {
        val total = repository.count()
        val known = knownIds.size
        val unknown = unknownIds.size
        val rate = if (total == 0) 0.0 else known * 100.0 / total

        binding.totalCountValue.text = total.toString()
        binding.knownCountValue.text = known.toString()
        binding.unknownCountValue.text = unknown.toString()
        binding.masteryRateValue.text = String.format("%.1f%%", rate)
    }

    private fun exportProgress(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                            writer.appendLine("汉字,拼音,难度,掌握情况")
                            repository.all().forEach { character ->
                                val status = when {
                                    knownIds.contains(character.id) -> "认识"
                                    unknownIds.contains(character.id) -> "不认识"
                                    else -> "未测试"
                                }
                                writer.appendLine(
                                    "${character.hanzi}," +
                                        "${character.pinyin}," +
                                        "${character.difficulty.label}," +
                                        status
                                )
                            }
                        }
                    }
                }
                Toast.makeText(this@MainActivity, "导出成功", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, "导出失败：${error.localizedMessage}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    companion object {
        private const val BATCH_SIZE = 4
    }
}
