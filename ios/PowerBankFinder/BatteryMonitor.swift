import Foundation
import UIKit

final class BatteryMonitor: ObservableObject {
    @Published var level: Int = 0
    @Published var isCharging: Bool = false

    /// 电量降到 1% 时触发（用于"低电量自动获取位置"）
    var onLowBattery: (() -> Void)?

    private let threshold = 1

    init() {
        UIDevice.current.isBatteryMonitoringEnabled = true
        refresh()

        NotificationCenter.default.addObserver(
            self, selector: #selector(batteryChanged),
            name: UIDevice.batteryLevelDidChangeNotification, object: nil
        )
        NotificationCenter.default.addObserver(
            self, selector: #selector(batteryChanged),
            name: UIDevice.batteryStateDidChangeNotification, object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func batteryChanged() {
        refresh()
        if level <= threshold && !isCharging {
            onLowBattery?()
        }
    }

    private func refresh() {
        let raw = UIDevice.current.batteryLevel
        level = raw < 0 ? 0 : Int(raw * 100)
        isCharging = UIDevice.current.batteryState == .charging ||
            UIDevice.current.batteryState == .full
    }
}