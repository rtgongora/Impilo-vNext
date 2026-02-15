package zw.gov.mohcc.impilo.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TransformService {

    private static final Logger log = LoggerFactory.getLogger(TransformService.class);

    private final ObjectMapper objectMapper;

    public TransformService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Applies header mapping rules: renames header keys according to the rules map.
     * Each rule entry maps an old header name to a new header name.
     */
    public Map<String, String> applyHeaderTransform(Map<String, String> headers, Map<String, String> rules) {
        if (headers == null || rules == null || rules.isEmpty()) {
            return headers;
        }
        Map<String, String> result = new LinkedHashMap<>(headers);
        for (Map.Entry<String, String> rule : rules.entrySet()) {
            String value = result.remove(rule.getKey());
            if (value != null) {
                result.put(rule.getValue(), value);
            }
        }
        return result;
    }

    /**
     * Applies JSON field rename rules: renames top-level fields in a JSON body.
     * Each rule entry maps an old field name to a new field name.
     * Returns the original body if it is null, empty, or not valid JSON.
     */
    public String applyFieldRenames(String body, Map<String, String> rules) {
        if (body == null || body.isBlank() || rules == null || rules.isEmpty()) {
            return body;
        }
        try {
            JsonNode tree = objectMapper.readTree(body);
            if (!tree.isObject()) {
                return body;
            }
            ObjectNode node = (ObjectNode) tree;
            for (Map.Entry<String, String> rule : rules.entrySet()) {
                JsonNode value = node.remove(rule.getKey());
                if (value != null) {
                    node.set(rule.getValue(), value);
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Failed to apply field renames to body, returning original", e);
            return body;
        }
    }
}
