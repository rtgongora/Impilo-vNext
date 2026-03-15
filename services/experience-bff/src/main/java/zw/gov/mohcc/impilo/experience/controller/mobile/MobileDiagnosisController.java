package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile diagnosis endpoints.
 * GET    /internal/v1/mobile/provider/diagnosis/icd11/search?q= - ICD-11 search
 * POST   /internal/v1/mobile/provider/diagnosis                 - record diagnosis
 * GET    /internal/v1/mobile/provider/diagnosis?encounter_id=   - list diagnoses
 * DELETE /internal/v1/mobile/provider/diagnosis/{id}            - delete diagnosis
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/diagnosis")
public class MobileDiagnosisController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileDiagnosisController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record RecordDiagnosisRequest(
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String icd11_code,
            @NotBlank String icd11_title,
            String diagnosis_type,
            String certainty,
            String notes
    ) {}

    @GetMapping("/icd11/search")
    public ResponseEntity<Map<String, Object>> searchIcd11(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "20") int limit) {

        int safeLimit = Math.min(limit, 100);
        String searchPattern = "%" + query.toLowerCase() + "%";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, code, title, chapter, block, is_residual
            FROM icd11_codes
            WHERE LOWER(code) LIKE ? OR LOWER(title) LIKE ?
            ORDER BY
                CASE WHEN LOWER(code) = ? THEN 0
                     WHEN LOWER(code) LIKE ? THEN 1
                     ELSE 2
                END,
                code
            LIMIT ?
            """, searchPattern, searchPattern, query.toLowerCase(), query.toLowerCase() + "%", safeLimit);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("code", row.get("code"));
            attributes.put("title", row.get("title"));
            attributes.put("chapter", row.get("chapter"));
            attributes.put("block", row.get("block"));
            attributes.put("is_residual", row.get("is_residual"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "ICD11Code");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "result_count", data.size()
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> recordDiagnosis(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordDiagnosisRequest request) {

        UUID diagnosisId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        String diagnosisType = request.diagnosis_type() != null ? request.diagnosis_type() : "PRIMARY";
        String certainty = request.certainty() != null ? request.certainty() : "CONFIRMED";

        jdbcTemplate.update("""
            INSERT INTO diagnoses
                (id, tenant_id, encounter_id, patient_id, icd11_code, icd11_title,
                 diagnosis_type, certainty, notes, diagnosed_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                diagnosisId, tenantId, request.encounter_id(), request.patient_id(),
                request.icd11_code(), request.icd11_title(),
                diagnosisType, certainty, request.notes(),
                now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.diagnosis.recorded.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Diagnosis",
                diagnosisId.toString(),
                Map.of(
                        "diagnosis_id", diagnosisId.toString(),
                        "encounter_id", request.encounter_id(),
                        "patient_id", request.patient_id(),
                        "icd11_code", request.icd11_code(),
                        "icd11_title", request.icd11_title(),
                        "diagnosis_type", diagnosisType,
                        "certainty", certainty
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("patient_id", request.patient_id());
        attributes.put("icd11_code", request.icd11_code());
        attributes.put("icd11_title", request.icd11_title());
        attributes.put("diagnosis_type", diagnosisType);
        attributes.put("certainty", certainty);
        attributes.put("notes", request.notes());
        attributes.put("diagnosed_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", diagnosisId.toString(),
                "type", "Diagnosis",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listDiagnoses(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "encounter_id") String encounterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, encounter_id, patient_id, icd11_code, icd11_title, diagnosis_type,
                   certainty, notes, diagnosed_at, created_at, updated_at
            FROM diagnoses
            WHERE tenant_id = ? AND encounter_id = ?::uuid
            ORDER BY diagnosed_at DESC
            LIMIT ? OFFSET ?
            """, tenantId, encounterId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM diagnoses WHERE tenant_id = ? AND encounter_id = ?::uuid
            """, Long.class, tenantId, encounterId);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("encounter_id", row.get("encounter_id"));
            attributes.put("patient_id", row.get("patient_id"));
            attributes.put("icd11_code", row.get("icd11_code"));
            attributes.put("icd11_title", row.get("icd11_title"));
            attributes.put("diagnosis_type", row.get("diagnosis_type"));
            attributes.put("certainty", row.get("certainty"));
            attributes.put("notes", row.get("notes"));
            attributes.put("diagnosed_at", row.get("diagnosed_at"));
            attributes.put("created_at", row.get("created_at"));
            attributes.put("updated_at", row.get("updated_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "Diagnosis");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteDiagnosis(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        int deleted = jdbcTemplate.update("""
            DELETE FROM diagnoses WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (deleted == 0) {
            throw new ResourceNotFoundException("Diagnosis not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.diagnosis.deleted.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Diagnosis",
                id.toString(),
                Map.of("diagnosis_id", id.toString()),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "deleted", true));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
