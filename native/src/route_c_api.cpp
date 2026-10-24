// route_c_api.cpp —— 对外 C 导出接口
// 提供与语言无关的字符串接口：
//   输入 Graph JSON，输出 PathResult JSON
// 供 Python(ctypes) / Android(JNI) / iOS(Swift C bridge) 复用同一套 C++ 算法。
#include "route.h"
#include "jsonlite.h"

#include <cstring>
#include <cstdlib>
#include <cstdio>

using namespace fastroute;

// mode: 0=最快(time), 1=最短(distance)
// 成功返回 0；-1=参数/图非法，-2=不可达
extern "C" int route_path_json(const char* graph_json, int start, int goal,
                               int mode, char* out, int out_size) {
    if (!graph_json || !out || out_size <= 0) return -1;

    try {
        jsonlite::Value root = jsonlite::parse(graph_json);
        const jsonlite::Value* nodes = root.find("nodes");
        const jsonlite::Value* edges = root.find("edges");
        if (!nodes || !edges || !nodes->is_array() || !edges->is_array()) return -1;

        Graph g;
        for (const auto& n : nodes->array) {
            const jsonlite::Value* id = n.find("id");
            const jsonlite::Value* lat = n.find("lat");
            const jsonlite::Value* lng = n.find("lng");
            if (!id || !lat || !lng) return -1;
            g.add_node((int)id->number, lat->number, lng->number);
        }
        for (const auto& e : edges->array) {
            const jsonlite::Value* from = e.find("from");
            const jsonlite::Value* to = e.find("to");
            const jsonlite::Value* dist = e.find("distance");
            const jsonlite::Value* time = e.find("time");
            if (!from || !to || !dist || !time) return -1;
            g.add_edge((int)from->number, (int)to->number, dist->number, time->number);
        }

        WeightMode wm = (mode == 1) ? WeightMode::Distance : WeightMode::Time;
        PathResult r = a_star(g, start, goal, wm);
        if (!r.found) return -2;

        jsonlite::Value result;
        result.type = jsonlite::Type::Object;
        jsonlite::Value f; f.type = jsonlite::Type::Bool; f.boolean = true;
        result.object.emplace_back("found", f);
        jsonlite::Value m; m.type = jsonlite::Type::String;
        m.string = (wm == WeightMode::Distance) ? "distance" : "time";
        result.object.emplace_back("mode", m);
        jsonlite::Value p; p.type = jsonlite::Type::Array;
        for (int id : r.path) {
            jsonlite::Value v; v.type = jsonlite::Type::Number; v.number = id;
            p.array.push_back(v);
        }
        result.object.emplace_back("path", p);
        jsonlite::Value w; w.type = jsonlite::Type::Number; w.number = r.total_weight;
        result.object.emplace_back("total_weight", w);

        std::string s = jsonlite::dump(result);
        if ((int)s.size() + 1 > out_size) return -1;
        std::memcpy(out, s.c_str(), s.size() + 1);
        return 0;
    } catch (...) {
        return -1;
    }
}

// 便捷函数：直接返回 malloc 的字符串（调用方需 route_free 释放）
extern "C" char* route_path(const char* graph_json, int start, int goal, int mode) {
    char buf[4096];
    int rc = route_path_json(graph_json, start, goal, mode, buf, sizeof(buf));
    if (rc != 0) return nullptr;
    size_t len = std::strlen(buf);
    char* p = (char*)std::malloc(len + 1);
    if (!p) return nullptr;
    std::memcpy(p, buf, len + 1);
    return p;
}

extern "C" void route_free(char* p) {
    std::free(p);
}

// 自检：用一个 5 节点小图验证算法正确性，返回 0 表示通过
extern "C" int route_selftest() {
    const char* g = R"({
      "nodes":[
        {"id":0,"lat":31.2304,"lng":121.4737},
        {"id":1,"lat":31.2310,"lng":121.4745},
        {"id":2,"lat":31.2320,"lng":121.4755},
        {"id":3,"lat":31.2330,"lng":121.4765},
        {"id":4,"lat":31.2340,"lng":121.4775}
      ],
      "edges":[
        {"from":0,"to":1,"distance":100,"time":72},
        {"from":1,"to":2,"distance":120,"time":80},
        {"from":2,"to":3,"distance":130,"time":90},
        {"from":3,"to":4,"distance":140,"time":100},
        {"from":0,"to":4,"distance":1000,"time":720}
      ]
    })";
    char out[4096];
    int rc = route_path_json(g, 0, 4, 0, out, sizeof(out));
    if (rc != 0) return 1; // 最快路径应存在
    // 0-1-2-3-4 累计 time = 72+80+90+100 = 342，远小于 0-4 的 720
    jsonlite::Value v = jsonlite::parse(out);
    const jsonlite::Value* w = v.find("total_weight");
    if (!w || w->number > 400.0) return 2;
    return 0;
}