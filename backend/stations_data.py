# -*- coding: utf-8 -*-
"""充电宝站点数据与地图图构建。

本模块是 Demo 的"数据源"：
  - STATIONS：模拟真实世界中的充电宝柜机坐标（上海人民广场周边）
  - build_route_graph：把用户位置 + 站点 + 简化路网组装成图，
    供 C++ 最快路线算法直接消费（节点/边 JSON 契约与 native 模块一致）。

图契约：
  nodes: [{"id", "lat", "lng"}, ...]    # id 从 0 开始的整数
  edges: [{"from", "to", "distance", "time"}, ...]  # 无向边，distance=米，time=秒
"""
from __future__ import annotations

import math

# 平均步行速度 m/s，用于把距离换算为"步行时间"边权
WALK_SPEED_MPS = 1.4

# 模拟充电宝柜机（真实 Demo 中由 scraper.py 抓取地图 POI 刷新）
STATIONS = [
    {"id": 1, "name": "人民广场站 · 地铁1号口柜机", "lat": 31.2304, "lng": 121.4737,
     "available": 5, "total": 10, "price_per_hour": 2.0},
    {"id": 2, "name": "南京东路步行街 · 快充柜", "lat": 31.2350, "lng": 121.4790,
     "available": 8, "total": 8, "price_per_hour": 3.0},
    {"id": 3, "name": "来福士广场 B1 服务台", "lat": 31.2260, "lng": 121.4760,
     "available": 2, "total": 12, "price_per_hour": 2.5},
    {"id": 4, "name": "上海博物馆北门", "lat": 31.2320, "lng": 121.4710,
     "available": 0, "total": 6, "price_per_hour": 2.0},
    {"id": 5, "name": "大世界地铁站 · 2号口", "lat": 31.2280, "lng": 121.4830,
     "available": 6, "total": 9, "price_per_hour": 2.0},
    {"id": 6, "name": "外滩观景平台 · 服务点", "lat": 31.2400, "lng": 121.4900,
     "available": 3, "total": 5, "price_per_hour": 4.0},
    {"id": 7, "name": "淮海中路 · 便利店柜机", "lat": 31.2210, "lng": 121.4700,
     "available": 7, "total": 10, "price_per_hour": 2.0},
    {"id": 8, "name": "云南南路美食街 · 入口柜机", "lat": 31.2260, "lng": 121.4810,
     "available": 1, "total": 4, "price_per_hour": 2.5},
]


def haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """球面距离（米）。"""
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def _route_nodes_latlng(node_id: int, bbox: dict) -> tuple[float, float]:
    """根据 bbox 把路网节点 id 还原为坐标（确定性散列，保证可复现）。"""
    lat_min, lat_max = bbox["lat_min"], bbox["lat_max"]
    lng_min, lng_max = bbox["lng_min"], bbox["lng_max"]
    # 用伪随机但确定的方式散布交点
    import random
    rnd = random.Random(node_id * 7919)
    lat = lat_min + (lat_max - lat_min) * rnd.random()
    lng = lng_min + (lng_max - lng_min) * rnd.random()
    return lat, lng


def build_route_graph(user_lat: float, user_lng: float,
                      stations: list[dict] | None = None,
                      junction_count: int = 24) -> dict:
    """组装无向路网图。

    节点布局：
      0               —— 用户当前位置
      1..junction_count —— 模拟的"路口/路网点"
      junction+1..   —— 充电宝站点（stations 的 id 映射为顺序节点）

    边：
      用户 连到最近的若干个路口
      路口 之间按 k-近邻相连
      站点 连到最近的路口
    边的 time = distance / WALK_SPEED_MPS；部分边被标记为"慢行"（时间 ×2），
    从而让"最快"与"最短"原则产生可区分的不同路径。
    """
    stations = stations or STATIONS

    # 包围盒（用户 + 站点外扩 20%）
    lats = [user_lat] + [s["lat"] for s in stations]
    lngs = [user_lng] + [s["lng"] for s in stations]
    span_lat = max(lats) - min(lats) or 0.01
    span_lng = max(lngs) - min(lngs) or 0.01
    bbox = {
        "lat_min": min(lats) - span_lat * 0.2,
        "lat_max": max(lats) + span_lat * 0.2,
        "lng_min": min(lngs) - span_lng * 0.2,
        "lng_max": max(lngs) + span_lng * 0.2,
    }

    nodes = [{"id": 0, "lat": user_lat, "lng": user_lng}]
    # 路口节点
    for i in range(1, junction_count + 1):
        lat, lng = _route_nodes_latlng(i, bbox)
        nodes.append({"id": i, "lat": lat, "lng": lng})

    # 站点节点（编号接在路口之后）
    station_node = {}  # 站点业务 id -> 图节点 id
    for idx, s in enumerate(stations):
        nid = junction_count + 1 + idx
        nodes.append({"id": nid, "lat": s["lat"], "lng": s["lng"]})
        station_node[s["id"]] = nid

    edges: list[dict] = []
    seen: set[tuple[int, int]] = set()

    def add_edge(a: int, b: int, slow: bool = False) -> None:
        key = (min(a, b), max(a, b))
        if key in seen:
            return
        seen.add(key)
        dist = haversine_m(nodes[a]["lat"], nodes[a]["lng"],
                           nodes[b]["lat"], nodes[b]["lng"])
        time = dist / WALK_SPEED_MPS
        if slow:
            time *= 2.2  # 慢行路段：人流密集 / 红绿灯
        edges.append({"from": a, "to": b, "distance": round(dist, 1),
                      "time": round(time, 1)})

    # 1) 用户连到最近的 4 个路口
    jn = [n for n in nodes if 1 <= n["id"] <= junction_count]
    nearest = sorted(jn, key=lambda n: haversine_m(user_lat, user_lng, n["lat"], n["lng"]))
    for n in nearest[:4]:
        add_edge(0, n["id"])

    # 2) 路口之间按 3-近邻相连（构造连通路网）
    for n in jn:
        others = sorted(jn, key=lambda m: haversine_m(n["lat"], n["lng"], m["lat"], m["lng"]))
        for m in others[1:4]:
            # 稳定地把部分边标记为慢行，制造"最快 vs 最短"差异
            slow = (n["id"] + m["id"]) % 5 == 0
            add_edge(n["id"], m["id"], slow=slow)

    # 3) 每个站点连到最近的路口
    for sid, nid in station_node.items():
        st = nodes[nid]
        jn_sorted = sorted(jn, key=lambda m: haversine_m(st["lat"], st["lng"], m["lat"], m["lng"]))
        for m in jn_sorted[:2]:
            add_edge(nid, m["id"])

    return {"nodes": nodes, "edges": edges, "station_node": station_node}


def nearest_stations(user_lat: float, user_lng: float,
                     stations: list[dict] | None = None, limit: int = 10) -> list[dict]:
    """按球面距离返回最近的站点，附带 distance_m。"""
    stations = stations or STATIONS
    out = []
    for s in stations:
        d = haversine_m(user_lat, user_lng, s["lat"], s["lng"])
        out.append({**s, "distance_m": round(d, 1)})
    out.sort(key=lambda x: x["distance_m"])
    return out[:limit]