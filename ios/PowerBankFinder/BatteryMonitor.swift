import Foundation
import UIKit

final class BatteryMonitor: ObservableObject {
    @Published var level: Int = 0
    @Published var isCharging: Bool = false

    /// 电量下穿 20%/10%/5% 或进入低电量模式时触发（用于"低电量自动获取位置"）。
    /// 参数 level 取值为 20/10/5，或 -1 表示低电量模式。
    var onLowBattery: ((Int) -> Void)?

    private let thresholds = [20, 10, 5]
    private var lastLevel = 100

    init() {
        UIDevice.current.isBatteryMonitoringEnabled = true
        refresh()
        lastLevel = level >= 0 ? level : 100

        NotificationCenter.default.addObserver(
            self, selector: #selector(batteryChanged),
            name: UIDevice.batteryLevelDidChangeNotification, object: nil
        )
        NotificationCenter.default.addObserver(
            self, selector: #selector(batteryChanged),
            name: UIDevice.batteryStateDidChangeNotification, object: nil
        )
        NotificationCenter.default.addObserver(
            self, selector: #selector(powerModeChanged),
            name: ProcessInfo.powerStateDidChangeNotification, object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func batteryChanged() {
        let prev = lastLevel
        refresh()
        if isCharging {
            lastLevel = level
            return
        }
        for t in thresholds where level <= t && prev > t {
            onLowBattery?(t)
        }
        lastLevel = level
    }

    @objc private func powerModeChanged() {
        if ProcessInfo.processInfo.isLowPowerModeEnabled {
            refresh()
            if !isCharging {
                onLowBattery?(-1)
            }
        }
    }

    private func refresh() {
        let raw = UIDevice.current.batteryLevel
        level = raw < 0 ? 0 : Int(raw * 100)
        isCharging = UIDevice.current.batteryState == .charging ||
            UIDevice.current.batteryState == .full
    }
}