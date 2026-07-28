package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Content hashing for configuration definition versions.
 *
 * <p>The hash is what makes "the same version always says the same thing" checkable rather than
 * merely intended. It mirrors the discipline tuso's {@code InspectionContentSeeder} proved: a
 * source pack that changes content under a fixed version fails fast at import instead of silently
 * mutating rules a live case is pinned to.</p>
 *
 * <p>The JSON is canonicalised — object keys sorted recursively — before hashing, so a pack whose
 * fields were merely reordered by an editor does not read as a content change. Array order is
 * preserved, because in configuration an ordered list of workflow stages is not the same
 * configuration as the same stages in a different order.</p>
 */
@Component
public class ConfigContentHasher {

    private final ObjectMapper objectMapper;

    public ConfigContentHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** SHA-256 hex of the canonicalised payload. Lower-case, 64 characters — the column's CHECK. */
    public String hash(JsonNode payload) {
        if (payload == null) {
            throw new IllegalArgumentException("A configuration version must carry a payload");
        }
        try {
            String canonical = objectMapper.writeValueAsString(canonicalise(payload));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash configuration payload", ex);
        }
    }

    private JsonNode canonicalise(JsonNode node) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                names.add(it.next());
            }
            names.sort(String::compareTo);
            ObjectNode sorted = objectMapper.createObjectNode();
            for (String name : names) {
                sorted.set(name, canonicalise(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            node.forEach(child -> copy.add(canonicalise(child)));
            return copy;
        }
        return node;
    }
}
