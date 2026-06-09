package zw.gov.mohcc.impilo.booking.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.booking.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.booking.persistence.repository.EventOutboxRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/internal/outbox")
public class BookingOutboxInternalController {

    private final EventOutboxRepository outboxRepository;

    public BookingOutboxInternalController(EventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> listEvents(
            @RequestParam(name = "afterId", defaultValue = "0") long afterId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {

        int capped = Math.min(Math.max(limit, 1), 500);
        List<EventOutboxEntity> events =
                outboxRepository.findByIdGreaterThanOrderByIdAsc(afterId, PageRequest.of(0, capped));

        List<Map<String, Object>> rows = events.stream().map(this::toDto).toList();
        long nextAfterId = events.isEmpty() ? afterId : events.get(events.size() - 1).getId();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", rows);
        body.put("meta", Map.of("afterId", afterId, "nextAfterId", nextAfterId, "count", rows.size()));
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> toDto(EventOutboxEntity event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.getId());
        row.put("aggregateType", event.getAggregateType());
        row.put("aggregateId", event.getAggregateId());
        row.put("eventType", event.getEventType());
        row.put("payload", event.getPayload());
        row.put("createdAt", event.getCreatedAt() != null ? event.getCreatedAt().toString() : null);
        row.put("publishedAt", event.getPublishedAt() != null ? event.getPublishedAt().toString() : null);
        return row;
    }
}
