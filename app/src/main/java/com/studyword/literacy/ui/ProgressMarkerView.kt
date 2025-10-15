package com.studyword.literacy.ui

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.studyword.literacy.R

class ProgressMarkerView(
    context: Context,
    private val points: List<ProgressActivity.TrendPoint>
) : MarkerView(context, R.layout.marker_progress) {

    private val dateText: TextView = findViewById(R.id.markerDate)
    private val rateText: TextView = findViewById(R.id.markerRate)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e != null) {
            val index = e.x.toInt()
            if (index in points.indices) {
                val point = points[index]
                dateText.text = point.label
                rateText.text = context.getString(R.string.marker_rate_format, point.rate)
            }
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 16f)
    }
}
