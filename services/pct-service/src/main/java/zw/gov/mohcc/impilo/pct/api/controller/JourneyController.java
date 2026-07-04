package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.api.dto.RouteRequest;
import zw.gov.mohcc.impilo.pct.api.dto.StartJourneyRequest;
import zw.gov.mohcc.impilo.pct.api.dto.TriageRequest;
import zw.gov.mohcc.impilo.pct.core.JourneyStateMachine;
import zw.gov.mohcc.impilo.pct.core.RoutingEngine;
import zw.gov.mohcc.impilo.pct.core.TriageService;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TriageRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;

/**
 * REST controller for patient journey lifecycle management.
 *
 * <p>Exposes endpoints for creating journeys, recording triage assessments,
 * routing patients to queues, and querying journey state. All operations
 * are tenant-scoped via the trust context.</p>
 */
@RestController
@RequestMapping("/v1/journeys")
public class JourneyController {

    private static final Logger log = LoggerFactory.getLogger(JourneyController.class);

    private final JourneyStateMachine journeyStateMachine;
    private final TriageService triageService;
    private final RoutingEngine routingEngine;
    private final JourneyRepository journeyRepository;

    public JourneyController(JourneyStateMachine journeyStateMachine,
                             TriageService triageService,
                             RoutingEngine routingEngine,
                             JourneyRepository journeyRepository) {
        this.journeyStateMachine = journeyStateMachine;
        this.triageService = triageService;
        this.routingEngine = routingEngine;
        this.journeyRepository = journeyRepository;
    }

    /**
     * Start a new patient journey at a facility.
     *
     * @param request the journey start request containing patient CPID and facility details
     * @return the newly created journey entity
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<JourneyEntity>> startJourney(
            @Valid @RequestBody StartJourneyRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        JourneyEntity journey = journeyStateMachine.createJourney(
                request.facilityId() != null ? request.facilityId() : ctx.facilityId(),
                request.patientCpid(),
                request.referralSource(),
                request.referralId(),
                request.appointmentId());

        return ResponseEntity.ok(ApiResponse.ok(journey, correlationId));
    }

    /**
     * Record a triage assessment for a journey.
     *
     * @param id      the journey identifier
     * @param request the triage request containing acuity, vitals, and notes
     * @return the created triage record
     */
    @PostMapping("/{id}/triage")
    public ResponseEntity<ApiResponse<TriageRecordEntity>> recordTriage(
            @PathVariable String id,
            @Valid @RequestBody TriageRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        TriageRecordEntity record = triageService.recordTriage(
                id, request.acuity(), request.vitals(), request.notes());

        return ResponseEntity.ok(ApiResponse.ok(record, correlationId));
    }

    /**
     * Route a patient journey to a target queue.
     *
     * @param id      the journey identifier
     * @param request the route request containing the target queue
     * @return acknowledgement of the routing
     */
    @PostMapping("/{id}/route")
    public ResponseEntity<ApiResponse<String>> routeJourney(
            @PathVariable String id,
            @Valid @RequestBody RouteRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        routingEngine.route(id, request.targetQueueId());

        return ResponseEntity.ok(ApiResponse.ok("Journey routed to queue " + request.targetQueueId(), correlationId));
    }

    /**
     * Retrieve a journey by its identifier.
     *
     * @param id the journey identifier
     * @return the journey entity with current state
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JourneyEntity>> getJourney(@PathVariable String id) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        JourneyEntity journey = journeyRepository
                .findByTenantIdAndJourneyId(ctx.tenantId(), id)
                .orElse(null);

        if (journey == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("JOURNEY_NOT_FOUND",
                            "Journey not found: " + id, 404, correlationId));
        }

        return ResponseEntity.ok(ApiResponse.ok(journey, correlationId));
    }

    /**
     * Retrieve journeys for a patient CPID (paged).
     *
     * @param cpid the patient's Common Patient Identifier
     * @param page the page number (0-based, default 0)
     * @param size the page size (default 20)
     * @return paginated list of journeys for the patient
     */
    @GetMapping("/patient/{cpid}")
    public ResponseEntity<ApiResponse<PagedResponse<JourneyEntity>>> getJourneysByPatient(
            @PathVariable String cpid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        List<JourneyEntity> allJourneys = journeyRepository
                .findByTenantIdAndPatientCpid(ctx.tenantId(), cpid);

        // Manual pagination over the list
        int start = Math.min(page * size, allJourneys.size());
        int end = Math.min(start + size, allJourneys.size());
        List<JourneyEntity> pageContent = allJourneys.subList(start, end);

        PagedResponse<JourneyEntity> paged = PagedResponse.of(
                pageContent, page, size, allJourneys.size());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }
}
