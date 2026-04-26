package zw.gov.mohcc.impilo.experience.queue;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates PCT per-queue counters (from {@code GET /v1/queues?facilityId=}) into the
 * Experience contract consumed by {@code useFacilityQueueStats} in the UI.
 */
public final class QueueStatsAggregator {

    private QueueStatsAggregator() {
    }

    /**
     * @param queues PCT {@code data} array from listQueues — each row may embed
     *               {@code waitingCount}, {@code calledCount}, {@code inServiceCount},
     *               {@code completedCount}, {@code avgWaitMinutes}.
     */
    public static Map<String, Object> fromPctQueueList(JsonNode queues) {
        long waiting = 0;
        long called = 0;
        long inService = 0;
        long completed = 0;
        long noShow = 0;
        double weightedAvgMinutesNumerator = 0;
        long weightedAvgMinutesDenominator = 0;

        if (queues != null && queues.isArray()) {
            for (JsonNode q : queues) {
                long w = q.path("waitingCount").asLong(0);
                waiting += w;
                called += q.path("calledCount").asLong(0);
                inService += q.path("inServiceCount").asLong(0);
                completed += q.path("completedCount").asLong(0);
                noShow += q.path("noShowCount").asLong(0);
                double avgMin = q.path("avgWaitMinutes").asDouble(0);
                if (w > 0 && avgMin > 0) {
                    weightedAvgMinutesNumerator += avgMin * w;
                    weightedAvgMinutesDenominator += w;
                }
            }
        }

        double avgMin = weightedAvgMinutesDenominator > 0
                ? weightedAvgMinutesNumerator / weightedAvgMinutesDenominator
                : 0;
        int avgWaitSeconds = (int) Math.round(avgMin * 60.0);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("waiting", waiting);
        out.put("called", called);
        out.put("inService", inService);
        out.put("completed", completed);
        out.put("noShow", noShow);
        out.put("avgWaitSeconds", avgWaitSeconds);
        return out;
    }
}
