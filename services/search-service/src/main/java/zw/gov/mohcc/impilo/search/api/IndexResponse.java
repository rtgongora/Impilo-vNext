package zw.gov.mohcc.impilo.search.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record IndexResponse(
        String id,
        String entityType,
        String entityId,
        Map<String, Object> contentJson,
        List<String> tags,
        String searchableText,
        OffsetDateTime indexedAt
) {
    /** Concatenated text for lexical ranking heuristics. */
    public String rankingBlob() {
        StringBuilder sb = new StringBuilder();
        if (searchableText != null) {
            sb.append(searchableText).append(' ');
        }
        if (tags != null) {
            for (String t : tags) {
                if (t != null) {
                    sb.append(t).append(' ');
                }
            }
        }
        if (contentJson != null && !contentJson.isEmpty()) {
            sb.append(flattenValues(contentJson));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static String flattenValues(Object node) {
        if (node == null) {
            return "";
        }
        if (node instanceof Map<?, ?> m) {
            StringBuilder b = new StringBuilder();
            for (Object v : m.values()) {
                b.append(flattenValues(v)).append(' ');
            }
            return b.toString();
        }
        if (node instanceof Iterable<?> it) {
            StringBuilder b = new StringBuilder();
            for (Object v : it) {
                b.append(flattenValues(v)).append(' ');
            }
            return b.toString();
        }
        return node.toString();
    }
}
