package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.ButanoServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CitizenHealthSummaryService {

    private final ButanoServiceClient butanoClient;
    private final PctServiceClient pctClient;

    public CitizenHealthSummaryService(ButanoServiceClient butanoClient, PctServiceClient pctClient) {
        this.butanoClient = butanoClient;
        this.pctClient = pctClient;
    }

    public Map<String, Object> buildSummary(String actorId) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            JsonNode pctSummary = pctClient.getPatientHealthSummary(actorId);
            if (pctSummary != null && pctSummary.isObject()) {
                pctSummary.fields().forEachRemaining(e -> data.put(e.getKey(), objectToJava(e.getValue())));
            }
        } catch (Exception ignored) {
            // PCT summary optional when actor is not yet linked to a patient chart
        }

        try {
            JsonNode conditions = pctClient.listConditions(actorId, 0, 50);
            data.put("conditions", jsonToList(conditions));
        } catch (Exception ignored) {
            data.putIfAbsent("conditions", List.of());
        }

        try {
            JsonNode allergies = pctClient.listAllergies(actorId);
            data.put("allergies", jsonToList(allergies));
        } catch (Exception ignored) {
            data.putIfAbsent("allergies", List.of());
        }

        try {
            var ipsResponse = butanoClient.getIpsSummary(actorId);
            if (ipsResponse.getBody() != null && !ipsResponse.getBody().isBlank()) {
                JsonNode ips = new com.fasterxml.jackson.databind.ObjectMapper().readTree(ipsResponse.getBody());
                if (ips.has("data")) {
                    data.put("ips", objectToJava(ips.get("data")));
                } else {
                    data.put("ips", objectToJava(ips));
                }
            }
        } catch (Exception ignored) {
            // BUTANO IPS optional for citizens without CPID linkage
        }
        return data;
    }

    private static List<Object> jsonToList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<Object> out = new ArrayList<>();
            node.forEach(child -> out.add(objectToJava(child)));
            return out;
        }
        if (node.has("content") && node.get("content").isArray()) {
            return jsonToList(node.get("content"));
        }
        if (node.has("items") && node.get("items").isArray()) {
            return jsonToList(node.get("items"));
        }
        if (node.has("data") && node.get("data").isArray()) {
            return jsonToList(node.get("data"));
        }
        return List.of(objectToJava(node));
    }

    private static Object objectToJava(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isInt()) {
            return node.intValue();
        }
        if (node.isLong()) {
            return node.longValue();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            return jsonToList(node);
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> map.put(e.getKey(), objectToJava(e.getValue())));
            return map;
        }
        return node.asText();
    }
}
