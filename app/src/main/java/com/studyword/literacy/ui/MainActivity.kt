package com.studyword.literacy.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repository = CharacterRepository()
    private val characterMap = repository.all().associateBy { it.id }
    private lateinit var progressStore: ProgressStore

    private val knownIds: MutableSet<Int> = mutableSetOf()
    private val unknownIds: MutableSet<Int> = mutableSetOf()
    private var currentDifficulty: Difficulty = Difficulty.EASY
    private val pendingCharacters: ArrayDeque<LearningCharacter> = ArrayDeque()
    private var currentCharacter: LearningCharacter? = null
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

        setupDifficultyToggle()
        setupActions()
        updateStatsAndLists()
        rebuildQueue()
        loadNextCharacter()
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
            pendingCharacters.clear()
            currentCharacter = null
            rebuildQueue()
            loadNextCharacter()
        }
        binding.difficultyToggle.check(binding.easyButton.id)
    }

    private fun setupActions() {
        binding.knowButton.setOnClickListener {
            handleResult(CharacterResult.KNOWN)
        }

        binding.unknownButton.setOnClickListener {
            handleResult(CharacterResult.UNKNOWN)
        }

        binding.skipButton.setOnClickListener {
            loadNextCharacter(requeueCurrent = true)
        }

        binding.resetButton.setOnClickListener {
            knownIds.clear()
            unknownIds.clear()
            progressStore.reset()
            updateStatsAndLists()
            rebuildQueue()
            loadNextCharacter()
            Snackbar.make(binding.root, "进度已重置", Snackbar.LENGTH_SHORT).show()
        }

        binding.exportButton.setOnClickListener {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            exportLauncher.launch("识字进度_$date.csv")
        }
    }

    private fun handleResult(result: CharacterResult) {
        val character = currentCharacter ?: return
        when (result) {
            CharacterResult.KNOWN -> {
                knownIds.add(character.id)
                unknownIds.remove(character.id)
            }
            CharacterResult.UNKNOWN -> {
                unknownIds.add(character.id)
                knownIds.remove(character.id)
                pendingCharacters.addLast(character)
            }
        }
        progressStore.save(knownIds, unknownIds)
        updateStatsAndLists()

        if (result == CharacterResult.KNOWN) {
            setActionButtonsEnabled(false)
            showConfetti()
            binding.root.postDelayed({
                loadNextCharacter()
            }, CONFETTI_DELAY_MS)
        } else {
            loadNextCharacter()
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
        val mastered = pool.filter { knownIds.contains(it.id) && !unknownIds.contains(it.id) }

        pendingCharacters.addAll(needReview.shuffled(random))
        pendingCharacters.addAll(untested.shuffled(random))
        pendingCharacters.addAll(mastered.shuffled(random))
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
    }

    private fun updateCurrentCharacterView(character: LearningCharacter?) {
        if (character == null) {
            binding.currentCharacter.text = "——"
            binding.currentPinyin.text = "暂无汉字"
            binding.currentDifficulty.text = ""
            binding.currentDifficulty.isVisible = false
            binding.remainingHint.text = "请选择其他难度或重置进度"
            setActionButtonsEnabled(false)
            return
        }

        binding.currentCharacter.text = character.hanzi
        binding.currentPinyin.text = character.pinyin.ifBlank { "(暂无拼音)" }
        binding.currentDifficulty.text = character.difficulty.label
        binding.currentDifficulty.isVisible = true
        updateRemainingHint()
        setActionButtonsEnabled(true)
    }

    private fun updateRemainingHint() {
        val pool = repository.byDifficulty(currentDifficulty)
        val untestedCount = pool.count { it.id !in knownIds && it.id !in unknownIds }
        val reviewCount = pool.count { unknownIds.contains(it.id) }
        binding.remainingHint.text = "未测 ${untestedCount} 个 · 待巩固 ${reviewCount} 个"
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        binding.knowButton.isEnabled = enabled
        binding.unknownButton.isEnabled = enabled
        binding.skipButton.isEnabled = enabled
    }

    private fun updateStatsAndLists() {
        val total = repository.count()
        val known = knownIds.size
        val unknown = unknownIds.size
        val rate = if (total == 0) 0.0 else known * 100.0 / total

        binding.totalCountValue.text = total.toString()
        binding.knownCountValue.text = known.toString()
        binding.unknownCountValue.text = unknown.toString()
        binding.masteryRateValue.text = String.format("%.1f%%", rate)

        populateChipGroup(
            binding.knownChipGroup,
            binding.knownEmptyHint,
            knownIds
        )
        populateChipGroup(
            binding.unknownChipGroup,
            binding.unknownEmptyHint,
            unknownIds
        )
    }

    private fun populateChipGroup(
        group: ChipGroup,
        emptyHint: View,
        ids: Set<Int>
    ) {
        group.removeAllViews()

        if (ids.isEmpty()) {
            emptyHint.isVisible = true
            group.isVisible = false
            return
        }

        emptyHint.isVisible = false
        group.isVisible = true

        val characters = ids.mapNotNull { characterMap[it] }
            .sortedBy { it.hanzi }

        val display = characters.take(MAX_DISPLAY_CHARS)
        display.forEach { character ->
            group.addView(createChip(character.hanzi))
        }

        if (characters.size > MAX_DISPLAY_CHARS) {
            group.addView(createChip("+${characters.size - MAX_DISPLAY_CHARS}"))
        }
    }

    private fun createChip(text: String): Chip {
        return Chip(this).apply {
            this.text = text
            isCheckable = false
            isClickable = false
            isCloseIconVisible = false
            setEnsureMinTouchTargetSize(false)
        }
    }

    private fun showConfetti() {
        val overlay = binding.confettiOverlay
        val width = overlay.width
        val height = overlay.height
        if (width == 0 || height == 0) {
            overlay.post { showConfetti() }
            return
        }

        repeat(CONFETTI_COUNT) { index ->
            val emoji = CONFETTI_EMOJIS[index % CONFETTI_EMOJIS.size]
            val textView = TextView(this).apply {
                text = emoji
                textSize = random.nextInt(18, 32).toFloat()
                alpha = 0f
            }
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            overlay.addView(textView, params)

            val startX = random.nextInt(width)
            val startY = -random.nextInt(height / 3 + 1)
            val endY = height + random.nextInt(height / 4 + 1)
            val amplitude = random.nextInt(width / 5 + 1)
            val phase = random.nextInt(4, 8)
            val rotationRange = random.nextInt(90, 220)

            textView.translationX = startX.toFloat()
            textView.translationY = startY.toFloat()

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = random.nextLong(1100L, 1700L)
                interpolator = LinearInterpolator()
                addUpdateListener { valueAnimator ->
                    val fraction = valueAnimator.animatedValue as Float
                    val currentY = startY + (endY - startY) * fraction
                    val drift = amplitude * sin(fraction * phase * PI).toFloat()
                    textView.translationX = startX + drift
                    textView.translationY = currentY
                    textView.rotation = rotationRange * (fraction - 0.5f)
                    textView.alpha = when {
                        fraction < 0.1f -> fraction / 0.1f
                        fraction > 0.85f -> (1f - fraction) / 0.15f
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
        }
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
                Toast.makeText(
                    this@MainActivity,
                    "导出失败：${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val MAX_DISPLAY_CHARS = 40
        private const val CONFETTI_COUNT = 14
        private const val CONFETTI_DELAY_MS = 600L
        private val CONFETTI_EMOJIS = listOf("🎉", "✨", "🎈", "🎊", "🌟")
    }
}
