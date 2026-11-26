package com.auratrade.evals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately tiny JSON reader and writer for the eval harness. Evals should not break when the
 * application's JSON library changes major version, and they only need objects, arrays, strings,
 * numbers, booleans and null. Objects become LinkedHashMap, arrays ArrayList, integral numbers Long
 * and other numbers Double.
 */
final class MiniJson {

  private final String s;
  private int i;

  private MiniJson(String s) {
    this.s = s;
  }

  static Object parse(String text) {
    MiniJson p = new MiniJson(text);
    p.ws();
    Object v = p.value();
    p.ws();
    if (p.i != p.s.length()) {
      throw p.error("unexpected trailing characters");
    }
    return v;
  }

  // ---------- parsing ----------

  private Object value() {
    if (i >= s.length()) {
      throw error("unexpected end of input");
    }
    char c = s.charAt(i);
    return switch (c) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      case 't' -> literal("true", Boolean.TRUE);
      case 'f' -> literal("false", Boolean.FALSE);
      case 'n' -> literal("null", null);
      default -> number();
    };
  }

  private Map<String, Object> object() {
    Map<String, Object> map = new LinkedHashMap<>();
    i++; // {
    ws();
    if (peek() == '}') {
      i++;
      return map;
    }
    while (true) {
      ws();
      if (peek() != '"') {
        throw error("expected a string key");
      }
      String key = string();
      ws();
      expect(':');
      ws();
      map.put(key, value());
      ws();
      char c = next();
      if (c == '}') {
        return map;
      }
      if (c != ',') {
        throw error("expected ',' or '}'");
      }
    }
  }

  private List<Object> array() {
    List<Object> list = new ArrayList<>();
    i++; // [
    ws();
    if (peek() == ']') {
      i++;
      return list;
    }
    while (true) {
      ws();
      list.add(value());
      ws();
      char c = next();
      if (c == ']') {
        return list;
      }
      if (c != ',') {
        throw error("expected ',' or ']'");
      }
    }
  }

  private String string() {
    i++; // opening quote
    StringBuilder sb = new StringBuilder();
    while (true) {
      if (i >= s.length()) {
        throw error("unterminated string");
      }
      char c = s.charAt(i++);
      if (c == '"') {
        return sb.toString();
      }
      if (c != '\\') {
        sb.append(c);
        continue;
      }
      if (i >= s.length()) {
        throw error("unterminated escape");
      }
      char e = s.charAt(i++);
      switch (e) {
        case '"', '\\', '/' -> sb.append(e);
        case 'b' -> sb.append('\b');
        case 'f' -> sb.append('\f');
        case 'n' -> sb.append('\n');
        case 'r' -> sb.append('\r');
        case 't' -> sb.append('\t');
        case 'u' -> {
          if (i + 4 > s.length()) {
            throw error("bad unicode escape");
          }
          sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
          i += 4;
        }
        default -> throw error("bad escape \\" + e);
      }
    }
  }

  private Object number() {
    int start = i;
    while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
      i++;
    }
    String token = s.substring(start, i);
    if (token.isEmpty()) {
      throw error("unexpected character '" + s.charAt(start) + "'");
    }
    try {
      if (token.matches("-?\\d+")) {
        return Long.parseLong(token);
      }
      return Double.parseDouble(token);
    } catch (NumberFormatException e) {
      throw error("bad number " + token);
    }
  }

  private Object literal(String word, Object result) {
    if (!s.startsWith(word, i)) {
      throw error("unexpected token");
    }
    i += word.length();
    return result;
  }

  private void ws() {
    while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
      i++;
    }
  }

  private char peek() {
    if (i >= s.length()) {
      throw error("unexpected end of input");
    }
    return s.charAt(i);
  }

  private char next() {
    char c = peek();
    i++;
    return c;
  }

  private void expect(char c) {
    if (next() != c) {
      throw error("expected '" + c + "'");
    }
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException("JSON error at position " + i + ": " + message);
  }

  // ---------- writing ----------

  static String toJson(Object value) {
    StringBuilder sb = new StringBuilder();
    write(sb, value, 0);
    return sb.toString();
  }

  private static void write(StringBuilder sb, Object v, int depth) {
    if (v == null) {
      sb.append("null");
    } else if (v instanceof String str) {
      quote(sb, str);
    } else if (v instanceof Boolean || v instanceof Long || v instanceof Integer) {
      sb.append(v);
    } else if (v instanceof Double d) {
      sb.append(d.isNaN() || d.isInfinite() ? "null" : Double.toString(d));
    } else if (v instanceof Number n) {
      sb.append(n);
    } else if (v instanceof Map<?, ?> map) {
      if (map.isEmpty()) {
        sb.append("{}");
        return;
      }
      sb.append("{\n");
      int n = 0;
      for (Map.Entry<?, ?> e : map.entrySet()) {
        indent(sb, depth + 1);
        quote(sb, String.valueOf(e.getKey()));
        sb.append(": ");
        write(sb, e.getValue(), depth + 1);
        sb.append(++n < map.size() ? ",\n" : "\n");
      }
      indent(sb, depth);
      sb.append('}');
    } else if (v instanceof Iterable<?> items) {
      List<Object> list = new ArrayList<>();
      items.forEach(list::add);
      if (list.isEmpty()) {
        sb.append("[]");
        return;
      }
      sb.append("[\n");
      for (int k = 0; k < list.size(); k++) {
        indent(sb, depth + 1);
        write(sb, list.get(k), depth + 1);
        sb.append(k + 1 < list.size() ? ",\n" : "\n");
      }
      indent(sb, depth);
      sb.append(']');
    } else {
      quote(sb, String.valueOf(v));
    }
  }

  private static void indent(StringBuilder sb, int depth) {
    sb.append("  ".repeat(depth));
  }

  private static void quote(StringBuilder sb, String str) {
    sb.append('"');
    for (char c : str.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }
}
