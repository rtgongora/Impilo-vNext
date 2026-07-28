package zw.gov.mohcc.impilo.experience.workhome;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkHomeBucketingTest {

    private static Map<String, Object> item(String status, String priority, String dueAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (status != null) m.put("status", status);
        if (priority != null) m.put("priority", priority);
        if (dueAt != null) m.put("due_at", dueAt);
        return m;
    }

    @Test
    void completedStatuses_alwaysBucketToCompletedRegardlessOfPriority() {
        Map<String, Object> item = item("COMPLETED", "URGENT", null);
        Map<String, List<Map<String, Object>>> buckets = WorkHomeBucketing.bucket(List.of(item));
        assertThat(buckets.get(WorkHomeBucketing.COMPLETED)).containsExactly(item);
        assertThat(buckets.get(WorkHomeBucketing.ACT_NOW)).isEmpty();
    }

    @Test
    void urgentPriority_bucketsToActNowEvenWithoutADueDate() {
        Map<String, Object> item = item("IN_PROGRESS", "URGENT", null);
        Map<String, List<Map<String, Object>>> buckets = WorkHomeBucketing.bucket(List.of(item));
        assertThat(buckets.get(WorkHomeBucketing.ACT_NOW)).containsExactly(item);
    }

    @Test
    void plainPendingStatus_isNotTreatedAsAwaitingSomeoneElse() {
        // A bare PENDING referral/dispense-order means "awaiting THIS viewer's action" in this
        // codebase's convention, not "awaiting someone else" — see WorkHomeBucketing's comment.
        Map<String, Object> item = item("PENDING", "MEDIUM", null);
        Map<String, List<Map<String, Object>>> buckets = WorkHomeBucketing.bucket(List.of(item));
        assertThat(buckets.get(WorkHomeBucketing.AWAITING_SOMEONE_ELSE)).isEmpty();
        assertThat(buckets.get(WorkHomeBucketing.FOR_AWARENESS)).containsExactly(item);
    }

    @Test
    void explicitlyAwaitingStatus_bucketsToAwaitingSomeoneElse() {
        Map<String, Object> item = item("AWAITING_APPROVAL", "MEDIUM", null);
        Map<String, List<Map<String, Object>>> buckets = WorkHomeBucketing.bucket(List.of(item));
        assertThat(buckets.get(WorkHomeBucketing.AWAITING_SOMEONE_ELSE)).containsExactly(item);
    }

    @Test
    void dueToday_bucketsToActToday_dueLater_bucketsToUpcoming() {
        String dueToday = Instant.now().toString();
        String dueNextWeek = Instant.now().plus(7, ChronoUnit.DAYS).toString();

        Map<String, Object> today = item("OPEN", "MEDIUM", dueToday);
        Map<String, Object> later = item("OPEN", "MEDIUM", dueNextWeek);

        Map<String, List<Map<String, Object>>> buckets = WorkHomeBucketing.bucket(List.of(today, later));

        assertThat(buckets.get(WorkHomeBucketing.ACT_TODAY)).containsExactly(today);
        assertThat(buckets.get(WorkHomeBucketing.UPCOMING)).containsExactly(later);
    }

    @Test
    void highPriorityNoDueDate_bucketsToActToday() {
        Map<String, Object> item = item("OPEN", "HIGH", null);
        Map<String, List<Map<String, Object>>> buckets = WorkHomeBucketing.bucket(List.of(item));
        assertThat(buckets.get(WorkHomeBucketing.ACT_TODAY)).containsExactly(item);
    }

    @Test
    void lowPriorityNoDueDate_bucketsToForAwareness() {
        Map<String, Object> item = item("OPEN", "LOW", null);
        Map<String, List<Map<String, Object>>> buckets = WorkHomeBucketing.bucket(List.of(item));
        assertThat(buckets.get(WorkHomeBucketing.FOR_AWARENESS)).containsExactly(item);
    }

    @Test
    void bucket_alwaysReturnsAllSixKeysEvenWhenEmpty() {
        Map<String, List<Map<String, Object>>> buckets = WorkHomeBucketing.bucket(List.of());
        assertThat(buckets.keySet()).containsExactlyInAnyOrderElementsOf(WorkHomeBucketing.BUCKET_ORDER);
        assertThat(buckets.values()).allMatch(List::isEmpty);
    }
}
