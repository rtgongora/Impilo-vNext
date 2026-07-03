package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.pct.api.dto.StartEncounterRequest;
import zw.gov.mohcc.impilo.pct.api.dto.UpdateEncounterPathwayProtocolRequest;
import zw.gov.mohcc.impilo.pct.core.EncounterService;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.visibility.ClinicalVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.*;

/**
 * REST controller for clinical encounter lifecycle management.
 *
 * <p>Provides endpoints for starting and completing encounters within
 * patient journeys, viewing individual encounters, and retrieving an
 * operational timeline of journeys and encounters for a patient CPID.</p>
 */
@RestController
@RequestMapping("/v1")
public class EncounterController {

    private static final Logger log = LoggerFactory.getLogger(EncounterController.class);

    private final EncounterService encounterService;
    private final EncounterRepository encounterRepository;
    private final JourneyRepository journeyRepository;

    public EncounterController(EncounterService encounterService,
                               EncounterRepository encounterRepository,
                               JourneyRepository journeyRepository) {
        this.encounterService = encounterService;
        this.encounterRepository = encounterRepository;
        this.journeyRepository = journeyRepository;
    }

    /**
     * Start a new encounter within a patient journey.
     *
     * @param id      the journey identifier
     * @param request the encounter start request containing the encounter type
     * @return the newly created encounter entity
     */
    @PostMapping("/journeys/{id}/encounter/start")
    public ResponseEntity<ApiResponse<EncounterEntity>> startEncounter(
            @PathVariable String id,
            @RequestHeader(value = CompanionHeaders.SHIFT_ID, required = false) String shiftIdHeader,
            @Valid @RequestBody StartEncounterRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String shiftId = shiftIdHeader != null && !shiftIdHeader.isBlank() ? shiftIdHeader : request.shiftId();

        EncounterEntity encounter = encounterService.startEncounter(
                id,
                request.encounterType(),
                request.encounterContext(),
                request.entryPoint(),
                request.modality(),
                request.virtualMode(),
                request.careSetting(),
                request.priority(),
                request.triageCategory(),
                request.pathwayRef(),
                request.protocolRef(),
                shiftId);

        return ResponseEntity.ok(ApiResponse.ok(encounter, correlationId));
    }

    /**
     * Complete a clinical encounter.
     *
     * @param id the encounter identifier
     * @return the completed encounter entity
     */
    @PostMapping("/encounters/{id}/complete")
    public ResponseEntity<ApiResponse<EncounterEntity>> completeEncounter(@PathVariable Long id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        EncounterEntity encounter = encounterService.completeEncounter(id);

        return ResponseEntity.ok(ApiResponse.ok(encounter, correlationId));
    }

    /**
     * Update encounter pathway/protocol references.
     */
    @PatchMapping("/encounters/{id}/pathway-protocol")
    public ResponseEntity<ApiResponse<EncounterEntity>> updateEncounterPathwayProtocol(
            @PathVariable Long id,
            @RequestBody UpdateEncounterPathwayProtocolRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        EncounterEntity encounter = encounterService.updateEncounterPathwayProtocol(
                id, request.pathwayRef(), request.protocolRef());
        return ResponseEntity.ok(ApiResponse.ok(encounter, correlationId));
    }

    /**
     * Retrieve an encounter by its identifier.
     *
     * @param id the encounter identifier
     * @return the encounter entity
     */
    @GetMapping("/encounters/{id}")
    public ResponseEntity<ApiResponse<EncounterEntity>> getEncounter(@PathVariable Long id) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        VisibilityProfile vis = VisibilityContextHolder.current().orElse(null);
        if (ClinicalVisibilityGuard.deniesClinicalRead(vis)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("VISIBILITY_CLINICAL_DENIED",
                            "Encounter detail is not permitted for the current visibility profile.",
                            HttpStatus.FORBIDDEN.value(), correlationId));
        }

        EncounterEntity encounter = encounterRepository
                .findByTenantIdAndId(ctx.tenantId(), id)
                .orElse(null);

        if (encounter == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("ENCOUNTER_NOT_FOUND",
                            "Encounter not found: " + id, 404, correlationId));
        }

        return ResponseEntity.ok(ApiResponse.ok(encounter, correlationId));
    }

    /**
     * Retrieve the operational timeline for a patient CPID.
     *
     * <p>Returns a composite view of all journeys and their associated
     * encounters for the given patient. This provides a chronological
     * record of all facility interactions.</p>
     *
     * @param cpid the patient's Common Patient Identifier
     * @return list of timeline entries (journeys with nested encounters)
     */
    @GetMapping("/patient/{cpid}/timeline")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTimeline(@PathVariable String cpid) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        VisibilityProfile vis = VisibilityContextHolder.current().orElse(null);
        if (ClinicalVisibilityGuard.deniesClinicalRead(vis)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("VISIBILITY_CLINICAL_DENIED",
                            "Patient timeline is not permitted for the current visibility profile.",
                            HttpStatus.FORBIDDEN.value(), correlationId));
        }

        List<JourneyEntity> journeys = journeyRepository
                .findByTenantIdAndPatientCpid(ctx.tenantId(), cpid);

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (JourneyEntity journey : journeys) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("journey", journey);

            List<EncounterEntity> encounters = encounterRepository
                    .findByTenantIdAndJourneyId(ctx.tenantId(), journey.getJourneyId());
            entry.put("encounters", encounters);

            timeline.add(entry);
        }

        return ResponseEntity.ok(ApiResponse.ok(timeline, correlationId));
    }
}
