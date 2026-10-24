# 充电宝救援（ChargeBuddy）Demo

一个面向"电量不足人群"的共享充电宝定位与借用应用 Demo：实时定位附近充电宝柜机，
**当电量降到 1% 时自动获取位置**，并给出**最快路线**（C++ 算法）引导用户前往借用。

> 本项目是工程技术演示，跨 4 种语言协同：Kotlin（Android）、Swift（iOS）、
> Python（后端 + 地图抓包）、C/C++（最快路线算法）。

---

## 功能特性

- 附近充电宝站点展示（有货 / 已借空状态着色）
- **低电量自动定位**：电量 ≤ 1% 自动触发，抓取当前位置，推荐最近可借点位
- **最快路线规划**：Dijkstra / A* 算法，支持"最快（时间）"与"最短（距离）"两种原则
- 借用 / 归还充电宝（后端接口 + 内存库存）
- 地图抓包：Python 从地图开放平台抓取充电宝 POI，刷新站点数据
- 同一套 C++ 算法被三种运行时复用：Python(ctypes)、Android(JNI)、iOS(C bridge)

---

## 技术栈与目录结构

```
map-electronic-for-help/
├── native/                 # C/C++ 最快路线算法（核心）
│   ├── include/            # jsonlite.h、route_c_api.h
│   ├── src/                # route.cpp(Dijkstra/A*)、route_c_api.cpp(C接口)、jsonlite.cpp
│   ├── tests/              # 单元测试
│   └── CMakeLists.txt
├── backend/                # Python 后端（FastAPI）
│   ├── app.py              # REST 接口
│   ├── stations_data.py    # 站点数据 + 路网图构建
│   ├── route_bridge.py     # ctypes 桥接 C++
│   ├── scraper.py          # 地图抓包（mock/高德/百度）
│   └── requirements.txt
├── android/                # Kotlin 客户端
│   └── app/src/main/
│       ├── java/com/powerbank/finder/    # Activity/电量/定位/地图/网络
│       └── cpp/            # JNI 桥接 + CMake（复用 native 源码）
└── ios/                    # Swift 客户端（SwiftUI + MapKit）
    └── PowerBankFinder/
```

---

## 数据契约（跨语言共享）

| 概念 | 字段 | 说明 |
|------|------|------|
| 站点 Station | `id,name,lat,lng,available,total,price_per_hour,distance_m` | 充电宝柜机 |
| 图 Graph | `nodes:[{id,lat,lng}]` `edges:[{from,to,distance,time}]` | 无向图，`time`=步行时间 |
| 路线 Route | `found,mode,path[],total_weight` | `path` 为节点 id 序列 |

C 导出接口（`native/include/route_c_api.h`）：

```c
// mode: 0=最快(time) 1=最短(distance)
char* route_path(const char* graph_json, int start, int goal, int mode);
void  route_free(char* p);
```

---

## 快速开始

### 1. 编译 C++ 核心（后端桥接所需）

```bash
# Windows（已用 g++ 14 验证，产物 libfastroute.dll）
cd native
g++ -std=c++17 -O2 -shared -I include -I src \
    src/route.cpp src/route_c_api.cpp src/jsonlite.cpp -o libfastroute.dll

# 运行单元测试
g++ -std=c++17 -O2 -I include -I src \
    src/route.cpp src/route_c_api.cpp src/jsonlite.cpp tests/test_route.cpp -o test_route && ./test_route
```

> macOS/Linux 相应产出 `libfastroute.dylib` / `libfastroute.so`；也可用 CMake 构建。

### 2. 启动后端

```bash
cd backend
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
```

接口一览：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查（含路线引擎：cpp/python） |
| GET | `/api/stations?lat=&lng=&radius=` | 附近站点 |
| GET | `/api/route?from_lat=&from_lng=&station_id=&mode=` | 最快/最短路线 |
| GET | `/api/emergency?lat=&lng=` | 低电量兜底（最近可借 + 最快路线） |
| POST | `/api/rent` / `/api/return` | 借用 / 归还 |

### 3. 地图抓包（刷新站点数据）

