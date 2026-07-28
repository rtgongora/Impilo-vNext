package zw.gov.mohcc.impilo.experience.workhome;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One Work Home section (Phase E3/E4) — always present in the response for every section an
 * adapter declares, whatever its downstream's health. {@code OK}/{@code EMPTY} are drawn from
 * real data; {@code DEGRADED} means the downstream did not answer in time or threw, and is
 * expressed here, never as an HTTP 5xx on the whole composition.
 *
 * @param status OK | EMPTY | DEGRADED
 * @param note   human-readable reason for EMPTY/DEGRADED, null for OK
 */
public record WorkHomeSection(
        String sectionId,
        String title,
        String status,
        List<Map<String, Object>> items,
        Map<String, List<Map<String, Object>>> buckets,
        Map<String, Object> summary,
        String note,
        String generatedAt
) {
    public static WorkHomeSection ok(String sectionId, String title, List<Map<String, Object>> items,
                                      Map<String, Object> summary) {
        boolean empty = items.isEmpty();
        return new WorkHomeSection(sectionId, title, empty ? "EMPTY" : "OK", items,
                WorkHomeBucketing.bucket(items), summary, empty ? "No items to show" : null,
                Instant.now().toString());
    }

    public static WorkHomeSection empty(String sectionId, String title, String reason) {
        return new WorkHomeSection(sectionId, title, "EMPTY", List.of(), WorkHomeBucketing.bucket(List.of()),
                Map.of("total", 0), reason, Instant.now().toString());
    }

    public static WorkHomeSection degraded(String sectionId, String title, String reason) {
        return new WorkHomeSection(sectionId, title, "DEGRADED", List.of(), WorkHomeBucketing.bucket(List.of()),
                Map.of("total", 0), reason, Instant.now().toString());
    }
}
