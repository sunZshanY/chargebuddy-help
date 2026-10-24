// test_route.cpp —— 算法正确性单元测试
#include "route.h"
#include <cassert>
#include <cstdio>
#include <cmath>

extern "C" int route_selftest();

using namespace fastroute;

static void test_dijkstra_basic() {
    Graph g;
    g.add_node(0, 0, 0);
    g.add_node(1, 0, 0);
    g.add_node(2, 0, 0);
    g.add_node(3, 0, 0);
    g.add_edge(0, 1, 100, 72);
    g.add_edge(1, 2, 120, 80);
    g.add_edge(2, 3, 130, 90);
    g.add_edge(0, 3, 500, 300);

    // 最快路径 0->1->2->3 = 242s
    PathResult r = shortest_path(g, 0, 3, WeightMode::Time);
    assert(r.found);
    assert(std::fabs(r.total_weight - 242.0) < 1e-6);
    assert((r.path == std::vector<int>{0, 1, 2, 3}));

    // 最短距离路径 0->1->2->3 = 350m（直接 0->3 反而是 500，故仍走 0-1-2-3）
    PathResult d = shortest_path(g, 0, 3, WeightMode::Distance);
    assert(d.found);
    assert(std::fabs(d.total_weight - 350.0) < 1e-6);
}

static void test_a_star_matches_dijkstra() {
    Graph g;
    for (int i = 0; i <= 10; ++i) g.add_node(i, 31.0 + i * 0.001, 121.0 + i * 0.001);
    for (int i = 0; i < 10; ++i) {
        g.add_edge(i, i + 1, 100 + i * 5, 70 + i * 3);
    }
    // 额外一条捷径边
    g.add_edge(0, 10, 5000, 3000);

    PathResult dj = shortest_path(g, 0, 10, WeightMode::Time);
    PathResult as = a_star(g, 0, 10, WeightMode::Time);
    assert(dj.found && as.found);
    assert(std::fabs(dj.total_weight - as.total_weight) < 1e-6);
    assert(dj.path == as.path);
}

static void test_unreachable() {
    Graph g;
    g.add_node(0, 0, 0);
    g.add_node(1, 0, 0);
    PathResult r = shortest_path(g, 0, 1, WeightMode::Time);
    assert(!r.found);
}

int main() {
    test_dijkstra_basic();
    test_a_star_matches_dijkstra();
    test_unreachable();
    assert(route_selftest() == 0);
    std::printf("all tests passed\n");
    return 0;
}