# -*- coding: utf-8 -*-
"""通过 ctypes 桥接调用 C++ 最快路线算法（native 目录编译产物）。

运行前请先编译 native 模块（见 README），产出动态库：
  - Windows: native/libfastroute.dll
  - macOS:   native/libfastroute.dylib
  - Linux:   native/libfastroute.so
"""
from __future__ import annotations

import ctypes
import json
import os
from ctypes.util import find_library

_NATIVE_DIR = os.path.join(os.path.dirname(__file__), "..", "native")


def _find_library() -> str:
    candidates = [
        os.path.join(_NATIVE_DIR, "libfastroute.dll"),
        os.path.join(_NATIVE_DIR, "libfastroute.dylib"),
        os.path.join(_NATIVE_DIR, "libfastroute.so"),
        os.path.join(_NATIVE_DIR, "fastroute.dll"),
    ]
    for c in candidates:
        if os.path.exists(c):
            return c
    # 兜底：让系统搜索
    found = find_library("fastroute")
    if found:
        return found
    raise FileNotFoundError(
        "未找到 fastroute 动态库，请先在 native/ 目录执行 CMake 编译（见 README）"
    )


class RouteBridge:
    def __init__(self) -> None:
        path = _find_library()
        self._lib = ctypes.CDLL(path)

        # char* route_path(const char*, int, int, int)
        self._lib.route_path.argtypes = [
            ctypes.c_char_p, ctypes.c_int, ctypes.c_int, ctypes.c_int,
        ]
        self._lib.route_path.restype = ctypes.c_void_p
        self._lib.route_free.argtypes = [ctypes.c_void_p]
        self._lib.route_free.restype = None

    def find_route(self, graph: dict, start: int, goal: int,
                   mode: str = "time") -> dict | None:
        """调用 C++ a_star 求最快/最短路径。

        mode: "time"（最快，默认）/ "distance"（最短）
        返回 {"path": [nodes], "total_weight": float, "mode": str}；不可达返回 None。
        """
        mode_code = 1 if mode == "distance" else 0
        graph_json = json.dumps(graph, ensure_ascii=False).encode("utf-8")
        ptr = self._lib.route_path(graph_json, start, goal, mode_code)
        if not ptr:
            return None
        try:
            raw = ctypes.string_at(ptr)
            return json.loads(raw.decode("utf-8"))
        finally:
            self._lib.route_free(ptr)


# 进程内单例
_bridge: RouteBridge | None = None


def get_bridge() -> RouteBridge:
    global _bridge
    if _bridge is None:
        _bridge = RouteBridge()
    return _bridge