```bash
cd backend
python scraper.py --lat 31.2304 --lng 121.4737 --radius 2000 --out stations.json

# 接入真实地图平台：
#   高德  MAP_PROVIDER=amap  AMAP_KEY=xxx python scraper.py ...
#   百度  MAP_PROVIDER=baidu BAIDU_AK=xxx  python scraper.py ...
```

### 4. Android 客户端（Kotlin）

1. 用 Android Studio 打开 `android/` 目录（AGP 8.5.2 / Kotlin 1.9.24）。
2. 构建时会通过 `app/src/main/cpp/CMakeLists.txt` 自动把 `native/` 的 C++ 源码编入
   `libfastroute.so`，供 JNI 调用。
3. 在模拟器运行（`BASE_URL` 已指向 `http://10.0.2.2:8000`，即宿主机后端）。
   - 真机请把 `api/ApiClient.kt` 的 `BASE_URL` 改为宿主机局域网 IP。

要点：
- `BatteryMonitor.kt`：注册 `ACTION_BATTERY_CHANGED` 实时读电量，≤1% 触发 `onLowBattery`。
- `MainActivity.handleLowBattery()`：自动定位 → 调 `/api/emergency` → 弹出最近可借点位。
- `MapView.kt`：无三方 SDK 的轻量画布地图；`native/RouteJNI.kt`：JNI 本地 C++ 路线。

### 5. iOS 客户端（Swift）

1. 新建 iOS App 工程（SwiftUI，iOS 17+），把 `ios/PowerBankFinder/` 下源码加入。
2. Build Settings 配置：
   - `Objective-C Bridging Header` → `PowerBankFinder-Bridging-Header.h`
   - `Header Search Paths` → 添加仓库根的 `native/include`
   - 将 `native/src/{route,route_c_api,jsonlite}.cpp` 加入 target 的 Compile Sources
3. 模拟器运行（`ApiClient.baseURL` 指向 `http://127.0.0.1:8000`）。
   - 真机请改为宿主机局域网 IP。

要点：
- `BatteryMonitor.swift`：`UIDevice.batteryLevelDidChangeNotification`，≤1% 触发兜底。
- `LocationManager.swift`：`CLLocationManager` 授权与定位。
- `RouteBridge.swift`：通过桥接头调用 C 接口 `route_path`；`MapPolyline` 绘制路线。

### 6. 网页版前端（Vue3，无需安装即可体验）

`frontend/` 是纯 HTML+CSS+JS 的 Vue3 单页应用（Vue 已本地 vendored，离线可用），
由后端直接托管，**不需要任何构建/安装**：

```bash
cd backend
uvicorn app:app --host 0.0.0.0 --port 8000
# 浏览器打开 http://127.0.0.1:8000/app/
```

包含三个页面：**地图**（附近站点 + 电量条 + 模拟 1% 低电量自动定位 + 最快路线）、
**详情**（站点信息 + C++ 最快路线 + 借用/归还）、**下载**（APK/iOS/源码下载 + 使用方法）。
下载页会返回一个完整的 `chargetbuddy-source.zip`。

> 自行部署：把 `frontend/` 放到任意静态服务器，通过后端 `frontend/backend` 挂载的
> `/app` 路径访问即可；接口同源调用，无需 CORS 配置。

---

## 核心实现说明

### 最快路线算法（C++）
`native/src/route.cpp` 实现：
- `dijkstra()`：优先队列优化的单源最短路径；
- `a_star()`：以球面直线距离（时间模式按 1.4 m/s 换算为时间下界）为启发式加速；
- 无向图、支持 `Distance` / `Time` 两种边权，`route_c_api.cpp` 导出 C 接口。

### 低电量自动定位（1%）
- Android：`ACTION_BATTERY_CHANGED` + `BatteryManager.EXTRA_LEVEL`，≤1% → 定位 → `/api/emergency`。
- iOS：`UIDevice.batteryLevel` 通知，≤1% → `CLLocationManager` → `/api/emergency`。

### 地图抓包（Python）
`scraper.py` 抽象 `MapProvider`（Mock / 高德 / 百度），按环境变量切换，
抓取结果统一输出为 `stations.json`，供后端站点接口消费。

---

## 免责声明

本项目为教学/演示用途，站点坐标与库存为模拟数据；真实接入地图数据需自行申请
高德/百度开放平台 API Key，并对接真实柜机厂商库存接口。