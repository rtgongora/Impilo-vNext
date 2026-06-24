package zw.gov.mohcc.impilo.datagovernance.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.datagovernance.api.dto.CancelDataSubjectRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.DataSubjectRequestResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.PrivacyPreferenceResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.SubmitDataSubjectRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.UpdatePrivacyPreferenceRequest;
import zw.gov.mohcc.impilo.datagovernance.core.DataSubjectRequestService;
import zw.gov.mohcc.impilo.datagovernance.core.PrivacyPreferenceService;
import zw.gov.mohcc.impilo.datagovernance.domain.DataSubjectRequestEntity;
import zw.gov.mohcc.impilo.datagovernance.domain.PrivacyDisplayPreferenceEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Data-subject privacy rights — the Privacy-by-Architecture plane (§6) endpoints
 * for account-deletion / right-to-erasure requests. The experience-bff proxies here.
 */
@RestController
public class PrivacyRightsController {

    private static final Logger log = LoggerFactory.getLogger(PrivacyRightsController.class);

    private final DataSubjectRequestService dsrService;
    private final PrivacyPreferenceService preferenceService;

    public PrivacyRightsController(DataSubjectRequestService dsrService,
                                   PrivacyPreferenceService preferenceService) {
        this.dsrService = dsrService;
        this.preferenceService = preferenceService;
    }

    // ── POST /internal/v1/governance/data-subject-requests — Submit deletion/erasure ──

    @PostMapping("/internal/v1/governance/data-subject-requests")
    public ResponseEntity<?> submit(@Valid @RequestBody SubmitDataSubjectRequest request,
                                    HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Submit data-subject request [subject={}, type={}] correlationId={}",
                request.subjectId(), request.requestType(), ctx.correlationId());

        DataSubjectRequestEntity req = dsrService.submit(
                request.subjectId(), request.requestType(), request.reason(),
                UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(), idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DataSubjectRequestResponse.from(req));
    }

    // ── GET /internal/v1/governance/data-subject-requests — Latest request status ──

    @GetMapping("/internal/v1/governance/data-subject-requests")
    public ResponseEntity<?> status(@RequestParam String subjectId,
                                    @RequestParam(required = false) String requestType) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("Status data-subject request [subject={}] correlationId={}",
                subjectId, ctx.correlationId());

        Optional<DataSubjectRequestEntity> latest = dsrService.latest(
                subjectId, requestType, UUID.fromString(ctx.tenantId()));

        return latest
                .<ResponseEntity<?>>map(e -> ResponseEntity.ok(DataSubjectRequestResponse.from(e)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── POST /internal/v1/governance/data-subject-requests/cancel — Cancel pending ──

    @PostMapping("/internal/v1/governance/data-subject-requests/cancel")
    public ResponseEntity<?> cancel(@Valid @RequestBody CancelDataSubjectRequest request,
                                    HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Cancel data-subject request [subject={}] correlationId={}",
                request.subjectId(), ctx.correlationId());

        Optional<DataSubjectRequestEntity> cancelled = dsrService.cancel(
                request.subjectId(), request.requestType(),
                UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(), idempotencyKey);

        return cancelled
                .<ResponseEntity<?>>map(e -> ResponseEntity.ok(DataSubjectRequestResponse.from(e)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── GET /internal/v1/governance/privacy-preferences — User's display preference ──

    @GetMapping("/internal/v1/governance/privacy-preferences")
    public ResponseEntity<?> getPreference(@RequestParam String userId) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("Get privacy preference [user={}] correlationId={}", userId, ctx.correlationId());

        Optional<PrivacyDisplayPreferenceEntity> pref = preferenceService.find(
                userId, UUID.fromString(ctx.tenantId()));

        return pref
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(PrivacyPreferenceResponse.from(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── PUT /internal/v1/governance/privacy-preferences — Upsert display preference ──

    @PutMapping("/internal/v1/governance/privacy-preferences")
    public ResponseEntity<?> updatePreference(@Valid @RequestBody UpdatePrivacyPreferenceRequest request,
                                              HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Update privacy preference [user={}] correlationId={}",
                request.userId(), ctx.correlationId());

        PrivacyDisplayPreferenceEntity pref = preferenceService.upsert(
                request.userId(), request.defaultLevel(), request.autoLockMinutes(),
                request.screenShareMode(),
                UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(), idempotencyKey);

        return ResponseEntity.ok(PrivacyPreferenceResponse.from(pref));
    }
}
