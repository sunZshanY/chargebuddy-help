import CoreLocation

struct Station: Identifiable, Codable {
    let id: Int
    let name: String
    let lat: Double
    let lng: Double
    let available: Int
    let total: Int
    let pricePerHour: Double
    let distanceM: Double

    enum CodingKeys: String, CodingKey {
        case id, name, lat, lng, available, total
        case pricePerHour = "price_per_hour"
        case distanceM = "distance_m"
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }
}

struct PathCoord: Codable {
    let id: Int
    let lat: Double
    let lng: Double

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }
}

struct RouteInfo: Codable {
    let found: Bool
    let mode: String
    let path: [Int]
    let totalWeight: Double
    let engine: String?
    let pathCoords: [PathCoord]

    enum CodingKeys: String, CodingKey {
        case found, mode, path, engine
        case totalWeight = "total_weight"
        case pathCoords = "path_coords"
    }
}

struct EmergencyResponse: Codable {
    let station: Station
    let route: RouteInfo
    let hint: String
}

// 本地 C++ 图（与 route_c_api 的 JSON 契约一致）
struct GraphNode: Codable {
    let id: Int
    let lat: Double
    let lng: Double
}

struct GraphEdge: Codable {
    let from: Int
    let to: Int
    let distance: Double
    let time: Double
}

struct Graph: Codable {
    let nodes: [GraphNode]
    let edges: [GraphEdge]

    func toJSON() -> String? {
        guard let data = try? JSONEncoder().encode(self) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

struct RouteResult: Codable {
    let found: Bool
    let mode: String
    let path: [Int]
    let totalWeight: Double

    enum CodingKeys: String, CodingKey {
        case found, mode, path
        case totalWeight = "total_weight"
    }
}