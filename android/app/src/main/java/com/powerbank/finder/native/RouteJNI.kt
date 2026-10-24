package com.powerbank.finder.native

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 通过 JNI 调用本地 C++ 最快路线算法（libfastroute.so，见 cpp/CMakeLists.txt）。
 * 与后端 Python 的 ctypes 桥接调用的是同一套 C++ 实现。
 */
object RouteJNI {

    init {
        System.loadLibrary("fastroute")
    }

    // mode: 0 = 最快(time), 1 = 最短(distance)
    private external fun nativeFindRoute(graphJson: String, start: Int, goal: Int, mode: Int): String?

    fun findRoute(graph: GraphJson, start: Int, goal: Int, mode: RouteMode = RouteMode.TIME): RouteResult? {
        val json = Gson().toJson(graph)
        val modeCode = if (mode == RouteMode.DISTANCE) 1 else 0
        val out = nativeFindRoute(json, start, goal, modeCode) ?: return null
        return Gson().fromJson(out, RouteResult::class.java)
    }
}

enum class RouteMode { TIME, DISTANCE }

data class GraphJson(val nodes: List<GraphNode>, val edges: List<GraphEdge>)

data class GraphNode(val id: Int, val lat: Double, val lng: Double)

data class GraphEdge(val from: Int, val to: Int, val distance: Double, val time: Double)

data class RouteResult(
    val found: Boolean,
    val mode: String,
    val path: List<Int>,
    @SerializedName("total_weight") val totalWeight: Double
)