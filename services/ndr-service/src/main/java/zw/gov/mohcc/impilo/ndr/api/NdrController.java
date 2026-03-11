package zw.gov.mohcc.impilo.ndr.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.ndr.api.dto.BronzeQueryResponse;
import zw.gov.mohcc.impilo.ndr.api.dto.BronzeQueryResponse.BronzeEventItem;
import zw.gov.mohcc.impilo.ndr.api.dto.GoldBuildResponse;
import zw.gov.mohcc.impilo.ndr.api.dto.GoldEncounterResponse;
import zw.gov.mohcc.impilo.ndr.api.dto.GoldEncounterResponse.EncounterItem;
import zw.gov.mohcc.impilo.ndr.api.dto.IngestEventRequest;
import zw.gov.mohcc.impilo.ndr.api.dto.IngestEventResponse;
import zw.gov.mohcc.impilo.ndr.client.GovernanceClient;
import zw.gov.mohcc.impilo.ndr.client.GovernanceClient.DecisionResult;
import zw.gov.mohcc.impilo.ndr.client.GovernanceClient.GovernanceUnavailableException;
import zw.gov.mohcc.impilo.ndr.core.GoldBuildService;
import zw.gov.mohcc.impilo.ndr.core.NdrIngestService;
import zw.gov.mohcc.impilo.ndr.core.NdrIngestService.ValidationResult;
import zw.gov.mohcc.impilo.ndr.domain.BronzeEventEntity;
import zw.gov.mohcc.impilo.ndr.domain.GoldEncounterEntity;
import zw.gov.mohcc.impilo.ndr.repository.BronzeEventRepository;
import zw.gov.mohcc.impilo.ndr.repository.GoldEncounterRepository;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class NdrController {

    private static final Logger log = LoggerFactory.getLogger(NdrController.class);
    private static final String PURPOSE_OF_USE_HEADER = "X-Purpose-Of-Use";
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final NdrIngestService ingestService;
    private final GoldBuildService goldBuildService;
    private final GovernanceClient governanceClient;
    private final BronzeEventRepository bronzeRepository;
    private final GoldEncounterRepository goldEncounterRepository;
    private final ObjectMapper objectMapper;

    public NdrController(NdrIngestService ingestService,
                          GoldBuildService goldBuildService,
                          GovernanceClient governanceClient,
                          BronzeEventRepository bronzeRepository,
                          GoldEncounterRepository goldEncounterRepository,
                          ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.goldBuildService = goldBuildService;
        this.governanceClient = governanceClient;
        this.bronzeRepository = bronzeRepository;
        this.goldEncounterRepository = goldEncounterRepository;
        this.objectMapper = objectMapper;
    }

    // ── POST /internal/v1/ndr/ingest/events — Store bronze event locally ──

    @PostMapping("/internal/v1/ndr/ingest/events")
    public ResponseEntity<?> ingestEvent(@RequestBody String rawBody,
                                          HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        IngestEventRequest request;
        try {
            request = objectMapper.readValue(rawBody, IngestEventRequest.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_JSON",
                            "Failed to parse request body: " + e.getOriginalMessage(),
                            ctx.requestId(), ctx.correlationId()));
        }

        ValidationResult validation = ingestService.validate(request);
        if (validation != null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorEnvelope.of(validation.code(), validation.message(),
                            ctx.requestId(), ctx.correlationId()));
        }

        IngestEventResponse response = ingestService.ingestEvent(
                request, rawBody,
                UUID.fromString(ctx.tenantId()), ctx.podId(),
                ctx.requestId(), ctx.correlationId(),
                idempotencyKey);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ── GET /internal/v1/ndr/query/bronze — Query bronze events (governance-enforced) ──

    @GetMapping("/internal/v1/ndr/query/bronze")
    public ResponseEntity<?> queryBronze(
            @RequestParam(value = "event_type", required = false) String eventType,
            @RequestParam(value = "subject_id", required = false) String subjectId,
            @RequestParam(value = "cursor", required = false, defaultValue = "0") int cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit,
            HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();

        String purposeOfUse = httpRequest.getHeader(PURPOSE_OF_USE_HEADER);
        if (purposeOfUse == null || purposeOfUse.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("MISSING_REQUIRED_HEADER",
                            "X-Purpose-Of-Use header is required for query endpoints",
                            ctx.requestId(), ctx.correlationId()));
        }

        // Governance check
        ResponseEntity<?> governanceDenied = enforceGovernance(
                ctx, purposeOfUse, "ndr.bronze", httpRequest);
        if (governanceDenied != null) return governanceDenied;

        UUID tenantId = UUID.fromString(ctx.tenantId());
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        PageRequest pageRequest = PageRequest.of(cursor, effectiveLimit, Sort.by("storedAt").descending());

        Page<BronzeEventEntity> page;
        if (eventType != null && subjectId != null) {
            page = bronzeRepository.findByTenantIdAndEventTypeAndSubjectId(tenantId, eventType, subjectId, pageRequest);
        } else if (eventType != null) {
            page = bronzeRepository.findByTenantIdAndEventType(tenantId, eventType, pageRequest);
        } else if (subjectId != null) {
            page = bronzeRepository.findByTenantIdAndSubjectId(tenantId, subjectId, pageRequest);
        } else {
            page = bronzeRepository.findByTenantId(tenantId, pageRequest);
        }

        List<BronzeEventItem> items = new ArrayList<>();
        for (BronzeEventEntity b : page.getContent()) {
            Object envelopeJson = parseJsonSafe(b.getEnvelopeJson());
            items.add(new BronzeEventItem(
                    b.getReceiptId().toString(),
                    b.getEventId(),
                    b.getEventType(),
                    b.getSubjectType(),
                    b.getSubjectId(),
                    b.getOccurredAt() != null ? b.getOccurredAt().toString() : null,
                    b.getStoredAt() != null ? b.getStoredAt().toString() : null,
                    envelopeJson));
        }

        String nextCursor = page.hasNext() ? String.valueOf(cursor + 1) : null;
        return ResponseEntity.ok(new BronzeQueryResponse(items, nextCursor, effectiveLimit, page.hasNext()));
    }

    // ── POST /internal/v1/ndr/build/gold/encounters — Materialize gold encounters ──

    @PostMapping("/internal/v1/ndr/build/gold/encounters")
    public ResponseEntity<?> buildGoldEncounters(HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        UUID tenantId = UUID.fromString(ctx.tenantId());
        GoldBuildResponse response = goldBuildService.buildGoldEncounters(
                tenantId, ctx.podId(), ctx.correlationId(), idempotencyKey);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ── GET /internal/v1/ndr/query/gold/encounters — Query gold encounters (governance-enforced) ──

    @GetMapping("/internal/v1/ndr/query/gold/encounters")
    public ResponseEntity<?> queryGoldEncounters(
            @RequestParam(value = "patient_id", required = false) String patientId,
            @RequestParam(value = "cursor", required = false, defaultValue = "0") int cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit,
            HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();

        String purposeOfUse = httpRequest.getHeader(PURPOSE_OF_USE_HEADER);
        if (purposeOfUse == null || purposeOfUse.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("MISSING_REQUIRED_HEADER",
                            "X-Purpose-Of-Use header is required for query endpoints",
                            ctx.requestId(), ctx.correlationId()));
        }

        // Governance check
        ResponseEntity<?> governanceDenied = enforceGovernance(
                ctx, purposeOfUse, "ndr.gold.encounters", httpRequest);
        if (governanceDenied != null) return governanceDenied;

        UUID tenantId = UUID.fromString(ctx.tenantId());
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        PageRequest pageRequest = PageRequest.of(cursor, effectiveLimit, Sort.by("createdAt").descending());

        Page<GoldEncounterEntity> page;
        if (patientId != null && !patientId.isBlank()) {
            page = goldEncounterRepository.findByTenantIdAndPatientRef(tenantId, patientId, pageRequest);
        } else {
            page = goldEncounterRepository.findByTenantId(tenantId, pageRequest);
        }

        List<EncounterItem> items = new ArrayList<>();
        for (GoldEncounterEntity g : page.getContent()) {
            items.add(new EncounterItem(
                    g.getEncounterId().toString(),
                    g.getPatientRef(),
                    g.getFacilityRef(),
                    g.getStartedAt() != null ? g.getStartedAt().toString() : null,
                    g.getStatus(),
                    g.getSourceEventId(),
                    g.getUpdatedAt() != null ? g.getUpdatedAt().toString() : null));
        }

        String nextCursor = page.hasNext() ? String.valueOf(cursor + 1) : null;
        return ResponseEntity.ok(new GoldEncounterResponse(items, nextCursor, effectiveLimit, page.hasNext()));
    }

    // ── Governance enforcement helper ──

    private ResponseEntity<?> enforceGovernance(RequestContext ctx, String purposeOfUse,
                                                  String dataset, HttpServletRequest httpRequest) {
        try {
            String principalId = ctx.principal() != null ? ctx.principal() : "anonymous";
            DecisionResult decision = governanceClient.decide(
                    principalId, dataset, purposeOfUse,
                    ctx.tenantId(), ctx.podId(), ctx.correlationId());

            if (!decision.isAllowed()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ErrorEnvelope.of("GOVERNANCE_DENY",
                                "Access denied by governance policy",
                                Map.of("reason_codes", decision.reasonCodes(),
                                        "policy_version", decision.policyVersion()),
                                ctx.requestId(), ctx.correlationId()));
            }
            return null; // allowed
        } catch (GovernanceUnavailableException e) {
            log.error("Governance service unavailable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorEnvelope.of("GOVERNANCE_UNAVAILABLE",
                            "Governance service is unreachable, cannot process query",
                            ctx.requestId(), ctx.correlationId()));
        }
    }

    private Object parseJsonSafe(String json) {
        if (json == null) return null;
        try { return objectMapper.readTree(json); }
        catch (JsonProcessingException e) { return json; }
    }
}
