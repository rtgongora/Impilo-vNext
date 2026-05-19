package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DataWarehouseServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.queue.QueueStatsAggregator;

import java.util.*;

/**
 * Cross-facility operational snapshots for district / provincial / national oversight.
 * Uses live PCT queue summaries for batch snapshots; national KPIs use warehouse aggregates only (patient-free).
 */
@RestController
@RequestMapping("/internal/v1/operations")
public class FacilityOperationsAggregateController {

    private static final Logger log = LoggerFactory.getLogger(FacilityOperationsAggregateController.class);
    private static final int MAX_FACILITIES = 40;

    private final PctServiceClient pctClient;
    private final DataWarehouseServiceClient dataWarehouseServiceClient;

    public FacilityOperationsAggregateController(PctServiceClient pctClient,
                                                 DataWarehouseServiceClient dataWarehouseServiceClient) {
        this.pctClient = pctClient;
        this.dataWarehouseServiceClient = dataWarehouseServiceClient;
    }

    /**
     * Patient-free national / programme KPI contract backed by the data warehouse (gold stats and related aggregates).
     * Intentionally distinct from live {@link #facilityQueueSnapshots} PCT snapshots — no per-patient identifiers.
     */
    @GetMapping("/national-kpis")
    public ResponseEntity<Map<String, Object>> nationalKpis(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        try {
            JsonNode stats = dataWarehouseServiceClient.goldStats();
            if (stats == null || stats.isNull()) {
                throw new IllegalStateException("data-warehouse-service returned empty gold stats payload");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("contract", "national-patient-free-kpis-v1");
            data.put("description", "Warehouse-backed aggregates only; not live PCT queue snapshots.");
            data.put("gold_stats", stats);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta));
        } catch (Exception e) {
            log.debug("Warehouse gold stats unavailable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of(
                            "code", "DATA_WAREHOUSE_UNAVAILABLE",
                            "message", "Unable to compute national KPIs while data-warehouse-service is unavailable"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
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
        List<String> unavailableFacilities = new ArrayList<>();
        for (String fid : facilityIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("facility_id", fid);
            try {
                JsonNode queues = pctClient.listQueues(UUID.fromString(fid), null);
                row.putAll(QueueStatsAggregator.fromPctQueueList(queues));
                row.put("source", "PCT");
            } catch (Exception e) {
                log.debug("PCT queue snapshot failed for facility {}: {}", fid, e.getMessage());
                unavailableFacilities.add(fid);
                row.put("source", "UNAVAILABLE");
            }
            rows.add(row);
        }

        if (!unavailableFacilities.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of(
                            "code", "PCT_QUEUE_UNAVAILABLE",
                            "message", "Unable to build facility queue snapshots for one or more facilities"),
                    "details", Map.of("failed_facility_ids", unavailableFacilities),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
