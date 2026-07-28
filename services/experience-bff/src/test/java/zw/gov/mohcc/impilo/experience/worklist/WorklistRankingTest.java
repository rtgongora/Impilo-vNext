package zw.gov.mohcc.impilo.experience.worklist;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorklistRankingTest {

    private static Map<String, Object> item(String priority, String createdAt, String source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("priority", priority);
        m.put("created_at", createdAt);
        m.put("source", source);
        return m;
    }

    @Test
    void priorityRank_ordersUrgentBeforeHighBeforeMediumBeforeLowBeforeUnknown() {
        assertThat(WorklistRanking.priorityRank("URGENT")).isEqualTo(0);
        assertThat(WorklistRanking.priorityRank("STAT")).isEqualTo(0);
        assertThat(WorklistRanking.priorityRank("EMERGENCY")).isEqualTo(0);
        assertThat(WorklistRanking.priorityRank("HIGH")).isEqualTo(1);
        assertThat(WorklistRanking.priorityRank("MEDIUM")).isEqualTo(2);
        assertThat(WorklistRanking.priorityRank("ROUTINE")).isEqualTo(2);
        assertThat(WorklistRanking.priorityRank("LOW")).isEqualTo(3);
        assertThat(WorklistRanking.priorityRank("SOMETHING_ELSE")).isEqualTo(4);
        assertThat(WorklistRanking.priorityRank(null)).isEqualTo(4);
    }

    @Test
    void epochMillis_parsesIsoAndFailsClosedToZero() {
        assertThat(WorklistRanking.epochMillis(null)).isEqualTo(0L);
        assertThat(WorklistRanking.epochMillis("")).isEqualTo(0L);
        assertThat(WorklistRanking.epochMillis("not-a-date")).isEqualTo(0L);
        Instant now = Instant.parse("2026-07-28T10:00:00Z");
        assertThat(WorklistRanking.epochMillis(now.toString())).isEqualTo(now.toEpochMilli());
    }

    @Test
    void sort_ordersByPriorityThenMostRecentFirst() {
        Map<String, Object> low = item("LOW", "2026-07-28T09:00:00Z", "pct");
        Map<String, Object> urgentOld = item("URGENT", "2026-07-27T09:00:00Z", "pct");
        Map<String, Object> urgentNew = item("URGENT", "2026-07-28T09:00:00Z", "oros");
        Map<String, Object> medium = item("MEDIUM", "2026-07-28T09:00:00Z", "pharmacy");

        List<Map<String, Object>> sorted = WorklistRanking.sort(List.of(low, medium, urgentOld, urgentNew));

        assertThat(sorted).containsExactly(urgentNew, urgentOld, medium, low);
    }

    @Test
    void summarize_countsUrgentOverdueAndBySource() {
        Map<String, Object> a = item("URGENT", "2026-07-28T09:00:00Z", "pct");
        a.put("due_at", "2020-01-01T00:00:00Z");
        Map<String, Object> b = item("LOW", "2026-07-28T09:00:00Z", "oros");
        Map<String, Object> c = item("HIGH", "2026-07-28T09:00:00Z", "pct");

        Map<String, Object> summary = WorklistRanking.summarize(List.of(a, b, c));

        assertThat(summary.get("total")).isEqualTo(3);
        assertThat(summary.get("urgent")).isEqualTo(2);
        assertThat(summary.get("overdue")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Integer> bySource = (Map<String, Integer>) summary.get("by_source");
        assertThat(bySource).containsEntry("pct", 2).containsEntry("oros", 1);
    }

    @Test
    void summarize_emptyList_isZeroed() {
        Map<String, Object> summary = WorklistRanking.summarize(List.of());
        assertThat(summary.get("total")).isEqualTo(0);
        assertThat(summary.get("urgent")).isEqualTo(0);
        assertThat(summary.get("overdue")).isEqualTo(0);
    }
}
