package zw.gov.mohcc.impilo.varapi.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.CpdEventDto;
import zw.gov.mohcc.impilo.varapi.api.dto.CpdSummaryResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.CreateCpdCycleRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.RecordCpdEventRequest;
import zw.gov.mohcc.impilo.varapi.core.CpdService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CpdCycleEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CpdEventEntity;

/**
 * Internal REST API for managing Continuing Professional Development (CPD) within
 * the VARAPI Provider Registry.
 *
 * CPD cycles track the ongoing education and training requirements for providers.
 * Each cycle has a target number of points, and events (courses, conferences,
 * publications, etc.) contribute earned points towards the requirement.
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/internal/providers/{providerPublicId}/cpd")
public class CpdController {

    private static final Logger log = LoggerFactory.getLogger(CpdController.class);

    private final CpdService cpdService;

    public CpdController(CpdService cpdService) {
        this.cpdService = cpdService;
    }

    @PostMapping("/cycles")
    public ResponseEntity<ApiResponse<CpdSummaryResponse>> createCycle(
            @PathVariable String providerPublicId,
            @Valid @RequestBody CreateCpdCycleRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating CPD cycle [providerPublicId={}, cycleName={}] correlationId={}",
                providerPublicId, request.cycleName(), ctx.correlationId());

        CpdCycleEntity cycle = cpdService.createCycle(providerPublicId, request);
        CpdSummaryResponse response = toCpdSummaryResponse(cycle);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/cycles/{cycleId}/events")
    public ResponseEntity<ApiResponse<CpdEventDto>> recordEvent(
            @PathVariable String providerPublicId,
            @PathVariable Long cycleId,
            @Valid @RequestBody RecordCpdEventRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Recording CPD event [providerPublicId={}, cycleId={}, type={}] correlationId={}",
                providerPublicId, cycleId, request.eventType(), ctx.correlationId());

        CpdEventEntity event = cpdService.recordEvent(providerPublicId, cycleId, request);
        CpdEventDto response = toCpdEventDto(event);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/events/{eventId}/verify")
    public ResponseEntity<ApiResponse<CpdEventDto>> verifyEvent(
            @PathVariable String providerPublicId,
            @PathVariable Long eventId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Verifying CPD event [providerPublicId={}, eventId={}] correlationId={}",
                providerPublicId, eventId, ctx.correlationId());

        CpdEventEntity event = cpdService.verifyEvent(providerPublicId, eventId);
        CpdEventDto response = toCpdEventDto(event);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<CpdSummaryResponse>> getCpdSummary(
            @PathVariable String providerPublicId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching CPD summary [providerPublicId={}] correlationId={}",
                providerPublicId, ctx.correlationId());

        CpdSummaryResponse response = cpdService.getCpdSummary(providerPublicId);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    // ---- Mapper: Entity → Response DTOs ----

    private CpdSummaryResponse toCpdSummaryResponse(CpdCycleEntity cycle) {
        return new CpdSummaryResponse(
                cycle.getId(),
                cycle.getProvider().getProviderPublicId(),
                cycle.getCycleName(),
                cycle.getStartDate(),
                cycle.getEndDate(),
                cycle.getRequiredPoints(),
                cycle.getEarnedPoints(),
                cycle.getStatus(),
                cycle.isPointsRequirementMet()
        );
    }

    private CpdEventDto toCpdEventDto(CpdEventEntity event) {
        return new CpdEventDto(
                event.getId(),
                event.getCycle().getId(),
                event.getEventType(),
                event.getTitle(),
                event.getDescription(),
                event.getPointsAwarded(),
                event.getEventDate(),
                event.getExternalRef(),
                event.getVerified() != null && event.getVerified(),
                event.getVerifiedBy(),
                event.getVerifiedAt(),
                event.getCreatedAt()
        );
    }
}
