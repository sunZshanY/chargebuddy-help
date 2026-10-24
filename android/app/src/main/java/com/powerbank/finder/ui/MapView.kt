package com.powerbank.finder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.powerbank.finder.model.PathCoord
import com.powerbank.finder.model.Station
import kotlin.math.max
import kotlin.math.min

/**
 * 轻量地图视图：不依赖任何地图 SDK，按经纬度相对位置绘制
 * 用户位置、充电宝站点（有货/无货着色）以及最快路线（橙色折线）。
 * 适用于 Demo 演示，真实产品可替换为高德/百度/腾讯地图渲染层。
 */
class MapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var stations: List<Station> = emptyList()
    private var userLat = 0.0
    private var userLng = 0.0
    private var route: List<PathCoord> = emptyList()

    private val userPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A73E8"); style = Paint.Style.FILL
    }
    private val availPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#34A853"); style = Paint.Style.FILL
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EA4335"); style = Paint.Style.FILL
    }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6D00"); style = Paint.Style.STROKE; strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333"); textSize = 26f
    }

    fun setData(userLat: Double, userLng: Double, stations: List<Station>, route: List<PathCoord>) {
        this.userLat = userLat
        this.userLng = userLng
        this.stations = stations
        this.route = route
        invalidate()
    }

    private fun bounds(): FloatArray {
        var minLat = userLat; var maxLat = userLat
        var minLng = userLng; var maxLng = userLng
        for (s in stations) {
            minLat = min(minLat, s.lat); maxLat = max(maxLat, s.lat)
            minLng = min(minLng, s.lng); maxLng = max(maxLng, s.lng)
        }
        return floatArrayOf(minLat, maxLat, minLng, maxLng)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 60f
        val w = width - pad * 2
        val h = height - pad * 2
        if (w <= 0 || h <= 0) return

        val (minLat, maxLat, minLng, maxLng) = bounds()
        val spanLat = (maxLat - minLat).let { if (it < 1e-6) 1e-6 else it }
        val spanLng = (maxLng - minLng).let { if (it < 1e-6) 1e-6 else it }

        fun project(lat: Double, lng: Double): PointF {
            val x = pad + ((lng - minLng) / spanLng * w).toFloat()
            val y = pad + ((maxLat - lat) / spanLat * h).toFloat()
            return PointF(x, y)
        }

        // 路线
        if (route.size >= 2) {
            val pts = route.map { project(it.lat, it.lng) }
            for (i in 0 until pts.size - 1) {
                canvas.drawLine(pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y, routePaint)
            }
        }

        // 站点
        for (s in stations) {
            val p = project(s.lat, s.lng)
            val paint = if (s.available > 0) availPaint else emptyPaint
            canvas.drawCircle(p.x, p.y, 18f, paint)
        }

        // 用户
        val u = project(userLat, userLng)
        canvas.drawCircle(u.x, u.y, 20f, userPaint)
        canvas.drawText("我", u.x - 16f, u.y - 26f, labelPaint)
    }
}