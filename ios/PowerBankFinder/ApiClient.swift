import Foundation

enum ApiClient {
    // 模拟器可访问本机；真机请改为宿主机局域网 IP，例如 http://192.168.1.10:8000
    static let baseURL = URL(string: "http://127.0.0.1:8000")!

    private static func get<T: Decodable>(_ path: String, query: [URLQueryItem]) async throws -> T {
        var comps = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        comps.queryItems = query
        let (data, response) = try await URLSession.shared.data(from: comps.url!)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    static func stations(lat: Double, lng: Double, radius: Double = 5000) async throws -> [Station] {
        try await get("api/stations", query: [
            URLQueryItem(name: "lat", value: "\(lat)"),
            URLQueryItem(name: "lng", value: "\(lng)"),
            URLQueryItem(name: "radius", value: "\(radius)"),
        ])
    }

    static func emergency(lat: Double, lng: Double) async throws -> EmergencyResponse {
        try await get("api/emergency", query: [
            URLQueryItem(name: "lat", value: "\(lat)"),
            URLQueryItem(name: "lng", value: "\(lng)"),
        ])
    }

    static func route(fromLat: Double, fromLng: Double, stationId: Int, mode: String = "time") async throws -> RouteInfo {
        try await get("api/route", query: [
            URLQueryItem(name: "from_lat", value: "\(fromLat)"),
            URLQueryItem(name: "from_lng", value: "\(fromLng)"),
            URLQueryItem(name: "station_id", value: "\(stationId)"),
            URLQueryItem(name: "mode", value: mode),
        ])
    }
}