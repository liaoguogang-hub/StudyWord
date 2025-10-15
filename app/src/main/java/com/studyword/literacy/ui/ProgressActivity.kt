package com.studyword.literacy.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
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
import com.studyword.literacy.R
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.ProgressSnapshot
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityProgressBinding
import com.studyword.literacy.model.Difficulty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressBinding
    private lateinit var repository: CharacterRepository
    private lateinit var progressStore: ProgressStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        progressStore = ProgressStore(this)
        binding.topBar.setNavigationOnClickListener { finish() }

        renderProgress()
    }

    private fun renderProgress() {
        val knownIds = progressStore.loadKnown()
        val unknownIds = progressStore.loadUnknown()
        val characters = repository.all().associateBy { it.id }
        val history = progressStore.loadHistory()

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

        renderTrendChart(history, total)
        renderDifficultyPie(knownIds)
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
        chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this@ProgressActivity, R.color.bubble_pink))
        setTextColor(ContextCompat.getColor(this@ProgressActivity, R.color.deep_blue))
    }

    private fun renderTrendChart(history: List<ProgressSnapshot>, total: Int) {
        val chart = binding.progressLineChart
        val recent = history.takeLast(MAX_TREND_POINTS)
        val dateLabels = mutableListOf<String>()
        val entries = recent.mapIndexed { index, snapshot ->
            val rate = if (total == 0) 0f else snapshot.knownCount * 100f / total
            dateLabels += dateFormatter.format(Date(snapshot.timestamp))
            Entry(index.toFloat(), rate)
        }

        if (entries.size < 2) {
            binding.trendEmptyHint.isVisible = true
            chart.clear()
            chart.isVisible = false
            return
        }

        binding.trendEmptyHint.isVisible = false
        chart.isVisible = true
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            setDrawGridLines(true)
            textColor = Color.DKGRAY
        }
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(false)
            textColor = Color.DKGRAY
            granularity = 1f
            setLabelCount(dateLabels.size, true)
            labelRotationAngle = -30f
            valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val index = value.toInt()
                    return if (index in dateLabels.indices) dateLabels[index] else ""
                }
            }
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
        }

        chart.data = LineData(dataSet)
        chart.invalidate()
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
        private const val MAX_TREND_POINTS = 20
        private val dateFormatter = SimpleDateFormat("MM-dd", Locale.getDefault())
    }
}
