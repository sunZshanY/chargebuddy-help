import Foundation

// 通过 Bridging Header 调用 C 导出的 C++ 最快路线算法
enum RouteBridge {

    /// mode: 0 = 最快(time), 1 = 最短(distance)
    static func findRoute(graph: Graph, start: Int32, goal: Int32, mode: Int32 = 0) -> RouteResult? {
        guard let json = graph.toJSON() else { return nil }
        guard let ptr = route_path(json, start, goal, mode) else { return nil }
        defer { route_free(ptr) }
        guard let str = String(cString: ptr, encoding: .utf8),
              let data = str.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(RouteResult.self, from: data)
    }
}

// 本地组装小图，演示"离线直接使用 C++ 算法"
enum GraphBuilder {

    private static func haversineM(_ a: GraphNode, _ b: GraphNode) -> Double {
        let r = 6_371_000.0
        let p1 = a.lat * .pi / 180, p2 = b.lat * .pi / 180
        let dp = (b.lat - a.lat) * .pi / 180
        let dl = (b.lng - a.lng) * .pi / 180
        let h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(h))
    }

    static func build(userLat: Double, userLng: Double, stations: [Station]) -> (graph: Graph, goal: Int) {
        guard let nearest = stations.min(by: { $0.distanceM < $1.distanceM }) else {
            preconditionFailure("至少需要一个站点")
        }
        let midLat = (userLat + nearest.lat) / 2
        let midLng = (userLng + nearest.lng) / 2

        let nodes = [
            GraphNode(id: 0, lat: userLat, lng: userLng),
            GraphNode(id: 1, lat: midLat + 0.0006, lng: midLng - 0.0003),
            GraphNode(id: 2, lat: midLat - 0.0004, lng: midLng + 0.0005),
            GraphNode(id: 3, lat: nearest.lat, lng: nearest.lng),
        ]

        var edges: [GraphEdge] = []
        func add(_ f: Int, _ t: Int, slow: Bool) {
            let d = haversineM(nodes[f], nodes[t])
            let time = slow ? d / 1.4 * 2.2 : d / 1.4
            edges.append(GraphEdge(from: f, to: t, distance: d, time: time))
        }

        add(0, 1, slow: true)   // 慢行岔路
        add(0, 2, slow: false)
        add(1, 3, slow: true)   // 慢行岔路
        add(2, 3, slow: false)

        return (Graph(nodes: nodes, edges: edges), 3)
    }
}