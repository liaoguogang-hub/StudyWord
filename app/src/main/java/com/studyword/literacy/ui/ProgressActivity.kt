package com.studyword.literacy.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.studyword.literacy.R
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.EnglishRepository
import com.studyword.literacy.data.ProgressSnapshot
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityProgressBinding
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.EnglishLetterItem
import com.studyword.literacy.model.EnglishWordItem
import com.studyword.literacy.model.StudyItem
import com.studyword.literacy.model.StudyMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * v1.4.0:
 * - 学习进度页"认识汉字 / 待巩固汉字 / 英文 known / 英文 review"四组的 chip 全部可点
 * 点 - 点击 → setResult(EXTRA_SELECTED_ID, EXTRA_SELECTED_LANG) + finish,
 *    MainActivity 通过 pageResultLauncher 接住,loadItemById 跳转到对应卡片
 */
class ProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressBinding
    private lateinit var repository: CharacterRepository
    private lateinit var englishRepository: EnglishRepository
    private lateinit var progressStore: ProgressStore
    private var trendRange: TrendRange = TrendRange.WEEK
    private var initializingRangeToggle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        englishRepository = EnglishRepository(this)
        progressStore = ProgressStore(this)
        binding.topBar.setNavigationOnClickListener { finish() }

        setupTrendRangeToggle()
        setupSwipeBack()
        renderProgress()
    }

    /**
     * v1.4.1:从屏幕左边缘向右滑动 → finish()
     * 与 CharacterLibraryActivity 共用同一套阈值,体感一致。
     */
    private var swipeBackDetector: GestureDetector? = null

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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeBackDetector?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun renderProgress() {
        // ====== 中文 ======
        val knownIds = progressStore.loadKnown()
        val unknownIds = progressStore.loadUnknown()
        val characters = repository.all().associateBy { it.id }
        val history = progressStore.loadHistory()
        val filteredHistory = filterHistory(history)
        val formatter = currentDateFormatter()

        val total = repository.count()
        val known = knownIds.size
        val unknown = unknownIds.size
        val masteryRate = if (total == 0) 0.0 else known * 100.0 / total

        binding.totalCountLabel.text = "字库总数：$total"
        binding.knownCountLabel.text = "认识：$known"
        binding.unknownCountLabel.text = "待巩固：$unknown"
        binding.masteryRateLabel.text = String.format("掌握率：%.1f%%", masteryRate)

        populateChineseChipGroup(
            binding.knownChipGroup,
            binding.knownEmptyHint,
            knownIds.mapNotNull { characters[it] }
        )
        populateChineseChipGroup(
            binding.unknownChipGroup,
            binding.unknownEmptyHint,
            unknownIds.mapNotNull { characters[it] }
        )

        renderTrendChart(filteredHistory, total, formatter)
        renderDifficultyPie(knownIds)

        // ====== 英文 ======
        val enKnownIds = progressStore.loadEnglishKnown()
        val enUnknownIds = progressStore.loadEnglishUnknown()
        val enTotal = englishRepository.count()
        val enKnown = enKnownIds.size
        val enUnknown = enUnknownIds.size
        val enMastery = if (enTotal == 0) 0.0 else enKnown * 100.0 / enTotal

        val letterCount = englishRepository.letterCount()
        val wordCount = englishRepository.wordCount()
        binding.enTotalLabel.text = "Total: $letterCount letters · $wordCount words"
        binding.enKnownLabel.text = "Known: $enKnown"
        binding.enUnknownLabel.text = "Review: $enUnknown"
        binding.enMasteryLabel.text = String.format("Mastery: %.1f%%", enMastery)

        val knownItems = enKnownIds.mapNotNull { id ->
            englishRepository.findByLetterById(id)?.let { EnglishLetterItem(it) }
                ?: englishRepository.findByWordById(id)?.let { EnglishWordItem(it) }
        }
        val unknownItems = enUnknownIds.mapNotNull { id ->
            englishRepository.findByLetterById(id)?.let { EnglishLetterItem(it) }
                ?: englishRepository.findByWordById(id)?.let { EnglishWordItem(it) }
        }
        populateEnglishChipGroup(
            binding.enKnownChipGroup,
            binding.enKnownEmptyHint,
            knownItems
        )
        populateEnglishChipGroup(
            binding.enUnknownChipGroup,
            binding.enUnknownEmptyHint,
            unknownItems
        )
    }

    /**
     * 渲染中文 chip 列表 + 把每个 chip 设为可点(点 → setResult + finish)
     */
    private fun populateChineseChipGroup(
        group: ChipGroup,
        emptyHint: View,
        data: List<com.studyword.literacy.model.LearningCharacter>
    ) {
        group.removeAllViews()
        if (data.isEmpty()) {
            emptyHint.isVisible = true
            group.isVisible = false
            return
        }
        emptyHint.isVisible = false
        group.isVisible = true
        data.sortedBy { it.hanzi }.forEach { character ->
            val chip = createChip(character.hanzi)
            chip.setOnClickListener {
                jumpBackToHome(character.id, StudyMode.CHINESE.name)
            }
            group.addView(chip)
        }
    }

    /**
     * 渲染英文 chip 列表(letter 显示 Aa,word 显示 单词·中文释义)+ 可点
     */
    private fun populateEnglishChipGroup(
        group: ChipGroup,
        emptyHint: View,
        data: List<StudyItem>
    ) {
        group.removeAllViews()
        if (data.isEmpty()) {
            emptyHint.isVisible = true
            group.isVisible = false
            return
        }
        emptyHint.isVisible = false
        group.isVisible = true
        data.sortedBy { renderEnglishLabel(it) }.forEach { item ->
            val chip = createChip(renderEnglishLabel(item))
            chip.setOnClickListener {
                jumpBackToHome(item.id, StudyMode.ENGLISH.name)
            }
            group.addView(chip)
        }
    }

    private fun renderEnglishLabel(item: StudyItem): String = when (item) {
        is EnglishLetterItem -> "${item.letter.uppercase}${item.letter.lowercase}"
        is EnglishWordItem -> "${item.word.word}·${item.word.chineseMeaning}"
        else -> item.primaryText
    }

    /**
     * v1.4.0:跳转回主页特定卡片
     */
    private fun jumpBackToHome(itemId: Int, lang: String) {
        val data = Intent().apply {
            putExtra(MainActivity.EXTRA_SELECTED_ID, itemId)
            putExtra(MainActivity.EXTRA_SELECTED_LANG, lang)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun createChip(text: String): Chip = Chip(this).apply {
        this.text = text
        isCheckable = false
        isClickable = true   // v1.4.0:改为可点
        isCloseIconVisible = false
        setEnsureMinTouchTargetSize(false)
        chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this@ProgressActivity, R.color.bubble_pink))
        setTextColor(ContextCompat.getColor(this@ProgressActivity, R.color.deep_blue))
    }

    private fun renderTrendChart(history: List<ProgressSnapshot>, total: Int, formatter: SimpleDateFormat) {
        val chart = binding.progressLineChart
        val aggregatedPoints = aggregateTrendPoints(history, total, formatter)
        val displayPoints = aggregatedPoints.takeLast(MAX_TREND_POINTS)
        val entries = displayPoints.mapIndexed { index, point ->
            Entry(index.toFloat(), point.rate)
        }
        val dateLabels = displayPoints.map { it.label }

        if (entries.isEmpty()) {
            binding.trendEmptyHint.isVisible = true
            chart.clear()
            chart.isVisible = false
            return
        }

        binding.trendEmptyHint.isVisible = false
        chart.isVisible = true
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.isHighlightPerTapEnabled = true
        chart.axisRight.isEnabled = false
        val maxRate = displayPoints.maxOf { it.rate }
        val minRate = displayPoints.minOf { it.rate }
        val padding = max(5f, (maxRate - minRate) * 0.1f)
        val axisMax = min(100f, maxRate + padding)
        val axisMin = max(0f, minRate - padding)

        chart.axisLeft.apply {
            axisMinimum = axisMin
            axisMaximum = if (axisMax <= axisMin) axisMin + 5f else axisMax
            setDrawGridLines(true)
            textColor = Color.DKGRAY
            granularity = 1f
        }
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(false)
            textColor = Color.DKGRAY
            granularity = 1f
            val step = max(1, dateLabels.size / MAX_LABEL_COUNT)
            setLabelCount(min(dateLabels.size, MAX_LABEL_COUNT), true)
            labelRotationAngle = -30f
            valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val index = value.toInt()
                    return if (index in dateLabels.indices && (index % step == 0 || index == dateLabels.lastIndex)) {
                        dateLabels[index]
                    } else ""
                }
            }
        }

        if (entries.size > VISIBLE_RANGE.toInt()) {
            chart.setVisibleXRangeMaximum(VISIBLE_RANGE)
            chart.moveViewToX(entries.size - VISIBLE_RANGE)
        } else {
            chart.setVisibleXRangeMaximum(entries.size.toFloat())
        }

        val color = ContextCompat.getColor(this, R.color.deep_blue)
        val dataSet = LineDataSet(entries, "掌握率").apply {
            lineWidth = 2.5f
            this.color = color
            setCircleColor(color)
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = color
            fillAlpha = 70
            setDrawHighlightIndicators(false)
            highLightColor = ContextCompat.getColor(this@ProgressActivity, R.color.tangerine)
        }

        chart.data = LineData(dataSet)
        chart.marker = ProgressMarkerView(this, displayPoints)
        chart.invalidate()
    }

    private fun aggregateTrendPoints(
        history: List<ProgressSnapshot>,
        total: Int,
        displayFormatter: SimpleDateFormat
    ): List<TrendPoint> {
        if (history.isEmpty()) return emptyList()
        val sorted = history.sortedBy { it.timestamp }
        val keyFormatter = when (trendRange) {
            TrendRange.YEAR -> SimpleDateFormat("yyyyMM", Locale.getDefault())
            else -> SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        }
        val map = linkedMapOf<String, ProgressSnapshot>()
        sorted.forEach { snapshot ->
            val key = keyFormatter.format(Date(snapshot.timestamp))
            map[key] = snapshot
        }
        return map.values.map { snapshot ->
            val rate = if (total == 0) 0f else snapshot.knownCount * 100f / total
            TrendPoint(
                label = displayFormatter.format(Date(snapshot.timestamp)),
                rate = rate,
                timestamp = snapshot.timestamp
            )
        }
    }

    private fun setupTrendRangeToggle() {
        initializingRangeToggle = true
        binding.trendRangeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            trendRange = when (checkedId) {
                binding.chipRangeWeek.id -> TrendRange.WEEK
                binding.chipRangeMonth.id -> TrendRange.MONTH
                binding.chipRangeYear.id -> TrendRange.YEAR
                else -> TrendRange.WEEK
            }
            renderProgress()
        }
        binding.chipRangeWeek.isChecked = true
        initializingRangeToggle = false
    }

    private fun filterHistory(history: List<ProgressSnapshot>): List<ProgressSnapshot> {
        val now = System.currentTimeMillis()
        val rangeMillis = when (trendRange) {
            TrendRange.WEEK -> DAYS_7
            TrendRange.MONTH -> DAYS_30
            TrendRange.YEAR -> DAYS_365
        }
        val cutoff = now - rangeMillis
        return history.filter { it.timestamp >= cutoff }
    }

    private fun currentDateFormatter(): SimpleDateFormat = when (trendRange) {
        TrendRange.WEEK, TrendRange.MONTH -> SimpleDateFormat("MM-dd", Locale.getDefault())
        TrendRange.YEAR -> SimpleDateFormat("yy-MM", Locale.getDefault())
    }

    private fun renderDifficultyPie(knownIds: Set<Int>) {
        val pieChart = binding.difficultyPieChart
        val counts = Difficulty.values().map { difficulty ->
            val mastered = repository.byDifficulty(difficulty).count { knownIds.contains(it.id) }
            difficulty to mastered
        }
        val totalMastered = counts.sumOf { it.second }

        if (totalMastered == 0) {
            binding.pieEmptyHint.isVisible = true
            pieChart.clear()
            pieChart.isVisible = false
            return
        }

        binding.pieEmptyHint.isVisible = false
        pieChart.isVisible = true
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.setDrawEntryLabels(true)
        pieChart.setEntryLabelColor(Color.DKGRAY)
        pieChart.centerText = "掌握\n按难度"
        pieChart.setCenterTextSize(14f)
        pieChart.holeRadius = 45f
        pieChart.transparentCircleRadius = 50f

        val entries = counts.filter { it.second > 0 }.map { (difficulty, value) ->
            PieEntry(value.toFloat(), difficulty.label)
        }

        val colors = listOf(
            ContextCompat.getColor(this, R.color.sunshine),
            ContextCompat.getColor(this, R.color.bubble_pink),
            ContextCompat.getColor(this, R.color.mint)
        )

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextColor = Color.WHITE
            valueTextSize = 12f
            sliceSpace = 3f
        }

        val data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(pieChart))
        }

        pieChart.data = data
        pieChart.invalidate()
        pieChart.animateY(800)
    }

    companion object {
        private const val MAX_TREND_POINTS = 30
        private const val VISIBLE_RANGE = 8f
        private const val MAX_LABEL_COUNT = 6
        private const val DAYS_7 = 7L * 24 * 60 * 60 * 1000
        private const val DAYS_30 = 30L * 24 * 60 * 60 * 1000
        private const val DAYS_365 = 365L * 24 * 60 * 60 * 1000
        // v1.4.1:边滑返回阈值
        private const val EDGE_THRESHOLD_DP = 24f
        private const val SWIPE_DISTANCE_THRESHOLD_DP = 120f
        private const val SWIPE_VELOCITY_THRESHOLD = 400f
    }

    private enum class TrendRange {
        WEEK,
        MONTH,
        YEAR
    }

    data class TrendPoint(
        val label: String,
        val rate: Float,
        val timestamp: Long
    )
}