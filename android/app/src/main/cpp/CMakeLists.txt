# Android NDK 构建脚本：把共享 C++ 最快路线算法编入 libfastroute.so
cmake_minimum_required(VERSION 3.22.1)
project(fastroute LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 复用仓库根目录 native/ 下的共享算法源码
set(NATIVE_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../native)

add_library(fastroute SHARED
    route_jni.cpp
    ${NATIVE_ROOT}/src/route.cpp
    ${NATIVE_ROOT}/src/route_c_api.cpp
    ${NATIVE_ROOT}/src/jsonlite.cpp
)

target_include_directories(fastroute PRIVATE
    ${NATIVE_ROOT}/include
    ${NATIVE_ROOT}/src
)

find_library(log-lib log)
target_link_libraries(fastroute ${log-lib})