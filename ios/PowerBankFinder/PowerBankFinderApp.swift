import SwiftUI

@main
struct PowerBankFinderApp: App {
    @StateObject private var location = LocationManager()
    @StateObject private var battery = BatteryMonitor()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(location)
                .environmentObject(battery)
        }
    }
}