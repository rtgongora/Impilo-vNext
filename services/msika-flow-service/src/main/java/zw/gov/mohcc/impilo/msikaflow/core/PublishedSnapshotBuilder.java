package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Builds the §11.8 (BINDING) PII-minimised published snapshot for a
 * marketplace request — the ONLY line content competing vendors ever see.
 *
 * <p>Allow-list by construction: every line is rebuilt field-by-field from a
 * closed set. Nothing else survives — not patient name, not CPID/HID, not
 * diagnosis/indication, not prescriber identity, not addresses or coordinates,
 * regardless of how rich the source order payload is.</p>
 */
public final class PublishedSnapshotBuilder {

    /** One coded, PII-free request line (the §8.3 allow-list). */
    public record RequestLine(
            String lineRef,
            String itemCode,
            String codingSystem,
            int quantity,
            String unit,
            boolean controlled,
            boolean coldChain,
            boolean substitutionAllowed
    ) {}

    private PublishedSnapshotBuilder() {}

    /** Builds the snapshot from typed, already-coded lines (the create-API path). */
    public static String build(ObjectMapper mapper, String profile, String coarseZone,
                               String urgency, List<RequestLine> lines) {
        ObjectNode root = mapper.createObjectNode();
        root.put("profile", profile);
        if (coarseZone != null && !coarseZone.isBlank()) {
            root.put("coarseZone", coarseZone);
        }
        if (urgency != null && !urgency.isBlank()) {
            root.put("urgency", urgency);
        }
        ArrayNode out = root.putArray("lines");
        for (RequestLine line : lines) {
            out.add(minimisedLine(mapper, line));
        }
        return root.toString();
    }

    /**
     * Builds the snapshot from a RICH OROS-order-shaped JSON payload. Only the
     * allow-listed fields are extracted per line; everything else in the source
     * (patient identity, diagnosis, prescriber, address…) is discarded by
     * construction. This is the enforcement point the §11.8 contract test pins.
     */
    public static String buildFromOrder(ObjectMapper mapper, JsonNode richOrder, String profile,
                                        String coarseZone, String urgency) {
        ObjectNode root = mapper.createObjectNode();
        root.put("profile", profile);
        if (coarseZone != null && !coarseZone.isBlank()) {
            root.put("coarseZone", coarseZone);
        }
        if (urgency != null && !urgency.isBlank()) {
            root.put("urgency", urgency);
        }
        ArrayNode out = root.putArray("lines");
        JsonNode lines = richOrder.path("lines").isArray() ? richOrder.path("lines")
                : richOrder.path("items");
        int i = 0;
        if (lines.isArray()) {
            for (JsonNode line : lines) {
                i++;
                String itemCode = firstNonBlank(line, "itemCode", "ziboCode", "medicationCode", "code");
                if (itemCode == null) {
                    continue; // an uncoded line is never published — no free-text products (§4.5)
                }
                RequestLine minimised = new RequestLine(
                        firstNonBlank(line, "lineRef", "lineId") != null
                                ? firstNonBlank(line, "lineRef", "lineId") : "L" + i,
                        itemCode,
                        firstNonBlank(line, "codingSystem"),
                        line.path("quantity").asInt(1),
                        firstNonBlank(line, "unit", "quantityUnit", "uom"),
                        line.path("controlled").asBoolean(false),
                        line.path("coldChain").asBoolean(false),
                        line.path("substitutionAllowed").asBoolean(
                                line.path("substitutionPermitted").asBoolean(true)));
                out.add(minimisedLine(mapper, minimised));
            }
        }
        return root.toString();
    }

    private static ObjectNode minimisedLine(ObjectMapper mapper, RequestLine line) {
        ObjectNode node = mapper.createObjectNode();
        node.put("lineRef", line.lineRef());
        node.put("itemCode", line.itemCode());
        if (line.codingSystem() != null && !line.codingSystem().isBlank()) {
            node.put("codingSystem", line.codingSystem());
        }
        node.put("quantity", line.quantity());
        if (line.unit() != null && !line.unit().isBlank()) {
            node.put("unit", line.unit());
        }
        node.put("controlled", line.controlled());
        node.put("coldChain", line.coldChain());
        node.put("substitutionAllowed", line.substitutionAllowed());
        return node;
    }

    private static String firstNonBlank(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode v = node.path(field);
            if (v.isValueNode() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }
}
