package zw.gov.mohcc.impilo.rito.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds and validates facility-readiness task payloads raised from an MPDSR review.
 *
 * <p>A maternal death review MAY PROMPT a readiness assessment; it MUST NOT BE one. No case content
 * crosses — enforced by {@link #assertSanitized(Map)} and the contaminated-payload unit test.
 */
public final class MpdsrReadinessFirewall {

    /** Keys that must never appear on an outbound readiness-task payload. */
    public static final Set<String> FORBIDDEN_PAYLOAD_KEYS = Set.of(
            "reviewId", "review_id", "mpdsrReviewId", "mpdsr_review_id",
            "caseId", "case_id", "deathCaseId", "death_case_id",
            "narrative", "findings", "finding", "minutesSummary", "minutes_summary",
            "deathDate", "death_date", "dateOfDeath", "date_of_death",
            "description", "triggerFlags", "trigger_flags", "whoClassification",
            "subjectCpid", "subject_cpid", "title", "committeeNotes", "committee_notes");

    /** Substrings that identify review attribution if they appear in string values. */
    private static final List<String> FORBIDDEN_VALUE_FRAGMENTS = List.of(
            "MPDSR", "death review", "maternal death", "RITO-");

    private MpdsrReadinessFirewall() {
    }

    /**
     * Build the only permitted outbound payload for a facility-readiness reassessment task.
     * Deliberately generic and non-attributable to any specific review.
     */
    public static Map<String, Object> buildSanitizedPayload(String facilityId, String dueAtIso) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskType", "FACILITY_READINESS_REASSESSMENT");
        payload.put("reasonCode", "SCHEDULED_CYCLE");
        payload.put("facilityId", facilityId);
        payload.put("owningRole", "FACILITY_READINESS_COORDINATOR");
        payload.put("method", "EMONC_SIGNAL_FUNCTION");
        if (dueAtIso != null && !dueAtIso.isBlank()) {
            payload.put("dueAt", dueAtIso);
        }
        assertSanitized(payload);
        return payload;
    }

    /**
     * Fail closed if any forbidden key or identifying fragment is present anywhere in the payload tree.
     */
    public static void assertSanitized(Map<String, Object> payload) {
        scanMap(payload, "");
    }

    private static void scanMap(Map<String, Object> map, String prefix) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (FORBIDDEN_PAYLOAD_KEYS.contains(key)) {
                throw new SecurityException("MPDSR firewall: forbidden key in readiness payload: " + key);
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                scanMap(nestedMap, prefix + key + ".");
            } else if (value instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item instanceof Map<?, ?> itemMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nestedMap = (Map<String, Object>) itemMap;
                        scanMap(nestedMap, prefix + key + "[].");
                    } else {
                        scanStringValue(String.valueOf(item), prefix + key);
                    }
                }
            } else if (value != null) {
                scanStringValue(String.valueOf(value), prefix + key);
            }
        }
    }

    private static void scanStringValue(String value, String fieldPath) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String fragment : FORBIDDEN_VALUE_FRAGMENTS) {
            if (lower.contains(fragment.toLowerCase(Locale.ROOT))) {
                throw new SecurityException(
                        "MPDSR firewall: identifying fragment in readiness payload at " + fieldPath);
            }
        }
    }
}
