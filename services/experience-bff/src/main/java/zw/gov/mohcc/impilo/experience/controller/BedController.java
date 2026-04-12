package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Bed and ward management endpoints.
 *
 * GET  /internal/v1/beds/wards — list wards for a facility
 * GET  /internal/v1/beds — list beds with ward/status filter
 * POST /internal/v1/beds/{id}/status — change bed status
 * POST /internal/v1/beds/{id}/assign — assign patient to bed
 * POST /internal/v1/beds/{id}/discharge — discharge patient from bed
 */
@RestController
@RequestMapping("/internal/v1/beds")
public class BedController {

    private static final Logger log = LoggerFactory.getLogger(BedController.class);

    private final JdbcTemplate jdbcTemplate; // TODO: remove after verification
    private final InpatientServiceClient inpatientClient;

    public BedController(JdbcTemplate jdbcTemplate, InpatientServiceClient inpatientClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.inpatientClient = inpatientClient;
    }

    @GetMapping("/wards")
    public ResponseEntity<Map<String, Object>> listWards(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") UUID facilityId) {

        // STRANGLER: delegate to InpatientServiceClient
        try {
            JsonNode data = inpatientClient.listWards(facilityId.toString());
            if (data != null) return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Inpatient listWards failed, falling back to local: {}", e.getMessage());
        }
        // STRANGLER: migrated to InpatientServiceClient — fallback to local JDBC
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT w.*,
                   COUNT(b.id) FILTER (WHERE b.status = 'AVAILABLE') as available_beds,
                   COUNT(b.id) FILTER (WHERE b.status = 'OCCUPIED') as occupied_beds,
                   COUNT(b.id) as actual_beds
            FROM wards w LEFT JOIN beds b ON b.ward_id = w.id
            WHERE w.facility_id = ? AND w.tenant_id = ? AND w.status = 'ACTIVE'
            GROUP BY w.id ORDER BY w.name
            """, facilityId, tenantId);

        List<Map<String, Object>> data = rows.stream().map(row -> Map.<String, Object>of(
                "id", row.get("id").toString(),
                "type", "ward",
                "attributes", Map.of(
                        "name", row.get("name"),
                        "wardType", row.getOrDefault("ward_type", "GENERAL"),
                        "floor", row.getOrDefault("floor", ""),
                        "totalBeds", row.getOrDefault("total_beds", 0),
                        "availableBeds", row.getOrDefault("available_beds", 0L),
                        "occupiedBeds", row.getOrDefault("occupied_beds", 0L),
                        "status", row.getOrDefault("status", "ACTIVE")
                )
        )).toList();

        return ResponseEntity.ok(Map.of("data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listBeds(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(required = false, name = "ward_id") UUID wardId,
            @RequestParam(required = false) String status) {

        // STRANGLER: delegate to InpatientServiceClient
        try {
            JsonNode data = inpatientClient.listBeds(facilityId.toString(), wardId != null ? wardId.toString() : null, status);
            if (data != null) return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Inpatient listBeds failed, falling back to local: {}", e.getMessage());
        }
        // STRANGLER: migrated to InpatientServiceClient — fallback to local JDBC
        StringBuilder sql = new StringBuilder(
                "SELECT b.*, w.name as ward_name FROM beds b JOIN wards w ON b.ward_id = w.id WHERE b.facility_id = ? AND b.tenant_id = ?");
        List<Object> params = new ArrayList<>(List.of(facilityId, tenantId));

        if (wardId != null) {
            sql.append(" AND b.ward_id = ?");
            params.add(wardId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND b.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY w.name, b.bed_number");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("bedNumber", row.get("bed_number"));
            attrs.put("bedType", row.getOrDefault("bed_type", "STANDARD"));
            attrs.put("status", row.get("status"));
            attrs.put("wardId", row.get("ward_id"));
            attrs.put("wardName", row.getOrDefault("ward_name", ""));
            attrs.put("acuity", row.get("acuity"));
            attrs.put("patientId", row.get("patient_id"));
            attrs.put("patientName", row.get("patient_name"));
            attrs.put("admittedAt", row.get("admitted_at"));
            attrs.put("assignedDoctor", row.get("assigned_doctor"));
            return Map.<String, Object>of(
                    "id", row.get("id").toString(),
                    "type", "bed",
                    "attributes", attrs);
        }).toList();

        return ResponseEntity.ok(Map.of("data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/status")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateBedStatus(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, String> body) {

        // STRANGLER: delegate to InpatientServiceClient first
        try { inpatientClient.updateBedStatus(id.toString(), body); } catch (Exception e) { log.warn("Inpatient updateBedStatus failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to InpatientServiceClient — dual-write to local BFF table as backup cache
        String newStatus = body.get("status");
        jdbcTemplate.update(
                "UPDATE beds SET status = ?, updated_at = ? WHERE id = ? AND tenant_id = ?",
                newStatus, OffsetDateTime.now(), id, tenantId);

        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", newStatus),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/assign")
    @Transactional
    public ResponseEntity<Map<String, Object>> assignPatient(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, String> body) {

        // STRANGLER: delegate to InpatientServiceClient first
        try { inpatientClient.assignPatientToBed(id.toString(), body); } catch (Exception e) { log.warn("Inpatient assignPatientToBed failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to InpatientServiceClient — dual-write to local BFF table as backup cache
        String patientId = body.get("patientId");
        String patientName = body.get("patientName");
        String acuity = body.getOrDefault("acuity", "MEDIUM");
        String doctor = body.get("assignedDoctor");
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            UPDATE beds SET status = 'OCCUPIED', patient_id = ?::uuid, patient_name = ?,
                acuity = ?, assigned_doctor = ?, admitted_at = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ?
            """, patientId, patientName, acuity, doctor, now, now, id, tenantId);

        return ResponseEntity.status(HttpStatus.OK).body(Map.of(
                "data", Map.of("id", id.toString(), "status", "OCCUPIED", "patientId", patientId),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/discharge")
    @Transactional
    public ResponseEntity<Map<String, Object>> dischargeBed(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // STRANGLER: delegate to InpatientServiceClient first
        try { inpatientClient.dischargeBed(id.toString()); } catch (Exception e) { log.warn("Inpatient dischargeBed failed (non-blocking): {}", e.getMessage()); }
        // STRANGLER: migrated to InpatientServiceClient — dual-write to local BFF table as backup cache
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
            UPDATE beds SET status = 'CLEANING', patient_id = NULL, patient_name = NULL,
                acuity = NULL, assigned_doctor = NULL, admitted_at = NULL, updated_at = ?
            WHERE id = ? AND tenant_id = ?
            """, now, id, tenantId);

        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "CLEANING"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
