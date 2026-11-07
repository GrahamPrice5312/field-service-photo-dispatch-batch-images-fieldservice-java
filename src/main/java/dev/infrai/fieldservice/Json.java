package dev.infrai.fieldservice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

final class Json {
    private Json() {}

    static String object(Map<String, ?> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append(quote(entry.getKey())).append(':').append(value(entry.getValue()));
        }
        return json.append('}').toString();
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static String value(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> stringKeys = new LinkedHashMap<>();
            map.forEach((key, item) -> stringKeys.put(String.valueOf(key), item));
            return object(stringKeys);
        }
        return quote(String.valueOf(value));
    }

    static Object parse(String source) {
        Parser parser = new Parser(source);
        Object parsed = parser.readValue();
        parser.skipSpace();
        if (!parser.atEnd()) throw new IllegalArgumentException("Unexpected JSON suffix");
        return parsed;
    }

    private static final class Parser {
        private final String source;
        private int index;

        Parser(String source) { this.source = source; }
        boolean atEnd() { return index == source.length(); }
        void skipSpace() { while (!atEnd() && Character.isWhitespace(source.charAt(index))) index++; }

        Object readValue() {
            skipSpace();
            if (atEnd()) throw new IllegalArgumentException("Expected JSON value");
            char current = source.charAt(index);
            if (current == '{') return readObject();
            if (current == '[') return readArray();
            if (current == '"') return readString();
            if (source.startsWith("true", index)) { index += 4; return true; }
            if (source.startsWith("false", index)) { index += 5; return false; }
            if (source.startsWith("null", index)) { index += 4; return null; }
            return readNumber();
        }

        List<Object> readArray() {
            List<Object> result = new ArrayList<>();
            index++;
            skipSpace();
            if (!atEnd() && source.charAt(index) == ']') { index++; return result; }
            while (true) {
                result.add(readValue());
                skipSpace();
                if (!atEnd() && source.charAt(index) == ']') { index++; return result; }
                expect(',');
            }
        }

        Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++;
            skipSpace();
            if (!atEnd() && source.charAt(index) == '}') { index++; return result; }
            while (true) {
                skipSpace();
                String key = readString();
                skipSpace();
                expect(':');
                result.put(key, readValue());
                skipSpace();
                if (!atEnd() && source.charAt(index) == '}') { index++; return result; }
                expect(',');
            }
        }

        String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (!atEnd()) {
                char current = source.charAt(index++);
                if (current == '"') return value.toString();
                if (current != '\\') { value.append(current); continue; }
                if (atEnd()) throw new IllegalArgumentException("Incomplete JSON escape");
                char escaped = source.charAt(index++);
                value.append(switch (escaped) {
                    case '"', '\\', '/' -> escaped;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> throw new IllegalArgumentException("Unsupported JSON escape");
                });
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }

        Number readNumber() {
            int start = index;
            while (!atEnd() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            String number = source.substring(start, index);
            return number.contains(".") || number.contains("e") || number.contains("E")
                    ? Double.parseDouble(number) : Long.parseLong(number);
        }

        void expect(char expected) {
            skipSpace();
            if (atEnd() || source.charAt(index) != expected) {
                throw new IllegalArgumentException("Expected JSON token " + expected);
            }
            index++;
        }
    }
}
