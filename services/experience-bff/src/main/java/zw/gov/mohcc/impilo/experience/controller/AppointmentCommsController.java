package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.scheduling.AppointmentCommsHistoryStore;

import java.util.List;
import java.util.Map;

/**
 * Audit/history surface for appointment comms — mirrors telemedicine workflow history pattern.
 */
@RestController
@RequestMapping("/internal/v1/appointment-comms")
public class AppointmentCommsController {

    private final AppointmentCommsHistoryStore historyStore;

    public AppointmentCommsController(AppointmentCommsHistoryStore historyStore) {
        this.historyStore = historyStore;
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        List<Map<String, Object>> rows = historyStore.recentHistory(capped);
        return ResponseEntity.ok(Map.of(
                "data", rows,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId, "count", rows.size())));
    }
}
