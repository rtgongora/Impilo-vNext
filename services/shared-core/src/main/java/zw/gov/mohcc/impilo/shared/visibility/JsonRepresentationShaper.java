package zw.gov.mohcc.impilo.shared.visibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Applies field suppression, masking, and pseudonymisation hints to a JSON tree.
 */
public final class JsonRepresentationShaper {

    private JsonRepresentationShaper() {
    }

    public static JsonNode shape(ObjectMapper mapper, Object value, VisibilityProfile profile) throws Exception {
        if (profile == null) {
            return mapper.valueToTree(value);
        }
        JsonNode tree = mapper.valueToTree(value);
        if (tree == null || tree.isNull()) {
            return tree;
        }
        if (profile.suppressFields() != null) {
            for (String path : profile.suppressFields()) {
                removePath(tree, path);
            }
        }
        if (profile.pseudonymiseFields() != null) {
            for (String path : profile.pseudonymiseFields()) {
                pseudonymisePath(tree, path);
            }
        }
        if (profile.piiAccess() != null
                && (profile.piiAccess().equalsIgnoreCase("MASKED")
                || profile.piiAccess().equalsIgnoreCase("NONE"))) {
            applyMaskList(tree, List.of(
                    "givenName", "familyName", "email", "phone", "nationalId",
                    "nationality", "dateOfBirth", "addressLine1", "addressLine2"));
        }
        return tree;
    }

    private static void removePath(JsonNode root, String dottedPath) {
        String[] parts = dottedPath.split("\\.");
        if (parts.length == 0) {
            return;
        }
        JsonNode cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (cur == null || !cur.has(parts[i])) {
                return;
            }
            cur = cur.get(parts[i]);
        }
        if (cur instanceof ObjectNode on) {
            on.remove(parts[parts.length - 1]);
        }
    }

    private static void pseudonymisePath(JsonNode root, String dottedPath) {
        String[] parts = dottedPath.split("\\.");
        JsonNode cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (cur == null || !cur.has(parts[i])) {
                return;
            }
            cur = cur.get(parts[i]);
        }
        if (cur instanceof ObjectNode on && on.has(parts[parts.length - 1])) {
            JsonNode val = on.get(parts[parts.length - 1]);
            if (val != null && val.isTextual()) {
                String pseudo = "pid:" + shortHash(val.asText());
                on.set(parts[parts.length - 1], TextNode.valueOf(pseudo));
            }
        }
    }

    private static void applyMaskList(JsonNode node, List<String> fieldNames) {
        if (node instanceof ObjectNode on) {
            for (String f : fieldNames) {
                if (on.has(f) && !on.get(f).isNull()) {
                    on.set(f, TextNode.valueOf("***"));
                }
            }
            on.fields().forEachRemaining(e -> applyMaskList(e.getValue(), fieldNames));
        } else if (node instanceof ArrayNode an) {
            for (JsonNode child : an) {
                applyMaskList(child, fieldNames);
            }
        }
    }

    private static String shortHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
