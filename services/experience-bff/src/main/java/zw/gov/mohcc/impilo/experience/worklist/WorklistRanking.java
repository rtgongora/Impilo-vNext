package zw.gov.mohcc.impilo.experience.worklist;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure ranking/summary logic extracted from {@code ClinicalWorklistController}
 * (Phase E, E1) — byte-identical behaviour, now shared by
 * {@code /internal/v1/clinical-worklist} and the Work Home composition layer
 * so there is one ranking implementation, not two.
 */
public final class WorklistRanking {

    private WorklistRanking() {}

    public static int priorityRank(String priority) {
        if (priority == null) return 4;
        return switch (priority.toUpperCase(Locale.ROOT)) {
            case "URGENT", "EMERGENCY", "STAT" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM", "ROUTINE" -> 2;
            case "LOW" -> 3;
            default -> 4;
        };
    }

    public static long epochMillis(String iso) {
        if (iso == null || iso.isBlank()) return 0L;
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /** priority rank ascending (URGENT first), then most-recent-first on created/due/occurred. */
    public static final Comparator<Map<String, Object>> COMPARATOR = (a, b) -> {
        int pa = priorityRank(text(a.get("priority")));
        int pb = priorityRank(text(b.get("priority")));
        if (pa != pb) return Integer.compare(pa, pb);
        long ta = epochMillis(text(a.get("created_at"), text(a.get("due_at"), text(a.get("occurred_at")))));
        long tb = epochMillis(text(b.get("created_at"), text(b.get("due_at"), text(b.get("occurred_at")))));
        return Long.compare(tb, ta);
    };

    public static List<Map<String, Object>> sort(List<Map<String, Object>> items) {
        return items.stream().sorted(COMPARATOR).toList();
    }

    public static Map<String, Object> summarize(List<Map<String, Object>> items) {
        int urgent = 0;
        int overdue = 0;
        Map<String, Integer> bySource = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String p = text(item.get("priority"), "MEDIUM").toUpperCase(Locale.ROOT);
            if ("URGENT".equals(p) || "STAT".equals(p) || "EMERGENCY".equals(p) || "HIGH".equals(p)) {
                urgent++;
            }
            String due = text(item.get("due_at"));
            if (due != null && !due.isBlank() && epochMillis(due) < System.currentTimeMillis()) {
                overdue++;
            }
            String source = text(item.get("source"), "unknown");
            bySource.put(source, bySource.getOrDefault(source, 0) + 1);
        }
        return Map.of(
                "total", items.size(),
                "urgent", urgent,
                "overdue", overdue,
                "by_source", bySource
        );
    }

    static String text(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        return s.isBlank() ? null : s;
    }

    static String text(Object value, String fallback) {
        String s = text(value);
        return s != null ? s : fallback;
    }
}
