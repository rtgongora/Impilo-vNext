package zw.gov.mohcc.impilo.tshepo.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LegacyRouteUsageTracker {

    private final ConcurrentHashMap<String, AtomicLong> byRoute = new ConcurrentHashMap<>();
    private final Deque<Map<String, Object>> recentEvents = new ArrayDeque<>();
    private volatile Instant lastSeenAt;

    public synchronized void record(
            String routeKey,
            String actorId,
            String callerService,
            String correlationId,
            Instant timestamp) {
        byRoute.computeIfAbsent(routeKey, ignored -> new AtomicLong(0)).incrementAndGet();
        Instant eventTime = timestamp != null ? timestamp : Instant.now();
        lastSeenAt = eventTime;

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("routeKey", routeKey);
        event.put("actorId", actorId);
        event.put("callerService", callerService);
        event.put("correlationId", correlationId);
        event.put("timestamp", eventTime.toString());
        recentEvents.addFirst(event);
        while (recentEvents.size() > 200) {
            recentEvents.removeLast();
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        byRoute.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue().get()));
        map.put("routes", counts);
        map.put("lastSeenAt", lastSeenAt != null ? lastSeenAt.toString() : null);
        map.put("totalLegacyHits", counts.values().stream().mapToLong(Long::longValue).sum());
        map.put("recentEvents", new ArrayList<>(recentEvents));
        return map;
    }
}
