cmake_minimum_required(VERSION 3.10)
project(fastroute CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# 核心算法库（C++ 实现 + C 导出接口）
add_library(fastroute SHARED
    src/route.cpp
    src/route_c_api.cpp
    src/jsonlite.cpp
)
target_include_directories(fastroute PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)

# 静态库版本（用于 iOS/Android 静态链接）
add_library(fastroute_static STATIC
    src/route.cpp
    src/route_c_api.cpp
    src/jsonlite.cpp
)
target_include_directories(fastroute_static PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
set_target_properties(fastroute_static PROPERTIES OUTPUT_NAME fastroute)

# 单元测试
enable_testing()
add_executable(test_route tests/test_route.cpp
    src/route.cpp
    src/route_c_api.cpp
    src/jsonlite.cpp
)
target_include_directories(test_route PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src)
add_test(NAME test_route COMMAND test_route)

if(MSVC)
    target_compile_options(fastroute PRIVATE /O2)
    target_compile_options(fastroute_static PRIVATE /O2)
else()
    target_compile_options(fastroute PRIVATE -O2 -Wall)
    target_compile_options(fastroute_static PRIVATE -O2 -Wall)
endif()