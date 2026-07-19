package zw.gov.mohcc.impilo.experience.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * D7 browser response-shape guard (Identity Contract §7.2): the browser must
 * never receive a raw Health ID or CRID — those are trust-core identifiers;
 * the browser works with the Patient Context Token (and, until the token
 * migration completes, the clinical-plane CPID).
 *
 * <p>Applies to every controller via advice — including ones this class must
 * not edit — and follows the estate ratchet: {@code off | log | enforce}
 * via {@code impilo.identifier-shaping.mode} (default {@code log}).</p>
 *
 * <ul>
 *   <li><b>log</b> — WARN once per (route, field) when a response carries a
 *       raw trust-core identifier; body unchanged. Burn-in mode: the leak set
 *       becomes the measured D7 backlog.</li>
 *   <li><b>enforce</b> — offending string values are redacted in place.</li>
 * </ul>
 *
 * <p>Deliberately out of scope here: {@code cpid} fields (the staff UI's
 * working key until the Patient Context Token migration lands — tracked as
 * D7 residual debt), and non-JSON/POJO bodies (covered by the source-level
 * ratchet in {@code IdentifierLeakInventoryTest}).</p>
 */
@RestControllerAdvice
public class IdentifierExposureShapingAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(IdentifierExposureShapingAdvice.class);

    /** Field names that carry trust-core identifiers when raw. */
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i)^(health_?id|impilo_?health_?id|subject_?health_?id|crid)$");

    /** UUID shape — the raw form of HID/CRID. Non-UUID values (e.g. masked) pass. */
    private static final Pattern UUID_VALUE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final String REDACTED = "[REDACTED-D7]";

    private final String mode;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public IdentifierExposureShapingAdvice(
            @Value("${impilo.identifier-shaping.mode:log}") String mode) {
        this.mode = mode == null ? "log" : mode.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return !"off".equals(mode);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body == null || (selectedContentType != null
                && !selectedContentType.includes(MediaType.APPLICATION_JSON))) {
            return body;
        }
        String route = request.getURI().getPath();
        inspect(body, route);
        return body;
    }

    private void inspect(Object body, String route) {
        if (body instanceof JsonNode node) {
            walk(node, route, "$");
        } else if (body instanceof Map<?, ?> map) {
            walkMap(map, route, "$");
        } else if (body instanceof List<?> list) {
            for (Object item : list) {
                inspect(item, route);
            }
        }
        // POJO bodies are intentionally not reflected over — the source-level
        // leak inventory ratchet covers those emitters.
    }

    private void walk(JsonNode node, String route, String path) {
        if (node instanceof ObjectNode obj) {
            var names = obj.fieldNames();
            java.util.List<String> fields = new java.util.ArrayList<>();
            names.forEachRemaining(fields::add);
            for (String field : fields) {
                JsonNode child = obj.get(field);
                if (isLeak(field, child)) {
                    report(route, path + "." + field);
                    if ("enforce".equals(mode)) {
                        obj.set(field, TextNode.valueOf(REDACTED));
                    }
                } else {
                    walk(child, route, path + "." + field);
                }
            }
        } else if (node instanceof ArrayNode arr) {
            for (JsonNode child : arr) {
                walk(child, route, path + "[]");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void walkMap(Map<?, ?> map, String route, String path) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String field = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof String s && SENSITIVE_KEY.matcher(field).matches()
                    && UUID_VALUE.matcher(s).matches()) {
                report(route, path + "." + field);
                if ("enforce".equals(mode)) {
                    try {
                        ((Map<Object, Object>) map).put(entry.getKey(), REDACTED);
                    } catch (UnsupportedOperationException immutable) {
                        // Immutable map (Map.of) — leave the body intact; the log
                        // entry still lands and the emitter is on the backlog.
                    }
                }
            } else if (value instanceof Map<?, ?> nested) {
                walkMap(nested, route, path + "." + field);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nested) {
                        walkMap(nested, route, path + "." + field + "[]");
                    } else if (item instanceof JsonNode node) {
                        walk(node, route, path + "." + field + "[]");
                    }
                }
            } else if (value instanceof JsonNode node) {
                walk(node, route, path + "." + field);
            }
        }
    }

    private boolean isLeak(String field, JsonNode value) {
        return value != null && value.isTextual()
                && SENSITIVE_KEY.matcher(field).matches()
                && UUID_VALUE.matcher(value.asText()).matches();
    }

    private void report(String route, String fieldPath) {
        if (warned.add(route + "|" + fieldPath)) {
            log.warn("D7_IDENTIFIER_EXPOSURE ({}): {} emits {} — raw trust-core identifier in a browser-bound response",
                    mode, route, fieldPath);
        }
    }
}
