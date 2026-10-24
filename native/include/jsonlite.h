// jsonlite.h —— 极简 JSON 解析器（无外部依赖，供 C 导出接口使用）
// 仅支持 Demo 所需语法：object / array / string / number / true / false / null
#ifndef FASTROUTE_JSONLITE_H
#define FASTROUTE_JSONLITE_H

#include <string>
#include <vector>
#include <memory>

namespace jsonlite {

enum class Type { Null, Bool, Number, String, Array, Object };

struct Value {
    Type type = Type::Null;
    bool boolean = false;
    double number = 0.0;
    std::string string;
    std::vector<Value> array;
    // 保持插入顺序的键值对（本 Demo 数据量小，线性查找足够）
    std::vector<std::pair<std::string, Value>> object;

    bool is_null() const { return type == Type::Null; }
    bool is_object() const { return type == Type::Object; }
    bool is_array() const { return type == Type::Array; }
    bool is_number() const { return type == Type::Number; }
    bool is_string() const { return type == Type::String; }

    const Value* find(const std::string& key) const {
        if (type != Type::Object) return nullptr;
        for (const auto& kv : object) {
            if (kv.first == key) return &kv.second;
        }
        return nullptr;
    }
};

// 解析失败时抛出 std::runtime_error
Value parse(const std::string& text);
// 将 Value 序列化为 JSON 字符串
std::string dump(const Value& v);

} // namespace jsonlite

#endif // FASTROUTE_JSONLITE_H