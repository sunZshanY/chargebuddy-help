#include "route.h"

#include <queue>
#include <unordered_map>
#include <cmath>
#include <algorithm>

namespace fastroute {

namespace {
constexpr double kPi = 3.14159265358979323846;
constexpr double kEarthRadiusM = 6371000.0;

double haversine_m(double lat1, double lng1, double lat2, double lng2) {
    const double dlat = (lat2 - lat1) * kPi / 180.0;
    const double dlng = (lng2 - lng1) * kPi / 180.0;
    const double a = std::sin(dlat / 2) * std::sin(dlat / 2) +
                     std::cos(lat1 * kPi / 180.0) * std::cos(lat2 * kPi / 180.0) *
                         std::sin(dlng / 2) * std::sin(dlng / 2);
    return 2.0 * kEarthRadiusM * std::asin(std::sqrt(a));
}
} // namespace

void Graph::add_node(int id, double lat, double lng) {
    if ((size_t)id >= nodes_.size()) {
        nodes_.resize(id + 1);
        adj_.resize(id + 1);
    }
    nodes_[id] = Node{id, lat, lng};
}

void Graph::add_edge(int from, int to, double distance, double time) {
    if ((size_t)from >= adj_.size()) adj_.resize(from + 1);
    if ((size_t)to >= adj_.size()) adj_.resize(to + 1);
    int idx = (int)edges_.size();
    edges_.push_back(Edge{from, to, distance, time});
    adj_[from].push_back(idx);
    // 无向图：同时建立反向边
    edges_.push_back(Edge{to, from, distance, time});
    adj_[to].push_back(idx + 1);
}

const Node* Graph::node(int id) const {
    if (id < 0 || (size_t)id >= nodes_.size()) return nullptr;
    return &nodes_[id];
}

double Graph::weight(const Edge& e, WeightMode mode) const {
    return mode == WeightMode::Distance ? e.distance : e.time;
}

const std::vector<int>& Graph::neighbors(int id) const {
    static const std::vector<int> empty;
    if (id < 0 || (size_t)id >= adj_.size()) return empty;
    return adj_[id];
}

const Edge& Graph::edge(int index) const { return edges_[index]; }

std::vector<double> dijkstra(const Graph& g, int start, WeightMode mode,
                             std::vector<int>& prev) {
    const size_t n = g.node_count();
    std::vector<double> dist(n, std::numeric_limits<double>::infinity());
    prev.assign(n, -1);
    if (g.node(start) == nullptr) return dist;

    using P = std::pair<double, int>; // (distance, node)
    std::priority_queue<P, std::vector<P>, std::greater<P>> pq;
    dist[start] = 0.0;
    pq.push({0.0, start});

    while (!pq.empty()) {
        auto [d, u] = pq.top();
        pq.pop();
        if (d > dist[u]) continue;
        for (int eidx : g.neighbors(u)) {
            const Edge& e = g.edge(eidx);
            double nd = d + g.weight(e, mode);
            if (nd < dist[e.to]) {
                dist[e.to] = nd;
                prev[e.to] = u;
                pq.push({nd, e.to});
            }
        }
    }
    return dist;
}

PathResult shortest_path(const Graph& g, int start, int goal, WeightMode mode) {
    PathResult r;
    r.mode = mode;
    if (g.node(start) == nullptr || g.node(goal) == nullptr) return r;

    std::vector<int> prev;
    std::vector<double> dist = dijkstra(g, start, mode, prev);
    if (dist[goal] == std::numeric_limits<double>::infinity()) return r;

    r.found = true;
    r.total_weight = dist[goal];
    for (int cur = goal; cur != -1; cur = prev[cur]) {
        r.path.push_back(cur);
    }
    std::reverse(r.path.begin(), r.path.end());
    return r;
}

PathResult a_star(const Graph& g, int start, int goal, WeightMode mode) {
    PathResult r;
    r.mode = mode;
    const Node* s = g.node(start);
    const Node* t = g.node(goal);
    if (s == nullptr || t == nullptr) return r;

    const size_t n = g.node_count();
    std::vector<double> g_score(n, std::numeric_limits<double>::infinity());
    std::vector<int> prev(n, -1);
    std::vector<bool> closed(n, false);

    // 启发式：球面直线距离；Time 模式下按平均步行速度 1.4 m/s 换算为时间下界
    auto heuristic = [&](int a) -> double {
        const Node* na = g.node(a);
        double h_m = haversine_m(na->lat, na->lng, t->lat, t->lng);
        if (mode == WeightMode::Time) return h_m / 1.4;
        return h_m;
    };

    using P = std::pair<double, int>;
    std::priority_queue<P, std::vector<P>, std::greater<P>> open;
    g_score[start] = 0.0;
    open.push({heuristic(start), start});

    while (!open.empty()) {
        auto [f, u] = open.top();
        open.pop();
        if (u == goal) break;
        if (closed[u]) continue;
        closed[u] = true;

        for (int eidx : g.neighbors(u)) {
            const Edge& e = g.edge(eidx);
            double tentative = g_score[u] + g.weight(e, mode);
            if (tentative < g_score[e.to]) {
                g_score[e.to] = tentative;
                prev[e.to] = u;
                open.push({tentative + heuristic(e.to), e.to});
            }
        }
    }

    if (g_score[goal] == std::numeric_limits<double>::infinity()) return r;
    r.found = true;
    r.total_weight = g_score[goal];
    for (int cur = goal; cur != -1; cur = prev[cur]) r.path.push_back(cur);
    std::reverse(r.path.begin(), r.path.end());
    return r;
}

} // namespace fastroute