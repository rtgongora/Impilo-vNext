package zw.gov.mohcc.impilo.mvumo.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mvumo.domain.ConsentRequestState;
import zw.gov.mohcc.impilo.mvumo.engine.ConsentRequirementEngine;
import zw.gov.mohcc.impilo.mvumo.service.CommunicationPreferenceService;
import zw.gov.mohcc.impilo.mvumo.service.MvumoService;
import zw.gov.mohcc.impilo.mvumo.service.TenantIds;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal MVUMO API — orchestration for consent requests, remote sessions, templates, and requirement evaluation.
 */
@RestController
@RequestMapping("/internal/v1/mvumo")
public class MvumoInternalController {

    private final MvumoService mvumoService;
    private final CommunicationPreferenceService communicationPreferenceService;

    public MvumoInternalController(
            MvumoService mvumoService, CommunicationPreferenceService communicationPreferenceService) {
        this.mvumoService = mvumoService;
        this.communicationPreferenceService = communicationPreferenceService;
    }

    @PostMapping("/consent-requests")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRequest(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestBody Map<String, Object> body) {
        return ok(mvumoService.createConsentRequest(parseTenant(tenant), body));
    }

    @GetMapping("/consent-requests/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRequest(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant, @PathVariable UUID id) {
        return ok(mvumoService.getConsentRequest(parseTenant(tenant), id));
    }

    @GetMapping("/patients/{patientId}/consents")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> forPatient(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant, @PathVariable String patientId) {
        return ok(mvumoService.listForPatient(parseTenant(tenant), patientId));
    }

    /**
     * Aggregated consent surface for EHR patient summary, banner, and emergency views (query param avoids path encoding for {@code Patient/} refs).
     */
    @GetMapping("/consent-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> consentSummary(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestParam("patientRef") String patientRef) {
        return ok(mvumoService.getConsentSummary(parseTenant(tenant), patientRef));
    }

    @GetMapping("/patients/{patientId}/communication-preferences")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunicationPreferences(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant, @PathVariable String patientId) {
        return ok(communicationPreferenceService.getCurrent(parseTenant(tenant), patientId));
    }

    @PutMapping("/patients/{patientId}/communication-preferences")
    public ResponseEntity<ApiResponse<Map<String, Object>>> putCommunicationPreferences(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestHeader(value = "X-Actor-Ref", required = false) String actor,
            @PathVariable String patientId,
            @RequestBody Map<String, Object> body) {
        return ok(communicationPreferenceService.put(
                parseTenant(tenant),
                patientId,
                body,
                actorRef(actor),
                actorRelationshipFrom(body),
                sourceChannelFrom(body, "put"),
                assuranceFrom(body)));
    }

    @GetMapping("/patients/{patientId}/communication-preferences/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> communicationPreferencesHistory(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable String patientId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return ok(communicationPreferenceService.getHistory(parseTenant(tenant), patientId, limit));
    }

    @PatchMapping("/patients/{patientId}/communication-preferences/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> patchCommunicationPreferences(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestHeader(value = "X-Actor-Ref", required = false) String actor,
            @PathVariable String patientId,
            @PathVariable("id") UUID revisionId,
            @RequestBody Map<String, Object> body) {
        return ok(communicationPreferenceService.patch(
                parseTenant(tenant), patientId, revisionId, body, actorRef(actor), sourceChannelFrom(body, "patch")));
    }

    @PostMapping("/patients/{patientId}/communication-preferences/{id}/withdraw")
    public ResponseEntity<ApiResponse<Map<String, Object>>> withdrawCommunicationPreferences(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestHeader(value = "X-Actor-Ref", required = false) String actor,
            @PathVariable String patientId,
            @PathVariable("id") UUID revisionId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(communicationPreferenceService.withdraw(
                parseTenant(tenant), patientId, revisionId, body, actorRef(actor), sourceChannelFrom(body, "withdraw")));
    }

    @PostMapping("/patients/{patientId}/communication-preferences/{id}/reenable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reenableCommunicationPreferences(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestHeader(value = "X-Actor-Ref", required = false) String actor,
            @PathVariable String patientId,
            @PathVariable("id") UUID revisionId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(communicationPreferenceService.reenable(
                parseTenant(tenant), patientId, revisionId, body, actorRef(actor), sourceChannelFrom(body, "reenable")));
    }

