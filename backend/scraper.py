# -*- coding: utf-8 -*-
"""地图抓包（抓取充电宝柜机 POI 数据）。

用途：从地图开放平台抓取"充电宝 / 共享充电宝"类 POI，刷新 stations.json，
交由后端 /api/stations 对外提供。

支持三种数据源，通过环境变量 MAP_PROVIDER 选择：
  - mock  （默认）：生成确定性模拟 POI，无需 API Key，开箱即用
  - amap  ：高德地图 POI 搜索（需 AMAP_KEY）
  - baidu ：百度地图 POI 搜索（需 BAIDU_AK）

用法：
  python scraper.py --lat 31.2304 --lng 121.4737 --radius 2000 --out stations.json
"""
from __future__ import annotations

import argparse
import json
import os
import time
from typing import Protocol

import requests

from stations_data import STATIONS, haversine_m

DEFAULT_LAT, DEFAULT_LNG = 31.2304, 121.4737  # 上海人民广场


class MapProvider(Protocol):
    def search_charging_stations(self, lat: float, lng: float,
                                 radius: int) -> list[dict]:
        """返回 [{"name","lat","lng","address"}, ...]"""


class MockProvider:
    """确定性模拟抓包结果，无需联网 / Key。"""

    def search_charging_stations(self, lat: float, lng: float,
                                 radius: int) -> list[dict]:
        results = []
        for i, s in enumerate(STATIONS):
            results.append({
                "name": s["name"],
                "lat": s["lat"],
                "lng": s["lng"],
                "address": f"模拟地址 {i + 1} 号",
                "distance_m": round(haversine_m(lat, lng, s["lat"], s["lng"]), 1),
            })
        results.sort(key=lambda x: x["distance_m"])
        return results


class AMapProvider:
    """高德地图 POI 搜索（关键词：充电宝）。"""

    def __init__(self, key: str) -> None:
        self.key = key

    def search_charging_stations(self, lat: float, lng: float,
                                 radius: int) -> list[dict]:
        url = "https://restapi.amap.com/v3/place/around"
        params = {
            "key": self.key,
            "location": f"{lng},{lat}",
            "keywords": "充电宝",
            "radius": radius,
            "types": "010100|160300",  # 购物服务 | 生活服务
            "offset": 25,
            "page": 1,
            "extensions": "all",
        }
        resp = requests.get(url, params=params, timeout=10)
        resp.raise_for_status()
        pois = resp.json().get("pois", [])
        return [self._map(p) for p in pois]

    @staticmethod
    def _map(p: dict) -> dict:
        loc = p.get("location", "0,0").split(",")
        return {
            "name": p.get("name", "充电宝柜机"),
            "lng": float(loc[0]), "lat": float(loc[1]),
            "address": p.get("address", ""),
        }


class BaiduMapProvider:
    """百度地图 POI 搜索（关键词：充电宝）。"""

    def __init__(self, ak: str) -> None:
        self.ak = ak

    def search_charging_stations(self, lat: float, lng: float,
                                 radius: int) -> list[dict]:
        url = "https://api.map.baidu.com/place/v2/search"
        params = {
            "query": "充电宝",
            "location": f"{lat},{lng}",
            "radius": radius,
            "output": "json",
            "page_size": 20,
            "page_num": 0,
            "coord_type": "wgs84ll",
            "ak": self.ak,
        }
        resp = requests.get(url, params=params, timeout=10)
        resp.raise_for_status()
        results = resp.json().get("results", [])
        return [self._map(r) for r in results]

    @staticmethod
    def _map(r: dict) -> dict:
        loc = r.get("location", {})
        return {
            "name": r.get("name", "充电宝柜机"),
            "lng": loc.get("lng", 0.0), "lat": loc.get("lat", 0.0),
            "address": r.get("address", ""),
        }


def get_provider() -> MapProvider:
    name = os.environ.get("MAP_PROVIDER", "mock").lower()
    if name == "amap":
        key = os.environ.get("AMAP_KEY")
        if not key:
            raise SystemExit("请设置环境变量 AMAP_KEY")
        return AMapProvider(key)
    if name == "baidu":
        ak = os.environ.get("BAIDU_AK")
        if not ak:
            raise SystemExit("请设置环境变量 BAIDU_AK")
        return BaiduMapProvider(ak)
    return MockProvider()


def main() -> None:
    ap = argparse.ArgumentParser(description="抓取充电宝 POI")
    ap.add_argument("--lat", type=float, default=DEFAULT_LAT)
    ap.add_argument("--lng", type=float, default=DEFAULT_LNG)
    ap.add_argument("--radius", type=int, default=2000, help="搜索半径（米）")
    ap.add_argument("--out", default="stations.json")
    args = ap.parse_args()

    provider = get_provider()
    pois = provider.search_charging_stations(args.lat, args.lng, args.radius)

    payload = {
        "center": {"lat": args.lat, "lng": args.lng},
        "radius": args.radius,
        "provider": os.environ.get("MAP_PROVIDER", "mock"),
        "fetched_at": int(time.time()),
        "count": len(pois),
        "stations": pois,
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print(f"[scraper] 抓取完成：{len(pois)} 个充电宝柜机 -> {args.out}")


if __name__ == "__main__":
    main()