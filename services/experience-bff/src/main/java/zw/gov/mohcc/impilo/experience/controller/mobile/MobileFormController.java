package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile dynamic form endpoints.
 * GET  /internal/v1/mobile/provider/forms                           - list form schemas
 * GET  /internal/v1/mobile/provider/forms/{id}                      - get form schema
 * POST /internal/v1/mobile/provider/forms/submissions               - submit form
 * GET  /internal/v1/mobile/provider/forms/submissions?encounter_id= - list submissions
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/forms")
public class MobileFormController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public MobileFormController(JdbcTemplate jdbcTemplate, OutboxService outboxService,
                                ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    public record SubmitFormRequest(
            @NotBlank String form_id,
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String submitted_by,
            @NotNull Map<String, Object> form_data
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listForms(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows;
        Long total;

        if (category != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, name, description, category, version, status, schema,
                       created_at, updated_at
                FROM form_schemas
                WHERE (tenant_id = ? OR tenant_id IS NULL) AND category = ? AND status = 'ACTIVE'
                ORDER BY name ASC
                LIMIT ? OFFSET ?
                """, tenantId, category, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM form_schemas
                WHERE (tenant_id = ? OR tenant_id IS NULL) AND category = ? AND status = 'ACTIVE'
                """, Long.class, tenantId, category);
        } else {
            rows = jdbcTemplate.queryForList("""
                SELECT id, name, description, category, version, status, schema,
                       created_at, updated_at
                FROM form_schemas
                WHERE (tenant_id = ? OR tenant_id IS NULL) AND status = 'ACTIVE'
                ORDER BY name ASC
                LIMIT ? OFFSET ?
                """, tenantId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM form_schemas
                WHERE (tenant_id = ? OR tenant_id IS NULL) AND status = 'ACTIVE'
                """, Long.class, tenantId);
        }

        List<Map<String, Object>> data = rows.stream().map(this::toFormResource).toList();

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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getForm(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, name, description, category, version, status, schema,
                   created_at, updated_at
            FROM form_schemas
            WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL) AND status = 'ACTIVE'
            """, id, tenantId);

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Form schema not found: " + id);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toFormResource(rows.get(0)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/submissions")
    @Transactional
    public ResponseEntity<Map<String, Object>> submitForm(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody SubmitFormRequest request) {

        UUID submissionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        String formDataJson;
        try {
            formDataJson = objectMapper.writeValueAsString(request.form_data());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize form data", e);
        }

        jdbcTemplate.update("""
            INSERT INTO form_submissions
                (id, tenant_id, form_id, encounter_id, patient_id, submitted_by,
                 form_data, status, submitted_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?::uuid, ?, ?::jsonb, 'SUBMITTED', ?, ?, ?)
            """,
                submissionId, tenantId, request.form_id(), request.encounter_id(),
                request.patient_id(), request.submitted_by(),
                formDataJson, now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.form.submitted.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "FormSubmission",
                submissionId.toString(),
                Map.of(
                        "submission_id", submissionId.toString(),
                        "form_id", request.form_id(),
                        "encounter_id", request.encounter_id(),
                        "patient_id", request.patient_id(),
                        "status", "SUBMITTED"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("form_id", request.form_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("patient_id", request.patient_id());
        attributes.put("submitted_by", request.submitted_by());
        attributes.put("form_data", request.form_data());
        attributes.put("status", "SUBMITTED");
        attributes.put("submitted_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", submissionId.toString(),
                "type", "FormSubmission",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/submissions")
    public ResponseEntity<Map<String, Object>> listSubmissions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "encounter_id") String encounterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT fs.id, fs.form_id, fs.encounter_id, fs.patient_id, fs.submitted_by,
                   fs.form_data, fs.status, fs.submitted_at, fs.created_at, fs.updated_at,
                   fsch.name AS form_name, fsch.category AS form_category
            FROM form_submissions fs
            LEFT JOIN form_schemas fsch ON fs.form_id = fsch.id
            WHERE fs.tenant_id = ? AND fs.encounter_id = ?::uuid
            ORDER BY fs.submitted_at DESC
            LIMIT ? OFFSET ?
            """, tenantId, encounterId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM form_submissions
            WHERE tenant_id = ? AND encounter_id = ?::uuid
            """, Long.class, tenantId, encounterId);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("form_id", row.get("form_id"));
            attributes.put("form_name", row.get("form_name"));
            attributes.put("form_category", row.get("form_category"));
            attributes.put("encounter_id", row.get("encounter_id"));
            attributes.put("patient_id", row.get("patient_id"));
            attributes.put("submitted_by", row.get("submitted_by"));
            attributes.put("form_data", row.get("form_data"));
            attributes.put("status", row.get("status"));
            attributes.put("submitted_at", row.get("submitted_at"));
            attributes.put("created_at", row.get("created_at"));
            attributes.put("updated_at", row.get("updated_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "FormSubmission");
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

    private Map<String, Object> toFormResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", row.get("name"));
        attributes.put("description", row.get("description"));
        attributes.put("category", row.get("category"));
        attributes.put("version", row.get("version"));
        attributes.put("status", row.get("status"));
        attributes.put("schema", row.get("schema"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "FormSchema");
        resource.put("attributes", attributes);
        return resource;
    }
}
