package zw.gov.mohcc.impilo.experience.workhome;

import zw.gov.mohcc.impilo.experience.worklist.WorklistRanking;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Six-bucket classification (Phase E4): Act now / Act today / Awaiting someone else / Upcoming
 * / For awareness / Completed. Partitions the already-ranked item list rather than re-ranking —
 * {@link WorklistRanking} decides order, this decides which shelf an item sits on.
 *
 * <p>Generic, item-shape-driven (status/priority/due_at), because every family adapter emits
 * items in the same shape {@code ClinicalWorklistComposer} already established. A family
 * adapter that needs finer control can set {@code status}/{@code priority}/{@code due_at}
 * deliberately rather than this class special-casing a domain.</p>
 */
public final class WorkHomeBucketing {

    private WorkHomeBucketing() {}

    public static final String ACT_NOW = "ACT_NOW";
    public static final String ACT_TODAY = "ACT_TODAY";
    public static final String AWAITING_SOMEONE_ELSE = "AWAITING_SOMEONE_ELSE";
    public static final String UPCOMING = "UPCOMING";
    public static final String FOR_AWARENESS = "FOR_AWARENESS";
    public static final String COMPLETED = "COMPLETED";

    public static final List<String> BUCKET_ORDER =
            List.of(ACT_NOW, ACT_TODAY, AWAITING_SOMEONE_ELSE, UPCOMING, FOR_AWARENESS, COMPLETED);

    private static final Set<String> COMPLETED_STATUSES =
            Set.of("COMPLETED", "CLOSED", "RESOLVED", "DISCHARGED", "ENDED", "CANCELLED", "VOIDED");

    // Deliberately excludes a bare "PENDING" (and "PENDING_APPROVAL") — most items in this
    // codebase default to that status precisely because they need THIS viewer's action
    // (an incoming referral, a dispense workflow), the opposite of "awaiting someone else".
    // Adapters where a PENDING item genuinely awaits another party set an unambiguous status
    // instead (e.g. facility-admin-claims uses "PENDING_MY_APPROVAL" for the reverse case).
    private static final Set<String> AWAITING_STATUSES = Set.of(
            "AWAITING_APPROVAL", "PENDING_REVIEW", "ESCALATED",
            "REFERRED", "AWAITING_RESPONSE", "PENDING_REVOCATION");

    public static String bucketOf(Map<String, Object> item, long endOfTodayMillis) {
        String status = text(item.get("status"));
        String normalizedStatus = status == null ? null : status.toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && COMPLETED_STATUSES.contains(normalizedStatus)) {
            return COMPLETED;
        }

        int priorityRank = WorklistRanking.priorityRank(text(item.get("priority")));
        if (priorityRank == 0) {
            return ACT_NOW;
        }

        if (normalizedStatus != null && AWAITING_STATUSES.contains(normalizedStatus)) {
            return AWAITING_SOMEONE_ELSE;
        }

        long dueAtMillis = WorklistRanking.epochMillis(text(item.get("due_at")));
        if (dueAtMillis > 0) {
            return dueAtMillis <= endOfTodayMillis ? ACT_TODAY : UPCOMING;
        }

        if (priorityRank == 1) {
            return ACT_TODAY;
        }

        return FOR_AWARENESS;
    }

    public static Map<String, List<Map<String, Object>>> bucket(List<Map<String, Object>> items) {
        Instant now = Instant.now();
        long endOfTodayMillis = now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        Map<String, List<Map<String, Object>>> buckets = new LinkedHashMap<>();
        for (String bucket : BUCKET_ORDER) {
            buckets.put(bucket, new ArrayList<>());
        }
        for (Map<String, Object> item : items) {
            buckets.get(bucketOf(item, endOfTodayMillis)).add(item);
        }
        return buckets;
    }

    private static String text(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        return s.isBlank() ? null : s;
    }
}
