import MapKit
import SwiftUI

struct ContentView: View {
    @EnvironmentObject var location: LocationManager
    @EnvironmentObject var battery: BatteryMonitor

    @State private var stations: [Station] = []
    @State private var routeCoords: [CLLocationCoordinate2D] = []
    @State private var routeText: String = ""
    @State private var showingEmergency = false
    @State private var emergencyMessage = ""
    @State private var position = MapCameraPosition.region(
        MKCoordinateRegion(
            center: LocationManager.fallback,
            span: MKCoordinateSpan(latitudeDelta: 0.03, longitudeDelta: 0.03)
        )
    )

    var body: some View {
        VStack(spacing: 0) {
            header

            Map(position: $position) {
                Annotation("我", coordinate: location.current) {
                    Circle()
                        .fill(.blue)
                        .frame(width: 18, height: 18)
                        .overlay(Circle().stroke(.white, lineWidth: 2))
                }
                ForEach(stations) { s in
                    Marker(s.name, systemImage: "battery.75percent", coordinate: s.coordinate)
                        .tint(s.available > 0 ? .green : .red)
                }
                if routeCoords.count > 1 {
                    MapPolyline(coordinates: routeCoords)
                        .stroke(.orange, lineWidth: 5)
                }
            }
            .frame(maxHeight: .infinity)

            controls
            list
        }
        .onAppear {
            battery.onLowBattery = { runEmergency(level: $0) }
            refresh()
        }
        .alert("电量即将耗尽！", isPresented: $showingEmergency) {
            Button("去借用") { emergencyMessage = "借用成功！" }
            Button("取消", role: .cancel) { }
        } message: {
            Text(emergencyMessage)
        }
    }

    private var header: some View {
        HStack {
            Text("充电宝救援")
                .font(.title2.bold())
            Spacer()
            Text("电量 \(battery.level)%").fontWeight(.semibold) +
            Text(battery.isCharging ? " · 充电中" : " · 放电").foregroundColor(.secondary)
        }
        .padding()
    }

    private var controls: some View {
        HStack {
            Button("刷新站点") { refresh() }
                .buttonStyle(.borderedProminent)
            Button("模拟 20%") { runEmergency(level: 20) }
                .buttonStyle(.bordered)
            Spacer()
            Button("本地C++路线") { runLocalRoute() }
                .buttonStyle(.bordered)
        }
        .padding(.horizontal)
        .padding(.bottom, 6)
    }

    private var list: some View {
        List {
            if !routeText.isEmpty {
                Section("最快路线") { Text(routeText).font(.footnote) }
            }
            Section("附近充电宝") {
                ForEach(stations) { s in
                    Button {
                        routeTo(s)
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(s.name).font(.body)
                                Text(String(format: "%.0f 米", s.distanceM))
                                    .font(.caption).foregroundColor(.secondary)
                            }
                            Spacer()
                            Text(s.available > 0 ? "有货 \(s.available)/\(s.total)" : "已借空")
                                .font(.caption)
                                .foregroundColor(s.available > 0 ? .green : .red)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .frame(height: 280)
    }

    // MARK: - 业务

    private func refresh() {
        Task {
            do {
                let list = try await ApiClient.stations(
                    lat: location.current.latitude,
                    lng: location.current.longitude
                )
                stations = list
                moveCamera(to: location.current)
            } catch {
                routeText = "后端不可用：\(error.localizedDescription)"
            }
        }
    }

    private func routeTo(_ s: Station) {
        Task {
            do {
                let r = try await ApiClient.route(
                    fromLat: location.current.latitude,
                    fromLng: location.current.longitude,
                    stationId: s.id
                )
                routeCoords = r.pathCoords.map(\.coordinate)
                routeText = String(format: "最快路线：%.0f 秒（引擎 %@）", r.totalWeight, r.engine ?? "-")
            } catch {
                routeText = "路线失败：\(error.localizedDescription)"
            }
        }
    }

    private func runEmergency(level: Int) {
        Task {
            do {
                let resp = try await ApiClient.emergency(
                    lat: location.current.latitude,
                    lng: location.current.longitude
                )
                stations = [resp.station]
                routeCoords = resp.route.pathCoords.map(\.coordinate)
                routeText = resp.hint
                let trigger = level < 0 ? "省电模式已开启，电量告急" : "电量已降至 \(level)%"
                emergencyMessage = String(
                    format: "%@\n为你定位到最近可借码宝：%@\n可用 %d 个，步行约 %.0f 米\n%@",
                    trigger, resp.station.name, resp.station.available, resp.station.distanceM, resp.hint
                )
                showingEmergency = true
                moveCamera(to: resp.station.coordinate)
            } catch {
                routeText = "兜底失败：\(error.localizedDescription)"
            }
        }
    }

    private func runLocalRoute() {
        guard !stations.isEmpty else {
            routeText = "请先刷新附近站点"
            return
        }
        let (graph, goal) = GraphBuilder.build(
            userLat: location.current.latitude,
            userLng: location.current.longitude,
            stations: stations
        )
        if let r = RouteBridge.findRoute(graph: graph, start: 0, goal: Int32(goal)) {
            routeCoords = r.path.compactMap { id in
                graph.nodes.first(where: { $0.id == id })?.coordinate
            }
            routeText = String(format: "本地 C++ 最快路线：%.1f 秒（%d 跳）", r.totalWeight, r.path.count - 1)
        } else {
            routeText = "本地路线不可达"
        }
    }

    private func moveCamera(to c: CLLocationCoordinate2D) {
        position = .region(
            MKCoordinateRegion(
                center: c,
                span: MKCoordinateSpan(latitudeDelta: 0.03, longitudeDelta: 0.03)
            )
        )
    }
}

private extension GraphNode {
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: lat, longitude: lng) }
}