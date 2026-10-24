// PowerBankFinder-Bridging-Header.h
// Swift 调用 C 导出接口所需的桥接头。
// 使用前提：在 Xcode 的 Build Settings 里设置
//   - Objective-C Bridging Header 指向本文件
//   - Header Search Paths 加入 $(SRCROOT)/../../native/include
//   - 将 native/src/{route,route_c_api,jsonlite}.cpp 添加到 target 的 Compile Sources
#import "route_c_api.h"