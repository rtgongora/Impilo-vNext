package zw.gov.mohcc.impilo.experience.controller;

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
import zw.gov.mohcc.impilo.experience.domain.Referral;
import zw.gov.mohcc.impilo.experience.repository.ReferralRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Referral management endpoints.
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

    public ReferralsController(ReferralRepository referralRepository,
                               OutboxService outboxService,
                               JdbcTemplate jdbcTemplate) {
        this.referralRepository = referralRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
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

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<Referral> result;
        if (patientId != null) {
            result = referralRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId), pageable);
        } else {
            result = referralRepository.findAll(pageable);
        }


        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * List incoming referrals for a receiving facility.
     * GET /internal/v1/referrals/incoming?facility_id=
     */
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
        int offset = page * limit;

        StringBuilder sql = new StringBuilder("""
            SELECT id, tenant_id, patient_id, encounter_id, referral_type, specialty,
                   referred_to, referred_to_facility, reason, urgency, status,
                   clinical_summary, referred_by, referred_by_name,
                   receiving_facility_id, receiving_facility_name,
                   response_notes, responded_at, accepted_at,
                   scheduled_at, completed_at, outcome, created_at, updated_at
            FROM referrals
            WHERE tenant_id = ? AND (
                referred_to_facility = ? OR receiving_facility_id = ?::uuid
            )
            """);
        StringBuilder countSql = new StringBuilder(
                "SELECT count(*) FROM referrals WHERE tenant_id = ? AND (referred_to_facility = ? OR receiving_facility_id = ?::uuid)");

        List<Object> params = new ArrayList<>(List.of(tenantId, facilityId, facilityId));
        List<Object> countParams = new ArrayList<>(List.of(tenantId, facilityId, facilityId));

        if (status != null) {
            sql.append(" AND status = ?");
            countSql.append(" AND status = ?");
            params.add(status);
            countParams.add(status);
        }

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("patient_id", row.get("patient_id"));
            attributes.put("encounter_id", row.get("encounter_id"));
            attributes.put("referral_type", row.get("referral_type"));
            attributes.put("specialty", row.get("specialty"));
            attributes.put("referred_to", row.get("referred_to"));
            attributes.put("referred_to_facility", row.get("referred_to_facility"));
            attributes.put("reason", row.get("reason"));
            attributes.put("urgency", row.get("urgency"));
            attributes.put("status", row.get("status"));
            attributes.put("clinical_summary", row.get("clinical_summary"));
            attributes.put("referred_by", row.get("referred_by"));
            attributes.put("referred_by_name", row.get("referred_by_name"));
            attributes.put("receiving_facility_id", row.get("receiving_facility_id"));
            attributes.put("receiving_facility_name", row.get("receiving_facility_name"));
            attributes.put("response_notes", row.get("response_notes"));
            attributes.put("responded_at", row.get("responded_at"));
            attributes.put("accepted_at", row.get("accepted_at"));
            attributes.put("scheduled_at", row.get("scheduled_at"));
            attributes.put("completed_at", row.get("completed_at"));
            attributes.put("outcome", row.get("outcome"));
            attributes.put("created_at", row.get("created_at"));
            attributes.put("updated_at", row.get("updated_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "Referral");
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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReferral(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        Referral referral = referralRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(referral));
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

        UUID referralId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO referrals
                (id, tenant_id, patient_id, encounter_id, referral_type,
                 specialty, referred_to, referred_to_facility, reason, urgency,
                 status, clinical_summary, referred_by, referred_by_name,
                 created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
            """,
                referralId, tenantId, request.patient_id(), request.encounter_id(),
                request.referral_type(),
                request.specialty(), request.referred_to(), request.referred_to_facility(),
                request.reason(), request.urgency() != null ? request.urgency() : "ROUTINE",
                request.clinical_summary(),
                request.referred_by(), request.referred_by_name(),
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.referral.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Referral",
                referralId.toString(),
                Map.of(
                        "referral_id", referralId.toString(),
                        "patient_id", request.patient_id(),
                        "referral_type", request.referral_type() != null ? request.referral_type() : "",
                        "specialty", request.specialty() != null ? request.specialty() : "",
                        "referred_by", request.referred_by(),
                        "status", "PENDING"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("referral_type", request.referral_type());
        attributes.put("specialty", request.specialty());
        attributes.put("referred_to", request.referred_to());
        attributes.put("referred_to_facility", request.referred_to_facility());
        attributes.put("reason", request.reason());
        attributes.put("urgency", request.urgency() != null ? request.urgency() : "ROUTINE");
        attributes.put("status", "PENDING");
        attributes.put("clinical_summary", request.clinical_summary());
        attributes.put("referred_by", request.referred_by());
        attributes.put("referred_by_name", request.referred_by_name());
        attributes.put("created_at", now);
        attributes.put("updated_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", referralId.toString(),
                "type", "Referral",
                "attributes", attributes
        ));
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

        Referral referral = referralRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral not found: " + id));

        String outcome = request != null ? request.outcome() : null;
        referral.complete(outcome);
        referralRepository.save(referral);

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
                        "patient_id", referral.getPatientId().toString(),
                        "status", "COMPLETED",
                        "outcome", outcome != null ? outcome : ""
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(referral));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Accept a referral at the receiving facility.
     * POST /internal/v1/referrals/{id}/accept
     */
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

        Referral referral = referralRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral not found: " + id));

        UUID receivingId = request.receiving_facility_id() != null
                ? UUID.fromString(request.receiving_facility_id()) : null;
        referral.accept(receivingId, request.receiving_facility_name());
        referralRepository.save(referral);

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
        response.put("data", toResource(referral));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Record a response from the receiving facility (outcome/notes from specialist).
     * POST /internal/v1/referrals/{id}/respond
     */
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

        Referral referral = referralRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral not found: " + id));

        referral.respond(request.response_notes(), request.outcome());
        referralRepository.save(referral);

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
        response.put("data", toResource(referral));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(Referral r) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", r.getPatientId());
        attributes.put("encounter_id", r.getEncounterId());
        attributes.put("referral_type", r.getReferralType());
        attributes.put("specialty", r.getSpecialty());
        attributes.put("referred_to", r.getReferredTo());
        attributes.put("referred_to_facility", r.getReferredToFacility());
        attributes.put("reason", r.getReason());
        attributes.put("urgency", r.getUrgency());
        attributes.put("status", r.getStatus());
        attributes.put("clinical_summary", r.getClinicalSummary());
        attributes.put("referred_by", r.getReferredBy());
        attributes.put("referred_by_name", r.getReferredByName());
        attributes.put("receiving_facility_id", r.getReceivingFacilityId());
        attributes.put("receiving_facility_name", r.getReceivingFacilityName());
        attributes.put("response_notes", r.getResponseNotes());
        attributes.put("responded_at", r.getRespondedAt());
        attributes.put("accepted_at", r.getAcceptedAt());
        attributes.put("scheduled_at", r.getScheduledAt());
        attributes.put("completed_at", r.getCompletedAt());
        attributes.put("outcome", r.getOutcome());
        attributes.put("created_at", r.getCreatedAt());
        attributes.put("updated_at", r.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", r.getId().toString());
        resource.put("type", "Referral");
        resource.put("attributes", attributes);
        return resource;
    }
}
