import CoreLocation
import Foundation

final class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    @Published var authorizationDenied = false

    private let manager = CLLocationManager()

    // 上海人民广场（模拟器无定位时使用）
    static let fallback = CLLocationCoordinate2D(latitude: 31.2304, longitude: 121.4737)

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.requestWhenInUseAuthorization()
    }

    var current: CLLocationCoordinate2D {
        coordinate ?? Self.fallback
    }

    func requestLocation() {
        manager.requestWhenInUseAuthorization()
        manager.requestLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        coordinate = locations.first?.coordinate
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // 无定位时回退到演示坐标
        if coordinate == nil { coordinate = Self.fallback }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            manager.requestLocation()
        case .denied, .restricted:
            authorizationDenied = true
            coordinate = Self.fallback
        default:
            break
        }
    }
}