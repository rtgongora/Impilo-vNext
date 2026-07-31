package zw.gov.mohcc.impilo.emergency.triage.corpus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader for the IITT corpus shape only.
 *
 * <p>This library stays free of Jackson so TeaVM can compile it. The corpus is small and
 * well-known; a general-purpose parser would pull in a dependency the engine itself does not need.
 *
 * <p>Classpath resources are embedded for the browser target via
 * {@link IittCorpusResourceSupplier} (TeaVM {@code ResourceSupplier} ServiceLoader plugin).
 * Prefer {@link ClassLoader#getResourceAsStream(String)} — TeaVM wires that path; a bare
 * {@code Class#getResourceAsStream} can omit {@code ClassLoader} from the reachable set.
 */
final class CorpusJson {

    private CorpusJson() {}

    static Object parse(String raw) {
        return new Parser(raw).parseValue();
    }

    /**
     * Read a classpath resource. Path may be absolute ({@code /…}) or relative; both normalize
     * to the ClassLoader form required by TeaVM's embedded resource table.
     */
    static String readClasspath(String resource) {
        String path = resource.startsWith("/") ? resource.substring(1) : resource;
        ClassLoader loader = CorpusJson.class.getClassLoader();
        if (loader == null) {
            throw new IllegalStateException("no ClassLoader for " + path);
        }
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("classpath resource missing: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asObject(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asArray(Object value) {
        return (List<Object>) value;
    }

    private static final class Parser {
        private final String raw;
        private int i;

        Parser(String raw) {
            this.raw = raw;
        }

        Object parseValue() {
            skipWs();
            if (i >= raw.length()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            char c = raw.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> out = new LinkedHashMap<>();
            skipWs();
            if (peek('}')) {
                i++;
                return out;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                out.put(key, parseValue());
                skipWs();
                if (peek('}')) {
                    i++;
                    return out;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> out = new ArrayList<>();
            skipWs();
            if (peek(']')) {
                i++;
                return out;
            }
            while (true) {
                out.add(parseValue());
                skipWs();
                if (peek(']')) {
                    i++;
                    return out;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < raw.length()) {
                char c = raw.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (i >= raw.length()) {
                        throw new IllegalArgumentException("truncated escape");
                    }
                    char e = raw.charAt(i++);
                    sb.append(switch (e) {
                        case '"', '\\', '/' -> e;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> parseUnicode();
                        default -> throw new IllegalArgumentException("bad escape \\" + e);
                    });
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("unclosed string");
        }

        private char parseUnicode() {
            if (i + 4 > raw.length()) {
                throw new IllegalArgumentException("truncated unicode escape");
            }
            int code = Integer.parseInt(raw.substring(i, i + 4), 16);
            i += 4;
            return (char) code;
        }

        private Object parseLiteral(String lit, Object value) {
            if (!raw.startsWith(lit, i)) {
                throw new IllegalArgumentException("expected " + lit);
            }
            i += lit.length();
            return value;
        }

        private Object parseNumber() {
            int start = i;
            if (peek('-')) {
                i++;
            }
            while (i < raw.length() && Character.isDigit(raw.charAt(i))) {
                i++;
            }
            boolean fractional = false;
            if (peek('.')) {
                fractional = true;
                i++;
                while (i < raw.length() && Character.isDigit(raw.charAt(i))) {
                    i++;
                }
            }
            if (peek('e') || peek('E')) {
                fractional = true;
                i++;
                if (peek('+') || peek('-')) {
                    i++;
                }
                while (i < raw.length() && Character.isDigit(raw.charAt(i))) {
                    i++;
                }
            }
            String token = raw.substring(start, i);
            if (fractional) {
                return Double.parseDouble(token);
            }
            long v = Long.parseLong(token);
            if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
                return (int) v;
            }
            return v;
        }

        private void skipWs() {
            while (i < raw.length()) {
                char c = raw.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    i++;
                } else {
                    return;
                }
            }
        }

        private boolean peek(char c) {
            return i < raw.length() && raw.charAt(i) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw new IllegalArgumentException("expected '" + c + "' at " + i);
            }
            i++;
        }
    }
}
