// route.h —— 最快路线算法（Dijkstra / A*）
#ifndef FASTROUTE_ROUTE_H
#define FASTROUTE_ROUTE_H

#include <vector>
#include <limits>
#include <string>

namespace fastroute {

struct Edge {
    int from = 0;
    int to = 0;
    double distance = 0.0; // 米
    double time = 0.0;     // 秒（"最快" 原则的边权）
};

struct Node {
    int id = 0;
    double lat = 0.0;
    double lng = 0.0;
};

enum class WeightMode {
    Distance, // 最短距离
    Time      // 最快时间
};

struct PathResult {
    bool found = false;
    std::vector<int> path;      // 经过的节点 id 序列
    double total_weight = 0.0;  // 按所选模式的累计权重
    WeightMode mode = WeightMode::Time;
};

class Graph {
public:
    void add_node(int id, double lat, double lng);
    void add_edge(int from, int to, double distance, double time);

    const Node* node(int id) const;
    double weight(const Edge& e, WeightMode mode) const;

    // 邻接表：node_id -> (neighbor_id, edge_index)
    const std::vector<int>& neighbors(int id) const;
    const Edge& edge(int index) const;

    size_t node_count() const { return nodes_.size(); }

private:
    std::vector<Node> nodes_;
    std::vector<Edge> edges_;
    std::vector<std::vector<int>> adj_; // 存储 edges_ 的下标
};

// Dijkstra：单源最短路径，返回源点到所有可达点的最短距离与前驱
std::vector<double> dijkstra(const Graph& g, int start, WeightMode mode,
                             std::vector<int>& prev);

// 求 start -> goal 的最快/最短路径
PathResult shortest_path(const Graph& g, int start, int goal, WeightMode mode);

// A*：利用球面距离启发式加速，要求 mode == Distance 或 Time 均可
// （Time 模式下启发式用直线距离除以平均速度近似下界）
PathResult a_star(const Graph& g, int start, int goal, WeightMode mode);

} // namespace fastroute

#endif // FASTROUTE_ROUTE_H