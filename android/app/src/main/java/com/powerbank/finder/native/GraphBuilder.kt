package com.powerbank.finder.native

import com.powerbank.finder.model.Station
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 在本地（Kotlin 侧）组装一张小图，交给 C++ 求最快路线。
 * 用于演示"离线 / 本地直接使用 C++ 算法"，无需依赖后端。
 */
object GraphBuilder {

    private const val SPEED = 1.4 // m/s

    private fun distM(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val r = 6371000.0
        val p1 = aLat * PI / 180.0
        val p2 = bLat * PI / 180.0
        val dp = (bLat - aLat) * PI / 180.0
        val dl = (bLng - aLng) * PI / 180.0
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /**
     * 组装一张"用户 -> 两个岔路点 -> 最近站点"的三角路网，
     * 其中一条岔路为慢行路段（time ×2.2），用于体现"最快"原则。
     *
     * @return (图, 目标站点在图中的节点 id)
     */
    fun build(userLat: Double, userLng: Double, stations: List<Station>): Pair<GraphJson, Int> {
        require(stations.isNotEmpty()) { "至少需要一个站点" }
        val nearest = stations.minByOrNull { it.distanceM } ?: stations.first()

        // 两个中间岔路点（在用户与站点连线附近做确定性偏移）
        val midLat = (userLat + nearest.lat) / 2
        val midLng = (userLng + nearest.lng) / 2
        val waypointA = GraphNode(1, midLat + 0.0006, midLng - 0.0003)
        val waypointB = GraphNode(2, midLat - 0.0004, midLng + 0.0005)
        val target = GraphNode(3, nearest.lat, nearest.lng)

        val nodes = listOf(GraphNode(0, userLat, userLng), waypointA, waypointB, target)
        val edges = mutableListOf<GraphEdge>()

        fun add(from: Int, to: Int, slow: Boolean = false) {
            val a = nodes[from]
            val b = nodes[to]
            val d = distM(a.lat, a.lng, b.lat, b.lng)
            val t = if (slow) d / SPEED * 2.2 else d / SPEED
            edges += GraphEdge(from, to, d, t)
        }

        add(0, 1, slow = true)  // 慢行岔路：人流密集
        add(0, 2)
        add(1, 3, slow = true)  // 慢行岔路
        add(2, 3)

        return GraphJson(nodes, edges) to 3
    }
}