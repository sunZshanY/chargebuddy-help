// route_c_api.h —— C 导出接口（纯 C 声明，供 Swift/其他语言桥接）
#ifndef FASTROUTE_C_API_H
#define FASTROUTE_C_API_H

#ifdef __cplusplus
extern "C" {
#endif

// mode: 0 = 最快(time), 1 = 最短(distance)
// 将结果 JSON 写入 out，成功返回 0；-1 参数/图非法；-2 不可达
int route_path_json(const char* graph_json, int start, int goal, int mode,
                    char* out, int out_size);

// 返回 malloc 分配的 JSON 字符串（调用方需 route_free 释放），失败返回 NULL
char* route_path(const char* graph_json, int start, int goal, int mode);

void route_free(char* p);

// 自检：返回 0 表示算法正确
int route_selftest(void);

#ifdef __cplusplus
}
#endif

#endif // FASTROUTE_C_API_H