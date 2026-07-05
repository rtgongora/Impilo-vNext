package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.varapi.api.dto.trust.ProviderTrustStatusResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.trust.RecordVerificationAttemptRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.trust.TrustTransitionRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.trust.TrustTransitionResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.trust.VerificationAttemptView;
import zw.gov.mohcc.impilo.varapi.core.ProviderTrustService;
import zw.gov.mohcc.impilo.varapi.enums.OnboardingChannel;
import zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderVerificationAttemptEntity;

import java.util.List;
import java.util.Locale;

/**
 * Provider trust API — FROZEN CONTRACT (IATG Wave 1; WS-D/WS-F compile against this).
 *
 * <ul>
 *   <li>{@code POST /v1/internal/providers/{providerId}/trust}
 *       body {@code {targetLevel, evidence:{type, source, ref, confidence}, channel}}
 *       &rarr; 200 {@code {providerPublicId (MASKED), trustLevel, registryStatus}}</li>
 *   <li>{@code GET /v1/internal/providers/{providerId}/trust}
 *       &rarr; {@code {trustLevel, registryStatus, onboardingChannel, attempts:[...]}}</li>
 *   <li>{@code POST /v1/internal/providers/{providerId}/verification-attempts}
 *       body {@code {type, source, ref, outcome, confidence}}</li>
 * </ul>
 *
 * <p>{@code {providerId}} accepts the numeric internal provider id or the
 * providerPublicId. Routes are ext_authz-gated at Envoy/TSHEPO like every other
 * {@code /v1/internal} varapi surface.</p>
 *
 * <p><b>Council numbers NEVER appear in any response payload</b> — they are matching
 * evidence, not identity. providerPublicId and every evidence ref are masked to
 * first-4-chars + "***" before serialization.</p>
 */
@RestController
@RequestMapping("/v1/internal/providers/{providerId}")
public class ProviderTrustController {

    private final ProviderTrustService trustService;

    public ProviderTrustController(ProviderTrustService trustService) {
        this.trustService = trustService;
    }

    @PostMapping("/trust")
    public ResponseEntity<TrustTransitionResponse> applyTransition(
            @PathVariable String providerId,
            @RequestBody TrustTransitionRequest request) {
        if (request == null || request.evidence() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "evidence is required");
        }
        ProviderTrustService.TrustTransitionResult result = trustService.applyTransition(
                providerId,
                parseTrustLevel(request.targetLevel()),
                new ProviderTrustService.TrustEvidence(
                        request.evidence().type(),
                        request.evidence().source(),
                        request.evidence().ref(),
                        request.evidence().confidence()),
                parseChannel(request.channel()));
        return ResponseEntity.ok(new TrustTransitionResponse(
                mask(result.provider().getProviderPublicId()),
                result.trustLevel().name(),
                result.registryStatus().name()));
    }

    @GetMapping("/trust")
    public ResponseEntity<ProviderTrustStatusResponse> getTrust(@PathVariable String providerId) {
        ProviderEntity provider = trustService.getProvider(providerId);
        List<VerificationAttemptView> attempts = trustService.listAttempts(provider).stream()
                .map(ProviderTrustController::toView)
                .toList();
        String trustLevel = provider.getTrustLevel() == null || provider.getTrustLevel().isBlank()
                ? ProviderTrustLevel.SELF_ASSERTED.name()
                : provider.getTrustLevel();
        return ResponseEntity.ok(new ProviderTrustStatusResponse(
                trustLevel,
                provider.getRegistryStatus(),
                provider.getOnboardingChannel(),
                attempts));
    }

    @PostMapping("/verification-attempts")
    public ResponseEntity<VerificationAttemptView> recordAttempt(
            @PathVariable String providerId,
            @RequestBody RecordVerificationAttemptRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        ProviderVerificationAttemptEntity row = trustService.recordAttempt(
                providerId, request.type(), request.source(), request.ref(),
                request.outcome(), request.confidence());
        return ResponseEntity.ok(toView(row));
    }

    // ── Mapping / masking ────────────────────────────────────────────────────

    private static VerificationAttemptView toView(ProviderVerificationAttemptEntity row) {
        return new VerificationAttemptView(
                row.getEvidenceType(),
                row.getEvidenceSource(),
                row.getOutcome(),
                row.getConfidence(),
                mask(row.getEvidenceRef()),
                row.getAttemptedBy(),
                row.getAttemptedAt());
    }

    /**
     * Mask a confidential value for API payloads: first 4 characters + {@code ***}
     * (single leading character for shorter values). Applied to providerPublicId and
     * every evidence ref so full council numbers / platform Provider IDs never appear
     * in a trust API response.
     */
    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        int keep = Math.min(4, v.length() - 1);
        if (keep <= 0) {
            return "***";
        }
        return v.substring(0, keep) + "***";
    }

    private static ProviderTrustLevel parseTrustLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetLevel is required");
        }
        try {
            return ProviderTrustLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown targetLevel: " + raw);
        }
    }

    private static OnboardingChannel parseChannel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OnboardingChannel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown channel: " + raw);
        }
    }
}
