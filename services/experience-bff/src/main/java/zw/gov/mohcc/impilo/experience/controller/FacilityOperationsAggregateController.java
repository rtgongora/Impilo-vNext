package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.queue.QueueStatsAggregator;

import java.util.*;

/**
 * Cross-facility operational snapshots for district / provincial / national oversight.
 * Uses live PCT queue summaries only — no fabricated KPIs.
 */
@RestController
@RequestMapping("/internal/v1/operations")
public class FacilityOperationsAggregateController {

    private static final Logger log = LoggerFactory.getLogger(FacilityOperationsAggregateController.class);
    private static final int MAX_FACILITIES = 40;

    private final PctServiceClient pctClient;

    public FacilityOperationsAggregateController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @PostMapping("/facility-queue-snapshots")
    public ResponseEntity<Map<String, Object>> facilityQueueSnapshots(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        List<String> facilityIds = parseFacilityIds(body != null ? body.get("facility_ids") : null);
        if (facilityIds.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "facility_ids must be a non-empty array of UUID strings (max " + MAX_FACILITIES + ")");
        }
        if (facilityIds.size() > MAX_FACILITIES) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "facility_ids exceeds maximum of " + MAX_FACILITIES);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String fid : facilityIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("facility_id", fid);
            try {
                JsonNode queues = pctClient.listQueues(UUID.fromString(fid), null);
                row.putAll(QueueStatsAggregator.fromPctQueueList(queues));
                row.put("source", "PCT");
            } catch (Exception e) {
                log.debug("PCT queue snapshot failed for facility {}: {}", fid, e.getMessage());
                row.put("waiting", 0L);
                row.put("called", 0L);
                row.put("inService", 0L);
                row.put("completed", 0L);
                row.put("noShow", 0L);
                row.put("avgWaitSeconds", 0);
                row.put("source", "UNAVAILABLE");
                row.put("error", "PCT unreachable or invalid facility id");
            }
            rows.add(row);
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        meta.put("facility_count", rows.size());

        return ResponseEntity.ok(Map.of("data", rows, "meta", meta));
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseFacilityIds(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                String s = o.toString().trim();
                if (!s.isBlank()) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
