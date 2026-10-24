# -*- coding: utf-8 -*-
"""充电宝定位/借用服务后端（FastAPI）。

对外提供：
  GET  /health                      健康检查
  GET  /api/stations                附近充电宝站点（最近优先）
  GET  /api/route                   从用户位置到某站点的最快/最短路线（C++ 算法）
  POST /api/rent                    借出（占用一个充电宝）
  POST /api/return                  归还（释放一个充电宝）
  GET  /api/emergency               低电量兜底：直接返回最近可用站点 + 最快路线

路线计算优先走 C++（native 模块）；若动态库未编译，自动降级为纯 Python Dijkstra，
并在 /health 中以 route_engine 字段注明，方便排查。
"""
from __future__ import annotations

import logging
import math
import os
from typing import Any

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from stations_data import STATIONS, build_route_graph, nearest_stations

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("powerbank")

app = FastAPI(title="充电宝定位与借用服务", version="0.1.0")

# 允许浏览器前端跨域调用（网页版/文件方式打开都可用）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 托管 Vue3 网页版前端：启动后访问 http://localhost:8000/app/
_frontend = os.path.join(os.path.dirname(os.path.dirname(__file__)), "frontend")
if os.path.isdir(_frontend):
    app.mount("/app", StaticFiles(directory=_frontend, html=True), name="frontend")
    logger.info("前端已挂载于 /app（%s）", _frontend)

# ---------- 内存中的可用库存（模拟数据库） ----------
_inventory: dict[int, int] = {s["id"]: s["available"] for s in STATIONS}


# ---------- 路线引擎（C++ 优先，Python 兜底） ----------
def _try_load_cpp_bridge():
    try:
        from route_bridge import get_bridge
        return get_bridge()
    except Exception as e:  # noqa: BLE001
        logger.warning("C++ 路线库不可用（%s），降级为 Python Dijkstra", e)
        return None


_bridge = _try_load_cpp_bridge()


def _python_dijkstra(graph: dict, start: int, goal: int, mode: str) -> dict | None:
    """兜底 Dijkstra。"""
    nodes = {n["id"]: n for n in graph["nodes"]}
    edges = graph["edges"]
    adj: dict[int, list[tuple[int, dict]]] = {}
    for e in edges:
        adj.setdefault(e["from"], []).append((e["to"], e))
        adj.setdefault(e["to"], []).append((e["from"], e))

    def w(e: dict) -> float:
        return e["distance"] if mode == "distance" else e["time"]

    dist = {start: 0.0}
    prev: dict[int, int] = {}
    import heapq
    pq = [(0.0, start)]
    while pq:
        d, u = heapq.heappop(pq)
        if d > dist.get(u, math.inf):
            continue
        if u == goal:
            break
        for v, e in adj.get(u, []):
            nd = d + w(e)
            if nd < dist.get(v, math.inf):
                dist[v] = nd
                prev[v] = u
                heapq.heappush(pq, (nd, v))
    if goal not in dist or dist[goal] == math.inf:
        return None
    path = []
    cur = goal
    while cur in prev or cur == start:
        path.append(cur)
        if cur == start:
            break
        cur = prev[cur]
    path.reverse()
    return {"path": path, "total_weight": round(dist[goal], 1),
            "mode": mode, "engine": "python"}


def _compute_route(graph: dict, start: int, goal: int, mode: str) -> dict | None:
    if _bridge is not None:
        result = _bridge.find_route(graph, start, goal, mode)
        if result is not None:
            result["engine"] = "cpp"
            return result
    return _python_dijkstra(graph, start, goal, mode)


def _route_with_coords(graph: dict, result: dict) -> dict:
    """把节点 id 序列转成带坐标的路径，方便地图渲染。"""
    index = {n["id"]: n for n in graph["nodes"]}
    result["path_coords"] = [
        {"id": pid, "lat": index[pid]["lat"], "lng": index[pid]["lng"]}
        for pid in result["path"] if pid in index
    ]
    return result


# ---------- 路由 ----------
@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "route_engine": "cpp" if _bridge is not None else "python",
        "stations": len(STATIONS),
        "inventory": _inventory,
    }


@app.get("/api/stations")
def stations(lat: float = Query(...), lng: float = Query(...),
             radius: float = Query(5000, ge=0)) -> list[dict]:
    """附近站点，附带实时可用库存。"""
    items = nearest_stations(lat, lng, STATIONS)
    out = []
    for s in items:
        if s["distance_m"] > radius:
            continue
        out.append({**s, "available": _inventory.get(s["id"], s["available"])})
    return out


@app.get("/api/route")
def route(from_lat: float = Query(...), from_lng: float = Query(...),
          station_id: int = Query(...),
          mode: str = Query("time", pattern="^(time|distance)$")) -> dict:
    """从用户位置到指定站点的最快/最短路线。"""
    station = next((s for s in STATIONS if s["id"] == station_id), None)
    if station is None:
        raise HTTPException(status_code=404, detail="站点不存在")

    graph = build_route_graph(from_lat, from_lng, STATIONS)
    goal = graph["station_node"][station_id]
    result = _compute_route(graph, 0, goal, mode)
    if result is None:
        raise HTTPException(status_code=404, detail="不可达")

    return _route_with_coords(graph, result) | {
        "station_id": station_id,
        "station_name": station["name"],
    }


class RentBody(BaseModel):
    station_id: int


@app.post("/api/rent")
def rent(body: RentBody) -> dict:
    sid = body.station_id
    if sid not in _inventory:
        raise HTTPException(status_code=404, detail="站点不存在")
    if _inventory[sid] <= 0:
        raise HTTPException(status_code=409, detail="该站点已无可用充电宝")
    _inventory[sid] -= 1
    return {"station_id": sid, "available": _inventory[sid], "status": "borrowed"}


@app.post("/api/return")
def return_bank(body: RentBody) -> dict:
    sid = body.station_id
    if sid not in _inventory:
        raise HTTPException(status_code=404, detail="站点不存在")
    total = next(s["total"] for s in STATIONS if s["id"] == sid)
    if _inventory[sid] >= total:
        raise HTTPException(status_code=409, detail="该站点槽位已满")
    _inventory[sid] += 1
    return {"station_id": sid, "available": _inventory[sid], "status": "returned"}


@app.get("/api/emergency")
def emergency(lat: float = Query(...), lng: float = Query(...)) -> dict:
    """低电量自动定位后调用：最近有货站点 + 最快路线。"""
    items = nearest_stations(lat, lng, STATIONS)
    avail = [s for s in items if _inventory.get(s["id"], 0) > 0]
    if not avail:
        raise HTTPException(status_code=404, detail="附近暂无可借充电宝")
    best = avail[0]

    graph = build_route_graph(lat, lng, STATIONS)
    goal = graph["station_node"][best["id"]]
    result = _compute_route(graph, 0, goal, "time")
    if result is None:
        raise HTTPException(status_code=404, detail="不可达")

    return {
        "station": {**best, "available": _inventory[best["id"]]},
        "route": _route_with_coords(graph, result),
        "hint": "电量即将耗尽，请尽快前往最近可借点位",
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)