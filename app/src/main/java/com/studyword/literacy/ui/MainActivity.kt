package com.studyword.literacy.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repository = CharacterRepository()
    private lateinit var progressStore: ProgressStore

    private val knownIds: MutableSet<Int> = mutableSetOf()
    private val unknownIds: MutableSet<Int> = mutableSetOf()
    private var currentDifficulty: Difficulty = Difficulty.EASY
    private val pendingCharacters: ArrayDeque<LearningCharacter> = ArrayDeque()
    private var currentCharacter: LearningCharacter? = null
    private val random = Random(System.currentTimeMillis())
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

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
        rebuildQueue()
        loadNextCharacter()
    }

    override fun onDestroy() {
        toneGenerator.release()
        super.onDestroy()
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
        binding.knowButton.setOnClickListener { handleResult(CharacterResult.KNOWN) }
        binding.unknownButton.setOnClickListener { handleResult(CharacterResult.UNKNOWN) }
        binding.skipButton.setOnClickListener { loadNextCharacter(requeueCurrent = true) }
        binding.resetButton.setOnClickListener {
            knownIds.clear()
            unknownIds.clear()
            progressStore.reset()
            rebuildQueue()
            loadNextCharacter()
            Snackbar.make(binding.root, "进度已重置", Snackbar.LENGTH_SHORT).show()
        }
        binding.exportButton.setOnClickListener {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            exportLauncher.launch("识字进度_$date.csv")
        }
        binding.viewProgressButton.setOnClickListener {
            startActivity(Intent(this, ProgressActivity::class.java))
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
                loadNextCharacter()
            }
        }
        progressStore.save(knownIds, unknownIds)
        updateSummaryHint()
    }

    private fun celebrate() {
        setActionButtonsEnabled(false)
        playTone()
        showConfetti {
            loadNextCharacter()
            setActionButtonsEnabled(true)
        }
    }

    private fun playTone() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
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
        updateSummaryHint()
    }

    private fun updateCurrentCharacterView(character: LearningCharacter?) {
        if (character == null) {
            binding.currentCharacter.text = "——"
            binding.currentPinyin.text = "暂无汉字"
            binding.currentDifficulty.isVisible = false
            binding.remainingHint.text = "暂无可测汉字，请调整难度或重置进度"
            setActionButtonsEnabled(false)
            return
        }

        binding.currentCharacter.text = character.hanzi
        binding.currentPinyin.text = character.pinyin.ifBlank { "(暂无拼音)" }
        binding.currentDifficulty.text = character.difficulty.label
        binding.currentDifficulty.isVisible = true
        setActionButtonsEnabled(true)
    }

    private fun updateSummaryHint() {
        val total = repository.count()
        val known = knownIds.size
        val unknown = unknownIds.size
        val untested = total - known - unknown
        binding.remainingHint.text =
            "已认识 $known / $total · 待巩固 $unknown · 未测 ${untested.coerceAtLeast(0)}"
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        binding.knowButton.isEnabled = enabled
        binding.unknownButton.isEnabled = enabled
        binding.skipButton.isEnabled = enabled
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
        private const val CONFETTI_COUNT = 18
        private const val CONFETTI_DURATION_MS = 900L
        private val CONFETTI_EMOJIS = listOf("🎉", "✨", "🎈", "🎊", "🌟", "💫")
    }
}
