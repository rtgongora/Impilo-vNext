package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.domain.Referral;
import zw.gov.mohcc.impilo.experience.repository.ReferralRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Referral management endpoints. Delegates to PctServiceClient.
 * GET  /internal/v1/referrals?patient_id= — list referrals for patient (paged).
 * GET  /internal/v1/referrals/{id} — get single referral.
 * POST /internal/v1/referrals — create referral.
 * POST /internal/v1/referrals/{id}/complete — complete referral.
 */
@RestController
@RequestMapping("/internal/v1/referrals")
public class ReferralsController {

    private final ReferralRepository referralRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;
    private final PctServiceClient pctClient;

    public ReferralsController(ReferralRepository referralRepository,
                               OutboxService outboxService,
                               JdbcTemplate jdbcTemplate,
                               PctServiceClient pctClient) {
        this.referralRepository = referralRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
        this.pctClient = pctClient;
    }

    public record CreateReferralRequest(
            @NotBlank String patient_id,
            String encounter_id,
            String referral_type,
            String specialty,
            String referred_to,
            String referred_to_facility,
            @NotBlank String reason,
            String urgency,
            String clinical_summary,
            @NotBlank String referred_by,
            String referred_by_name
    ) {}

    public record CompleteReferralRequest(
            String outcome
    ) {}

    public record AcceptReferralRequest(
            String receiving_facility_id,
            String receiving_facility_name,
            String scheduled_at,
            String notes
    ) {}

    public record RespondReferralRequest(
            @NotBlank String response_notes,
            String outcome
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listReferrals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        int limit = Math.min(size, 100);

        // STRANGLER: migrated — delegate to PctServiceClient
        JsonNode referrals = pctClient.listPatientReferrals(patientId, page, limit);

        // STRANGLER: migrated — was ReferralRepository.findByTenantIdAndPatientId
        // PageRequest pageable = PageRequest.of(page, limit, Sort.by("createdAt").descending());
        // Page<Referral> result = referralRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId), pageable);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", referrals);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/incoming")
    public ResponseEntity<Map<String, Object>> listIncomingReferrals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);

        // STRANGLER: migrated — delegate to PctServiceClient
        JsonNode referrals = pctClient.listIncomingReferrals(facilityId, status, page, limit);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from referrals table
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", referrals);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReferral(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // STRANGLER: migrated — delegate to PctServiceClient
        JsonNode referral = pctClient.getReferral(id.toString());

        // STRANGLER: migrated — was ReferralRepository.findByIdAndTenantId
        // Referral referral = referralRepository.findByIdAndTenantId(id, tenantId)
        //         .orElseThrow(() -> new ResourceNotFoundException("Referral not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", referral);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createReferral(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateReferralRequest request) {

        // STRANGLER: migrated — delegate to PctServiceClient
        Map<String, Object> referralData = new LinkedHashMap<>();
        referralData.put("patient_id", request.patient_id());
        referralData.put("encounter_id", request.encounter_id());
        referralData.put("referral_type", request.referral_type());
        referralData.put("specialty", request.specialty());
        referralData.put("referred_to", request.referred_to());
        referralData.put("referred_to_facility", request.referred_to_facility());
        referralData.put("reason", request.reason());
        referralData.put("urgency", request.urgency() != null ? request.urgency() : "ROUTINE");
        referralData.put("clinical_summary", request.clinical_summary());
        referralData.put("referred_by", request.referred_by());
        referralData.put("referred_by_name", request.referred_by_name());
        referralData.put("tenant_id", tenantId);

        JsonNode result = pctClient.createReferral(referralData);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT into referrals
        // jdbcTemplate.update("""
        //     INSERT INTO referrals (...) VALUES (...)
        //     """, ...);

        outboxService.writeOutboxEvent(
                "impilo.experience.referral.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Referral",
                requestId,
                Map.of(
                        "patient_id", request.patient_id(),
                        "referral_type", request.referral_type() != null ? request.referral_type() : "",
                        "specialty", request.specialty() != null ? request.specialty() : "",
                        "referred_by", request.referred_by(),
                        "status", "PENDING"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/complete")
    @Transactional
    public ResponseEntity<Map<String, Object>> completeReferral(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CompleteReferralRequest request) {

        String outcome = request != null ? request.outcome() : null;

        // STRANGLER: migrated — delegate to PctServiceClient
        Map<String, Object> completeData = new LinkedHashMap<>();
        if (outcome != null) completeData.put("outcome", outcome);
        JsonNode result = pctClient.completeReferral(id.toString(), completeData);

        // STRANGLER: migrated — was ReferralRepository.findByIdAndTenantId + referral.complete() + save
        // Referral referral = referralRepository.findByIdAndTenantId(id, tenantId)...
        // referral.complete(outcome);
        // referralRepository.save(referral);

        outboxService.writeOutboxEvent(
                "impilo.experience.referral.completed.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Referral",
                id.toString(),
                Map.of(
                        "referral_id", id.toString(),
                        "status", "COMPLETED",
                        "outcome", outcome != null ? outcome : ""
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/accept")
    @Transactional
    public ResponseEntity<Map<String, Object>> acceptReferral(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody AcceptReferralRequest request) {

        // STRANGLER: migrated — delegate to PctServiceClient
        Map<String, Object> acceptData = new LinkedHashMap<>();
        acceptData.put("receiving_facility_id", request.receiving_facility_id());
        acceptData.put("receiving_facility_name", request.receiving_facility_name());
        if (request.scheduled_at() != null) acceptData.put("scheduled_at", request.scheduled_at());
        if (request.notes() != null) acceptData.put("notes", request.notes());

        JsonNode result = pctClient.acceptReferral(id.toString(), acceptData);

        // STRANGLER: migrated — was ReferralRepository.findByIdAndTenantId + referral.accept() + save
        // referral.accept(receivingId, request.receiving_facility_name());
        // referralRepository.save(referral);

        outboxService.writeOutboxEvent(
                "impilo.experience.referral.accepted.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "Referral", id.toString(),
                Map.of(
                        "referral_id", id.toString(),
                        "receiving_facility_id", request.receiving_facility_id() != null ? request.receiving_facility_id() : "",
                        "receiving_facility_name", request.receiving_facility_name() != null ? request.receiving_facility_name() : "",
                        "status", "ACCEPTED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/respond")
    @Transactional
    public ResponseEntity<Map<String, Object>> respondReferral(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RespondReferralRequest request) {

        // STRANGLER: migrated — delegate to PctServiceClient
        Map<String, Object> respondData = new LinkedHashMap<>();
        respondData.put("response_notes", request.response_notes());
        if (request.outcome() != null) respondData.put("outcome", request.outcome());

        JsonNode result = pctClient.respondReferral(id.toString(), respondData);

        // STRANGLER: migrated — was ReferralRepository.findByIdAndTenantId + referral.respond() + save
        // referral.respond(request.response_notes(), request.outcome());
        // referralRepository.save(referral);

        outboxService.writeOutboxEvent(
                "impilo.experience.referral.responded.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "Referral", id.toString(),
                Map.of(
                        "referral_id", id.toString(),
                        "response_notes", request.response_notes(),
                        "outcome", request.outcome() != null ? request.outcome() : "",
                        "status", "RESPONDED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
