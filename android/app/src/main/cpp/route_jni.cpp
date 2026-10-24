// route_jni.cpp —— JNI 桥接，让 Kotlin 直接调用本地 C++ 最快路线算法
#include <jni.h>

// C 导出接口（由 native/src/route_c_api.cpp 提供）
extern "C" int route_path_json(const char* graph_json, int start, int goal,
                               int mode, char* out, int out_size);

// mode: 0 = 最快(time), 1 = 最短(distance)
// 成功返回结果 JSON 字符串，失败返回 null
extern "C" JNIEXPORT jstring JNICALL
Java_com_powerbank_finder_native_RouteJNI_nativeFindRoute(
        JNIEnv* env, jobject /* this */, jstring graphJson,
        jint start, jint goal, jint mode) {
    if (graphJson == nullptr) return nullptr;

    const char* gj = env->GetStringUTFChars(graphJson, nullptr);
    if (gj == nullptr) return nullptr;

    char out[8192];
    int rc = route_path_json(gj, start, goal, mode, out, sizeof(out));
    env->ReleaseStringUTFChars(graphJson, gj);

    if (rc != 0) return nullptr;
    return env->NewStringUTF(out);
}