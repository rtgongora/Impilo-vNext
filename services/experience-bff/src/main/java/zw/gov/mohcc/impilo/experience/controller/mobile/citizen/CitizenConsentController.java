package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Citizen consent management endpoints.
 * GET  /internal/v1/mobile/citizen/consents?patient_id= — list consent preferences.
 * PUT  /internal/v1/mobile/citizen/consents — update a consent preference.
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/consents")
public class CitizenConsentController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public CitizenConsentController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record UpdateConsentRequest(
            @NotBlank String patient_id,
            @NotBlank String consent_type,
            @NotNull Boolean granted
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listConsents(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, consent_type, granted, description, updated_at, created_at
            FROM consent_preferences
            WHERE tenant_id = ? AND patient_id = ?::uuid
            ORDER BY consent_type
            """, tenantId, patientId);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("consent_type", row.get("consent_type"));
            attributes.put("granted", row.get("granted"));
            attributes.put("description", row.get("description"));
            attributes.put("updated_at", row.get("updated_at"));
            attributes.put("created_at", row.get("created_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "ConsentPreference");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PutMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> updateConsent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody UpdateConsentRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO consent_preferences (id, tenant_id, patient_id, consent_type, granted, updated_at, created_at)
            VALUES (gen_random_uuid(), ?, ?::uuid, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, patient_id, consent_type)
            DO UPDATE SET granted = EXCLUDED.granted, updated_at = EXCLUDED.updated_at
            """, tenantId, request.patient_id(), request.consent_type(),
                request.granted(), now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.consent.updated.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "ConsentPreference",
                request.patient_id() + ":" + request.consent_type(),
                Map.of(
                        "patient_id", request.patient_id(),
                        "consent_type", request.consent_type(),
                        "granted", request.granted().toString()
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("consent_type", request.consent_type());
        attributes.put("granted", request.granted());
        attributes.put("updated_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", request.patient_id() + ":" + request.consent_type(),
                "type", "ConsentPreference",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