    @PostMapping("/communication-preferences/evaluate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> evaluateCommunicationPreferences(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body != null ? new HashMap<>(body) : new HashMap<>();
        b.put("tenantId", parseTenant(tenant).toString());
        return ok(communicationPreferenceService.evaluate(b));
    }

    @PostMapping("/communication-preferences/offline-sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> communicationPreferencesOfflineSync(
            @RequestBody Map<String, Object> body) {
        return ok(communicationPreferenceService.offlineSync(body));
    }

    @GetMapping("/encounters/{encounterId}/consents")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> forEncounter(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant, @PathVariable String encounterId) {
        return ok(mvumoService.listForEncounter(parseTenant(tenant), encounterId));
    }

    @PostMapping("/consent-requests/{id}/explanation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> explanation(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(mvumoService.transition(parseTenant(tenant), id, ConsentRequestState.EXPLANATION_PROVIDED, body));
    }

    @PostMapping("/consent-requests/{id}/verify-identity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verify(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(mvumoService.transition(parseTenant(tenant), id, ConsentRequestState.PENDING_SIGNATURE, body));
    }

    @PostMapping("/consent-requests/{id}/grant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> grant(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(mvumoService.transition(parseTenant(tenant), id, ConsentRequestState.GRANTED, body));
    }

    @PostMapping("/consent-requests/{id}/partial-grant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> partial(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(mvumoService.transition(parseTenant(tenant), id, ConsentRequestState.PARTIALLY_GRANTED, body));
    }

    @PostMapping("/consent-requests/{id}/refuse")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refuse(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(mvumoService.transition(parseTenant(tenant), id, ConsentRequestState.REFUSED, body));
    }

    @PostMapping("/consent-requests/{id}/withdraw")
    public ResponseEntity<ApiResponse<Map<String, Object>>> withdraw(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(mvumoService.transition(parseTenant(tenant), id, ConsentRequestState.WITHDRAWN, body));
    }

    @PostMapping("/consent-requests/{id}/renew")
    public ResponseEntity<ApiResponse<Map<String, Object>>> renew(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(mvumoService.transition(parseTenant(tenant), id, ConsentRequestState.PENDING_EXPLANATION, body));
    }

    @PostMapping("/consent-requests/{id}/supersede")
    public ResponseEntity<ApiResponse<Map<String, Object>>> supersede(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(mvumoService.transition(parseTenant(tenant), id, ConsentRequestState.SUPERSEDED, body));
    }

    @PostMapping("/consent-requests/{id}/verify-proof")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyProof(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ok(mvumoService.getProof(parseTenant(tenant), id));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> evaluate(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = mvumoService.evaluateConsentDecision(body);
        if (isConsentDenied(result)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Consent policy denied");
        }
        return ok(result);
    }

    @PostMapping("/requirements/evaluate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requirements(@RequestBody Map<String, Object> b) {
        Map<String, Object> ext = Map.of();
        if (b.get("extension") instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) m;
            ext = cast;
        }
        var req = new ConsentRequirementEngine.RequirementEvaluationRequest(
                str(b.get("workflow")),
                str(b.get("consentType")),
                str(b.get("clinicalRisk")),
                str(b.get("dataSensitivity")),
                b.get("patientAge") instanceof Number n ? n.intValue() : null,
                str(b.get("connectivity")),
                str(b.get("channelHint")),
                ext);
        return ok(mvumoService.evaluateRequirements(req));
    }

    @PostMapping("/remote-sessions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> remoteCreate(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant, @RequestBody Map<String, Object> body) {
        return ok(mvumoService.createRemoteSession(parseTenant(tenant), body));
    }

    @GetMapping("/remote-sessions/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> remoteGet(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant, @PathVariable UUID id) {
        return ok(mvumoService.getRemoteSession(parseTenant(tenant), id));
    }

