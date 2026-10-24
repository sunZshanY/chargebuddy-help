package com.powerbank.finder.map

import com.powerbank.finder.model.Station

/**
 * 内置模拟充电宝站点（硬编码自 backend/stations_data.py 的 STATIONS 常量，
 * 含 id/name/lat/lng/available/total/price_per_hour），地址为演示用补充字段。
 * 无地图 Key 或地图接口不可用时作为兜底数据源。
 */
object MockStations {

    val ALL: List<Station> = listOf(
        Station(
            id = 1, name = "人民广场站 · 地铁1号口柜机",
            lat = 31.2304, lng = 121.4737,
            available = 5, total = 10, pricePerHour = 2.0,
            address = "上海市黄浦区人民广场地铁站1号口"
        ),
        Station(
            id = 2, name = "南京东路步行街 · 快充柜",
            lat = 31.2350, lng = 121.4790,
            available = 8, total = 8, pricePerHour = 3.0,
            address = "上海市黄浦区南京东路步行街"
        ),
        Station(
            id = 3, name = "来福士广场 B1 服务台",
            lat = 31.2260, lng = 121.4760,
            available = 2, total = 12, pricePerHour = 2.5,
            address = "上海市黄浦区西藏中路268号来福士广场B1层"
        ),
        Station(
            id = 4, name = "上海博物馆北门",
            lat = 31.2320, lng = 121.4710,
            available = 0, total = 6, pricePerHour = 2.0,
            address = "上海市黄浦区人民大道201号上海博物馆北门"
        ),
        Station(
            id = 5, name = "大世界地铁站 · 2号口",
            lat = 31.2280, lng = 121.4830,
            available = 6, total = 9, pricePerHour = 2.0,
            address = "上海市黄浦区大世界地铁站2号口"
        ),
        Station(
            id = 6, name = "外滩观景平台 · 服务点",
            lat = 31.2400, lng = 121.4900,
            available = 3, total = 5, pricePerHour = 4.0,
            address = "上海市黄浦区中山东一路外滩观景平台"
        ),
        Station(
            id = 7, name = "淮海中路 · 便利店柜机",
            lat = 31.2210, lng = 121.4700,
            available = 7, total = 10, pricePerHour = 2.0,
            address = "上海市黄浦区淮海中路沿线便利店"
        ),
        Station(
            id = 8, name = "云南南路美食街 · 入口柜机",
            lat = 31.2260, lng = 121.4810,
            available = 1, total = 4, pricePerHour = 2.5,
            address = "上海市黄浦区云南南路美食街入口"
        ),
    )
}
