package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import zw.gov.mohcc.impilo.experience.domain.Encounter;
import zw.gov.mohcc.impilo.experience.repository.EncounterRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Encounter management endpoints.
 * GET  /internal/v1/encounters — list encounters with patient_id filter, pagination.
 * GET  /internal/v1/encounters/{id} — get single encounter.
 * POST /internal/v1/encounters — create encounter.
 * POST /internal/v1/encounters/{id}/close — close encounter.
 */
@RestController
@RequestMapping("/internal/v1/encounters")
public class EncounterController {

    private static final Logger log = LoggerFactory.getLogger(EncounterController.class);

    private final EncounterRepository encounterRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;
    private final PctServiceClient pctClient;

    public EncounterController(EncounterRepository encounterRepository,
                               OutboxService outboxService,
                               JdbcTemplate jdbcTemplate,
                               PctServiceClient pctClient) {
        this.encounterRepository = encounterRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
        this.pctClient = pctClient;
    }

    public record CreateEncounterRequest(
            @NotBlank String patient_id,
            @NotBlank String facility_id,
            @NotBlank String encounter_type,
            String chief_complaint,
            String pct_journey_id,
            String patient_cpid
    ) {}

    public record CloseEncounterRequest(
            String diagnosis
    ) {}

    public record DischargeEncounterRequest(
            @NotBlank String discharge_type,
            String discharge_diagnosis,
            String treatment_summary,
            String follow_up_instructions,
            String medications_at_discharge,
            String patient_instructions,
            String discharged_by
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listEncounters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<Encounter> result;
        if (patientId != null) {
            result = encounterRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId), pageable);
        } else {
            result = encounterRepository.findAll(pageable);
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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEncounter(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        Encounter encounter = encounterRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(encounter));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createEncounter(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateEncounterRequest request) {

        UUID encounterId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO encounters
                (id, tenant_id, facility_id, patient_id, encounter_type, chief_complaint,
                 status, started_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, 'IN_PROGRESS', ?, ?, ?)
            """,
                encounterId, tenantId, request.facility_id(), request.patient_id(),
                request.encounter_type(), request.chief_complaint(),
                now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.encounter.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Encounter",
                encounterId.toString(),
                Map.of(
                        "encounter_id", encounterId.toString(),
                        "patient_id", request.patient_id(),
                        "facility_id", request.facility_id(),
                        "encounter_type", request.encounter_type(),
                        "status", "IN_PROGRESS"
                ),
                Map.of()
        );

        // Delegate to PCT: start encounter in the sovereign service
        String pctEncounterRef = null;
        if (request.pct_journey_id() != null && !request.pct_journey_id().isBlank()) {
            try {
                JsonNode pctEncounter = pctClient.startEncounter(
                        request.pct_journey_id(), request.encounter_type());
                if (pctEncounter != null && pctEncounter.has("encounterRef")) {
                    pctEncounterRef = pctEncounter.get("encounterRef").asText();
                }
                log.info("PCT encounter started: ref={} for BFF encounter {}",
                        pctEncounterRef, encounterId);

                // Persist the PCT encounter reference and journey ID
                jdbcTemplate.update("""
                    UPDATE encounters SET pct_encounter_ref = ?, pct_journey_id = ?
                    WHERE id = ? AND tenant_id = ?
                    """, pctEncounterRef, request.pct_journey_id(), encounterId, tenantId);
            } catch (Exception e) {
                log.warn("PCT encounter delegation failed (non-blocking): {}", e.getMessage());
            }
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("facility_id", request.facility_id());
        attributes.put("encounter_type", request.encounter_type());
        attributes.put("chief_complaint", request.chief_complaint());
        attributes.put("status", "IN_PROGRESS");
        attributes.put("started_at", now);
        attributes.put("created_at", now);
        if (pctEncounterRef != null) {
            attributes.put("pct_encounter_ref", pctEncounterRef);
        }
        if (request.pct_journey_id() != null) {
            attributes.put("pct_journey_id", request.pct_journey_id());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", encounterId.toString(),
                "type", "Encounter",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/close")
    @Transactional
    public ResponseEntity<Map<String, Object>> closeEncounter(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CloseEncounterRequest request) {

        Encounter encounter = encounterRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + id));

        encounter.close();
        encounterRepository.save(encounter);

        outboxService.writeOutboxEvent(
                "impilo.experience.encounter.closed.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Encounter",
                id.toString(),
                Map.of(
                        "encounter_id", id.toString(),
                        "status", "COMPLETED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(encounter));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/discharge")
    @Transactional
    public ResponseEntity<Map<String, Object>> dischargeEncounter(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DischargeEncounterRequest request) {

        Encounter encounter = encounterRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + id));

        encounter.discharge(
                request.discharge_type(),
                request.discharge_diagnosis(),
                request.treatment_summary(),
                request.follow_up_instructions(),
                request.medications_at_discharge(),
                request.patient_instructions(),
                request.discharged_by()
        );
        encounterRepository.save(encounter);

        // Delegate to PCT: start discharge workflow in sovereign service
        String pctJourneyId = encounter.getNotes(); // stored as notes if linked
        if (pctJourneyId == null || pctJourneyId.isBlank()) {
            // Try to find PCT journey from encounter metadata via JDBC
            List<Map<String, Object>> metaRows = jdbcTemplate.queryForList(
                    "SELECT pct_journey_id FROM encounters WHERE id = ? AND tenant_id = ?",
                    id, tenantId);
            if (!metaRows.isEmpty() && metaRows.get(0).get("pct_journey_id") != null) {
                pctJourneyId = metaRows.get(0).get("pct_journey_id").toString();
            }
        }
        if (pctJourneyId != null && !pctJourneyId.isBlank()) {
            try {
                pctClient.startDischarge(pctJourneyId, request.discharge_type());
                log.info("PCT discharge started for journey={} from encounter={}", pctJourneyId, id);
            } catch (Exception e) {
                log.warn("PCT discharge delegation failed (non-blocking): {}", e.getMessage());
            }
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.encounter.discharged.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Encounter",
                id.toString(),
                Map.of(
                        "encounter_id", id.toString(),
                        "discharge_type", request.discharge_type(),
                        "status", "DISCHARGED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(encounter));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(Encounter e) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", e.getFacilityId());
        attributes.put("patient_id", e.getPatientId());
        attributes.put("shift_id", e.getShiftId());
        attributes.put("encounter_type", e.getEncounterType());
        attributes.put("status", e.getStatus());
        attributes.put("chief_complaint", e.getChiefComplaint());
        attributes.put("diagnosis", e.getDiagnosis());
        attributes.put("notes", e.getNotes());
        attributes.put("vitals", e.getVitals());
        attributes.put("started_at", e.getStartedAt());
        attributes.put("ended_at", e.getEndedAt());
        attributes.put("discharge_type", e.getDischargeType());
        attributes.put("discharge_diagnosis", e.getDischargeDiagnosis());
        attributes.put("treatment_summary", e.getTreatmentSummary());
        attributes.put("follow_up_instructions", e.getFollowUpInstructions());
        attributes.put("medications_at_discharge", e.getMedicationsAtDischarge());
        attributes.put("patient_instructions", e.getPatientInstructions());
        attributes.put("discharged_by", e.getDischargedBy());
        attributes.put("discharged_at", e.getDischargedAt());
        attributes.put("created_at", e.getCreatedAt());
        attributes.put("updated_at", e.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", e.getId().toString());
        resource.put("type", "Encounter");
        resource.put("attributes", attributes);
        return resource;
    }
}