    @PostMapping("/remote-sessions/{id}/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> remoteVerify(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Ref", required = false) String actorRef,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        String actor = requiredTrustHeader(firstString(actorId, actorRef), "X-Actor-Id");
        String purpose = requiredTrustHeader(purposeOfUse, "X-Purpose-Of-Use");
        String correlation = requiredTrustHeader(correlationId, "X-Correlation-Id");
        return ok(mvumoService.remoteVerify(
                parseTenant(tenant), id, actor, purpose, correlation, body));
    }

    @PostMapping("/remote-sessions/{id}/grant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> remoteGrant(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Ref", required = false) String actorRef,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        String actor = requiredTrustHeader(firstString(actorId, actorRef), "X-Actor-Id");
        String purpose = requiredTrustHeader(purposeOfUse, "X-Purpose-Of-Use");
        String correlation = requiredTrustHeader(correlationId, "X-Correlation-Id");
        return ok(mvumoService.remoteGrant(
                parseTenant(tenant), id, actor, purpose, correlation, body));
    }

    @PostMapping("/remote-sessions/{id}/refuse")
    public ResponseEntity<ApiResponse<Map<String, Object>>> remoteRefuse(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Ref", required = false) String actorRef,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        String actor = requiredTrustHeader(firstString(actorId, actorRef), "X-Actor-Id");
        String purpose = requiredTrustHeader(purposeOfUse, "X-Purpose-Of-Use");
        String correlation = requiredTrustHeader(correlationId, "X-Correlation-Id");
        return ok(mvumoService.remoteRefuse(
                parseTenant(tenant), id, actor, purpose, correlation, body));
    }

    @PostMapping("/remote-sessions/{id}/withdraw")
    public ResponseEntity<ApiResponse<Map<String, Object>>> remoteWithdraw(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Ref", required = false) String actorRef,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        String actor = requiredTrustHeader(firstString(actorId, actorRef), "X-Actor-Id");
        String purpose = requiredTrustHeader(purposeOfUse, "X-Purpose-Of-Use");
        String correlation = requiredTrustHeader(correlationId, "X-Correlation-Id");
        return ok(mvumoService.remoteWithdraw(
                parseTenant(tenant), id, actor, purpose, correlation, body));
    }

    @PostMapping("/offline-sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> offline(@RequestBody Map<String, Object> body) {
        return ok(mvumoService.offlineSyncBatch(body));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> templates(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant) {
        return ok(mvumoService.listTemplates(parseTenant(tenant)));
    }

    @GetMapping("/templates/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> oneTemplate(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant, @PathVariable UUID id) {
        return ok(mvumoService.getTemplate(parseTenant(tenant), id));
    }

    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<Map<String, Object>>> postTemplate(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @RequestBody Map<String, Object> body) {
        return ok(mvumoService.createTemplate(parseTenant(tenant), body));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> putTemplate(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ok(mvumoService.updateTemplate(parseTenant(tenant), id, body));
    }

    @GetMapping("/audit/{consentId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> audit(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant, @PathVariable UUID consentId) {
        return ok(mvumoService.auditForConsent(parseTenant(tenant), consentId));
    }

    @GetMapping("/consents/{id}/proof")
    public ResponseEntity<ApiResponse<Map<String, Object>>> proof(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenant, @PathVariable UUID id) {
        return ok(mvumoService.getProof(parseTenant(tenant), id));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String actorRef(String header) {
        if (header != null && !header.isBlank()) {
            return header;
        }
        return "system";
    }

    private static String requiredTrustHeader(String value, String headerName) {
        String normalized = firstString(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, headerName + " is required");
        }
        return normalized;
    }

    private static String sourceChannelFrom(Map<String, Object> body, String fallback) {
        if (body != null && body.get("sourceChannel") != null) {
            return body.get("sourceChannel").toString();
        }
        return fallback;
    }

    private static String assuranceFrom(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        if (body.get("assuranceMethod") != null) {
            return body.get("assuranceMethod").toString();
        }
        return null;
    }

    private static String actorRelationshipFrom(Map<String, Object> body) {
        if (body == null || body.get("actorRelationship") == null) {
            return "unknown";
        }
        return body.get("actorRelationship").toString();
    }

    private static UUID parseTenant(String header) {
        if (header == null || header.isBlank()) {
            return TenantIds.PLATFORM;
        }
        return UUID.fromString(header);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data, java.util.UUID.randomUUID().toString()));
    }

    private static boolean isConsentDenied(Map<String, Object> result) {
        if (result == null) {
            return false;
        }
        String verdict = firstString(result.get("decision"), result.get("verdict"), result.get("status"));
        return verdict != null
                && ("deny".equalsIgnoreCase(verdict)
                || "denied".equalsIgnoreCase(verdict)
                || "forbidden".equalsIgnoreCase(verdict)
                || "reject".equalsIgnoreCase(verdict)
                || "rejected".equalsIgnoreCase(verdict));
    }

    private static String firstString(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String s = value.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }
}
