#include "jsonlite.h"

#include <stdexcept>
#include <cstdlib>
#include <cctype>
#include <sstream>

namespace jsonlite {

namespace {
char g_hex[] = "0123456789abcdef";

class Parser {
public:
    explicit Parser(const std::string& text) : text_(text) {}

    Value parse_value() {
        skip_ws();
        if (pos_ >= text_.size()) fail("unexpected end of input");
        char c = text_[pos_];
        if (c == '{') return parse_object();
        if (c == '[') return parse_array();
        if (c == '"') { Value v; v.type = Type::String; v.string = parse_string(); return v; }
        if (c == 't') { expect("true"); Value v; v.type = Type::Bool; v.boolean = true; return v; }
        if (c == 'f') { expect("false"); Value v; v.type = Type::Bool; v.boolean = false; return v; }
        if (c == 'n') { expect("null"); Value v; v.type = Type::Null; return v; }
        if (c == '-' || std::isdigit((unsigned char)c)) return parse_number();
        fail("unexpected character");
        return Value{};
    }

private:
    void skip_ws() {
        while (pos_ < text_.size() && std::isspace((unsigned char)text_[pos_])) ++pos_;
    }

    void expect(const char* lit) {
        for (const char* p = lit; *p; ++p) {
            if (pos_ >= text_.size() || text_[pos_] != *p) fail("invalid literal");
            ++pos_;
        }
    }

    Value parse_object() {
        Value v;
        v.type = Type::Object;
        ++pos_; // '{'
        skip_ws();
        if (peek() == '}') { ++pos_; return v; }
        while (true) {
            skip_ws();
            if (peek() != '"') fail("expected string key");
            std::string key = parse_string();
            skip_ws();
            if (peek() != ':') fail("expected ':'");
            ++pos_;
            v.object.emplace_back(key, parse_value());
            skip_ws();
            char c = peek();
            if (c == ',') { ++pos_; continue; }
            if (c == '}') { ++pos_; break; }
            fail("expected ',' or '}'");
        }
        return v;
    }

    Value parse_array() {
        Value v;
        v.type = Type::Array;
        ++pos_; // '['
        skip_ws();
        if (peek() == ']') { ++pos_; return v; }
        while (true) {
            v.array.push_back(parse_value());
            skip_ws();
            char c = peek();
            if (c == ',') { ++pos_; continue; }
            if (c == ']') { ++pos_; break; }
            fail("expected ',' or ']'");
        }
        return v;
    }

    std::string parse_string() {
        if (peek() != '"') fail("expected '\"'");
        ++pos_;
        std::string out;
        while (pos_ < text_.size()) {
            char c = text_[pos_++];
            if (c == '"') return out;
            if (c == '\\') {
                if (pos_ >= text_.size()) fail("bad escape");
                char e = text_[pos_++];
                switch (e) {
                    case '"': out.push_back('"'); break;
                    case '\\': out.push_back('\\'); break;
                    case '/': out.push_back('/'); break;
                    case 'n': out.push_back('\n'); break;
                    case 't': out.push_back('\t'); break;
                    case 'r': out.push_back('\r'); break;
                    case 'b': out.push_back('\b'); break;
                    case 'f': out.push_back('\f'); break;
                    case 'u': out += parse_unicode(); break;
                    default: fail("bad escape char");
                }
            } else {
                out.push_back(c);
            }
        }
        fail("unterminated string");
        return out;
    }

    std::string parse_unicode() {
        if (pos_ + 4 > text_.size()) fail("bad unicode escape");
        unsigned code = 0;
        for (int i = 0; i < 4; ++i) {
            char c = text_[pos_++];
            code <<= 4;
            if (c >= '0' && c <= '9') code |= (c - '0');
            else if (c >= 'a' && c <= 'f') code |= (c - 'a' + 10);
            else if (c >= 'A' && c <= 'F') code |= (c - 'A' + 10);
            else fail("bad hex digit");
        }
        std::string out;
        // BMP 内基本 UTF-8 编码，足以覆盖中文等常见字符
        if (code < 0x80) {
            out.push_back((char)code);
        } else if (code < 0x800) {
            out.push_back((char)(0xC0 | (code >> 6)));
            out.push_back((char)(0x80 | (code & 0x3F)));
        } else {
            out.push_back((char)(0xE0 | (code >> 12)));
            out.push_back((char)(0x80 | ((code >> 6) & 0x3F)));
            out.push_back((char)(0x80 | (code & 0x3F)));
        }
        return out;
    }

    Value parse_number() {
        size_t start = pos_;
        if (peek() == '-') ++pos_;
        while (pos_ < text_.size() && std::isdigit((unsigned char)text_[pos_])) ++pos_;
        if (peek() == '.') {
            ++pos_;
            while (pos_ < text_.size() && std::isdigit((unsigned char)text_[pos_])) ++pos_;
        }
        if (peek() == 'e' || peek() == 'E') {
            ++pos_;
            if (peek() == '+' || peek() == '-') ++pos_;
            while (pos_ < text_.size() && std::isdigit((unsigned char)text_[pos_])) ++pos_;
        }
        Value v;
        v.type = Type::Number;
        v.number = std::strtod(text_.substr(start, pos_ - start).c_str(), nullptr);
        return v;
    }

    char peek() const {
        if (pos_ >= text_.size()) return '\0';
        return text_[pos_];
    }

    [[noreturn]] void fail(const char* msg) const {
        throw std::runtime_error(std::string("JSON parse error: ") + msg);
    }

    const std::string& text_;
    size_t pos_ = 0;
};
} // namespace (anonymous)

namespace {
std::string escape_string(const std::string& s) {
    std::ostringstream os;
    os << '"';
    for (char c : s) {
        switch (c) {
            case '"': os << "\\\""; break;
            case '\\': os << "\\\\"; break;
            case '\n': os << "\\n"; break;
            case '\t': os << "\\t"; break;
            case '\r': os << "\\r"; break;
            default:
                if ((unsigned char)c < 0x20) {
                    os << "\\u00" << g_hex[(c >> 4) & 0xF] << g_hex[c & 0xF];
                } else {
                    os << c;
                }
        }
    }
    os << '"';
    return os.str();
}
} // namespace (anonymous)

std::string dump(const Value& v) {
    std::ostringstream os;
    switch (v.type) {
        case Type::Null: os << "null"; break;
        case Type::Bool: os << (v.boolean ? "true" : "false"); break;
        case Type::Number: {
            if (v.number == (long long)v.number) os << (long long)v.number;
            else os << v.number;
            break;
        }
        case Type::String: os << escape_string(v.string); break;
        case Type::Array: {
            os << '[';
            for (size_t i = 0; i < v.array.size(); ++i) {
                if (i) os << ',';
                os << dump(v.array[i]);
            }
            os << ']';
            break;
        }
        case Type::Object: {
            os << '{';
            for (size_t i = 0; i < v.object.size(); ++i) {
                if (i) os << ',';
                os << escape_string(v.object[i].first) << ':' << dump(v.object[i].second);
            }
            os << '}';
            break;
        }
    }
    return os.str();
}

Value parse(const std::string& text) {
    Parser p(text);
    return p.parse_value();
}

} // namespace jsonlite