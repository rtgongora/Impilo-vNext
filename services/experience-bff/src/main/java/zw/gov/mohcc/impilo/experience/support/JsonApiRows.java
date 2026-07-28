package zw.gov.mohcc.impilo.experience.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an upstream service's own JSON into the JSON:API row the shell's typed hooks declare.
 *
 * <p>WHY THIS EXISTS
 * <p>The BFF grew two populations of read endpoints. One builds a real resource — {@code
 * ClinicalDocumentsController.documentRow}, {@code ConditionsController.toConditionResource},
 * {@code VitalsObservationBridge.composeSetRow}. The other pipes the upstream node straight
 * through: {@code ResponseEntity.ok(Map.of("data", pctData, ...))}. The TypeScript hooks in
 * {@code ui/one-ui-shell/src/hooks/queries} declare {@code attributes} for <em>both</em>
 * populations, so for every passthrough endpoint the declared type is a claim about a payload
 * that nothing produces, and the consumer's {@code x.attributes.status} throws.
 *
 * <p>That is not a theoretical mismatch. {@code PatientBanner} renders on every EHR screen and
 * reads {@code a.attributes.severity}; PCT's allergy rows are flat snake_case, so the banner
 * crashes precisely on the patients who <em>have</em> a severe allergy recorded — the ones it
 * exists to warn about. The immunization list is worse: PCT returns an object
 * {@code {items, page, size, total}} where the hook declares an array, so {@code ?? []} cannot
 * rescue it and {@code .filter is not a function} fires on every load.
 *
 * <p>The fix is at this boundary, not at the ~48 dereference sites. Point fixes do not hold:
 * every new EHR tab copies the {@code attributes} idiom from the tab beside it, so the next one
 * reintroduces the bug. Normalising here makes the declared types true.
 *
 * <p>Both dialects are emitted for the same reason {@code documentRow} emits both: read hooks
 * type camelCase while the create contracts are snake_case, and a row that answers to only one
 * spelling silently reads as absent to half its callers — the empty-200 failure one layer in.
 */
public final class JsonApiRows {

    private JsonApiRows() {
    }

    /**
     * Flattens whatever container an upstream chose into the rows inside it.
     *
     * <p>Upstreams disagree: PCT's allergy list is a bare array, its immunization list is
     * {@code {"items": [...]}}, and some wrap in {@code {"data": [...]}}. A caller that assumes
     * one of those breaks on the others, which is exactly how the immunization hook shipped.
     * A single object is treated as a one-row list rather than dropped.
     */
    public static List<JsonNode> items(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node == null || node.isNull() || node.isMissingNode()) {
            return out;
        }
        JsonNode container = node;
        if (container.isObject()) {
            for (String key : new String[] { "items", "data", "content", "results" }) {
                if (container.has(key) && (container.get(key).isArray() || container.get(key).isObject())) {
                    container = container.get(key);
                    break;
                }
            }
        }
        if (container.isArray()) {
            container.forEach(out::add);
        } else if (container.isObject()) {
            out.add(container);
        }
        return out;
    }

    /**
     * Copies every field of a flat upstream row into an {@code attributes} map under both the
     * snake_case and camelCase spelling of its name. Values stay as {@link JsonNode}, which
     * Jackson serialises unchanged — no type information is guessed or lost on the way through.
     */
    public static Map<String, Object> attributesOf(JsonNode row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (row == null || !row.isObject()) {
            return attributes;
        }
        row.fields().forEachRemaining(field -> {
            String name = field.getKey();
            attributes.put(name, field.getValue());
            String alternate = name.contains("_") ? toCamel(name) : toSnake(name);
            if (!alternate.equals(name)) {
                attributes.putIfAbsent(alternate, field.getValue());
            }
        });
        return attributes;
    }

    /**
     * Wraps one upstream row as {@code {id, type, attributes}}.
     *
     * @param idKeys candidate identifier fields in preference order (e.g. {@code "allergy_id"});
     *               the first present, non-blank one wins. If none match, any field whose name
     *               ends in {@code _id} or {@code Id} is used, so a row is never emitted without
     *               an id — React keys off it and a null id collapses a list to one element.
     */
    public static Map<String, Object> row(JsonNode source, String type, String... idKeys) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", resolveId(source, idKeys));
        resource.put("type", type);
        resource.put("attributes", attributesOf(source));
        return resource;
    }

    /** {@link #row} applied across whatever container {@link #items} finds. */
    public static List<Map<String, Object>> rows(JsonNode source, String type, String... idKeys) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode item : items(source)) {
            out.add(row(item, type, idKeys));
        }
        return out;
    }

    private static String resolveId(JsonNode source, String... idKeys) {
        if (source == null || !source.isObject()) {
            return null;
        }
        for (String key : idKeys) {
            String value = text(source, key);
            if (value != null) {
                return value;
            }
        }
        String value = text(source, "id");
        if (value != null) {
            return value;
        }
        List<String> names = new ArrayList<>();
        source.fieldNames().forEachRemaining(names::add);
        for (String name : names) {
            if (name.endsWith("_id") || name.endsWith("Id")) {
                String candidate = text(source, name);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Puts a value under both spellings — the shape {@code documentRow} established. */
    public static void putBoth(Map<String, Object> attributes, String snake, String camel, Object value) {
        attributes.put(snake, value);
        attributes.put(camel, value);
    }

    /** Reads a field as text, treating absent, null and blank alike so callers get one check. */
    public static String text(JsonNode node, String key) {
        if (node == null || !node.hasNonNull(key)) {
            return null;
        }
        String value = node.get(key).asText();
        return value.isBlank() ? null : value;
    }

    private static String toCamel(String snake) {
        StringBuilder out = new StringBuilder(snake.length());
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                out.append(Character.toUpperCase(c));
                upper = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String toSnake(String camel) {
        StringBuilder out = new StringBuilder(camel.length() + 4);
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                out.append('_').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
