package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §11.8 PII-minimisation table — BINDING contract test (OF-B4).
 *
 * <p>Builds the published snapshot from a deliberately rich OROS-order-shaped
 * payload and asserts the serialized broadcast contains NONE of: patient name,
 * CPID, HID, diagnosis text, prescriber identity, address/coordinates, contact
 * details — while keeping the coded lines, quantities and capability flags.</p>
 */
class PublishedSnapshotPiiContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode richOrder() {
        ObjectNode order = mapper.createObjectNode();
        order.put("orderId", "ORD-123");
        order.put("patientName", "Tendai Moyo");
        order.put("cpid", "CPID-9988776655");
        order.put("hid", "HID-2026-000123");
        order.put("healthId", "HID-2026-000123");
        order.put("diagnosis", "Type 2 diabetes mellitus with neuropathy");
        order.put("indication", "poorly controlled glycaemia");
        order.put("clinicalNotes", "Patient reports dizziness; consider dose review");
        order.put("prescriberName", "Dr Rufaro Chikafu");
        order.put("prescriberId", "MDP-44521");
        order.put("address", "14 Samora Machel Ave, Harare");
        order.put("lat", -17.8292);
        order.put("lon", 31.0522);
        order.put("phone", "+263771234567");

        ArrayNode lines = order.putArray("lines");
        ObjectNode line1 = lines.addObject();
        line1.put("lineRef", "L1");
        line1.put("itemCode", "ZIBO-MET-500");
        line1.put("codingSystem", "ZIBO");
        line1.put("quantity", 60);
        line1.put("unit", "TAB");
        line1.put("controlled", false);
        line1.put("coldChain", false);
        line1.put("substitutionAllowed", true);
        // PII smuggled INSIDE a line — must not survive either
        line1.put("patientName", "Tendai Moyo");
        line1.put("diagnosis", "diabetes");
        ObjectNode line2 = lines.addObject();
        line2.put("lineRef", "L2");
        line2.put("itemCode", "ZIBO-INS-GLA");
        line2.put("quantity", 2);
        line2.put("unit", "VIAL");
        line2.put("controlled", false);
        line2.put("coldChain", true);
        return order;
    }

    @Test
    void snapshotFromRichOrder_containsOnlyAllowListedFields() throws Exception {
        String snapshot = PublishedSnapshotBuilder.buildFromOrder(
                mapper, richOrder(), "MEDICATION", "ndila-zone-hre-04", "ROUTINE");

        String lower = snapshot.toLowerCase(Locale.ROOT);
        // §11.8 forbidden elements — none may appear in the broadcast payload
        assertFalse(lower.contains("tendai"), "patient name leaked");
        assertFalse(lower.contains("moyo"), "patient name leaked");
        assertFalse(lower.contains("cpid"), "CPID leaked");
        assertFalse(lower.contains("hid-2026"), "Health ID leaked");
        assertFalse(lower.contains("diabetes"), "diagnosis leaked");
        assertFalse(lower.contains("neuropathy"), "diagnosis leaked");
        assertFalse(lower.contains("glycaemia"), "indication leaked");
        assertFalse(lower.contains("dizziness"), "clinical narrative leaked");
        assertFalse(lower.contains("rufaro"), "prescriber identity leaked");
        assertFalse(lower.contains("chikafu"), "prescriber identity leaked");
        assertFalse(lower.contains("mdp-44521"), "prescriber registration leaked");
        assertFalse(lower.contains("samora"), "address leaked");
        assertFalse(lower.contains("-17.82"), "latitude leaked");
        assertFalse(lower.contains("31.05"), "longitude leaked");
        assertFalse(lower.contains("263771234567"), "contact details leaked");
        assertFalse(lower.contains("\"lat\""), "coordinate field leaked");
        assertFalse(lower.contains("\"lon\""), "coordinate field leaked");
        assertFalse(lower.contains("address"), "address field leaked");
        assertFalse(lower.contains("diagnosis"), "diagnosis field leaked");

        // The allow-listed content IS present
        JsonNode parsed = mapper.readTree(snapshot);
        assertEquals("MEDICATION", parsed.path("profile").asText());
        assertEquals("ndila-zone-hre-04", parsed.path("coarseZone").asText());
        assertEquals("ROUTINE", parsed.path("urgency").asText());
        assertEquals(2, parsed.path("lines").size());
        JsonNode l1 = parsed.path("lines").get(0);
        assertEquals("ZIBO-MET-500", l1.path("itemCode").asText());
        assertEquals(60, l1.path("quantity").asInt());
        assertEquals("TAB", l1.path("unit").asText());
        assertTrue(parsed.path("lines").get(1).path("coldChain").asBoolean());
    }

    @Test
    void typedBuildPath_isEquallyMinimised() throws Exception {
        String snapshot = PublishedSnapshotBuilder.build(mapper, "MEDICATION", "zone-1", "URGENT",
                List.of(new PublishedSnapshotBuilder.RequestLine(
                        "L1", "ZIBO-MOR-10", "ZIBO", 10, "AMP", true, false, false)));
        JsonNode parsed = mapper.readTree(snapshot);
        JsonNode line = parsed.path("lines").get(0);
        // Exactly the allow-listed fields, nothing else
        List<String> fields = new java.util.ArrayList<>();
        line.fieldNames().forEachRemaining(fields::add);
        assertTrue(List.of("lineRef", "itemCode", "codingSystem", "quantity", "unit",
                "controlled", "coldChain", "substitutionAllowed").containsAll(fields),
                "unexpected fields in published line: " + fields);
        assertTrue(line.path("controlled").asBoolean());
    }

    @Test
    void uncodedLines_areNeverPublished() throws Exception {
        ObjectNode order = mapper.createObjectNode();
        ArrayNode lines = order.putArray("lines");
        ObjectNode freeText = lines.addObject();
        freeText.put("description", "some free-text product with patient details: Tendai Moyo");
        freeText.put("quantity", 1);

        String snapshot = PublishedSnapshotBuilder.buildFromOrder(mapper, order, "MEDICATION", null, null);
        JsonNode parsed = mapper.readTree(snapshot);
        assertEquals(0, parsed.path("lines").size(), "uncoded line must be dropped (§4.5 no free-text products)");
        assertFalse(snapshot.contains("Tendai"));
    }
}
