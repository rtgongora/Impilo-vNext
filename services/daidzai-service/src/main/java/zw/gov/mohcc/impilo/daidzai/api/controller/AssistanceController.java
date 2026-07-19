package zw.gov.mohcc.impilo.daidzai.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.daidzai.core.AssistanceService;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AssistanceContributionSeenEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AssistanceEventEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AssistanceRequestEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Assistance-case (crowdfunding) API. The verification lifecycle is the trust story:
 * DRAFT cases are private to the requester; only provider-attested (verified-ACTIVE)
 * cases are publicly listed; the verified state carries a signed anonymised attestation
 * donors can independently check. Money endpoints delegate to mushe by reference.
 */
@RestController
@RequestMapping("/internal/v1/daidzai/assistance")
public class AssistanceController {

    private final AssistanceService service;

    public AssistanceController(AssistanceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AssistanceRequestEntity> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestBody Map<String, Object> body) {
        try {
            AssistanceRequestEntity a = service.create(tenantId,
                    str(body, "requesterType") != null ? str(body, "requesterType")
                            : ("PROVIDER".equals(actorType) ? "PROVIDER" : "CITIZEN"),
                    actorId,
                    str(body, "subjectIdentityMode"), str(body, "subjectCpid"),
                    str(body, "category"), str(body, "title"), str(body, "story"),
                    decimal(body, "targetAmount"), str(body, "currency"),
                    odt(body, "endsAt"), uuid(body, "beneficiaryWalletId"));
            return ResponseEntity.status(HttpStatus.CREATED).body(a);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Publicly-listable cases: verified-ACTIVE only. */
    @GetMapping
    public List<AssistanceRequestEntity> listPublic(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return service.listPublic(tenantId);
    }

    @GetMapping("/mine")
    public List<AssistanceRequestEntity> listMine(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId) {
        return service.listMine(tenantId, actorId);
    }

    @GetMapping("/{id}")
    public AssistanceRequestEntity get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId, @PathVariable UUID id) {
        try {
            return service.require(tenantId, id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/{id}/timeline")
    public List<AssistanceEventEntity> timeline(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId, @PathVariable UUID id) {
        return service.timelineOf(tenantId, id);
    }

    @GetMapping("/{id}/contributions")
    public List<AssistanceContributionSeenEntity> contributions(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId, @PathVariable UUID id) {
        return service.contributionsOf(tenantId, id);
    }

    @PostMapping("/{id}/request-verification")
    public AssistanceRequestEntity requestVerification(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return guarded(() -> service.requestVerification(tenantId, id, actorId, str(body, "consentRef")));
    }

    /** Provider-only attestation — the sole path to verified-ACTIVE. */
    @PostMapping("/{id}/attest")
    public AssistanceRequestEntity attest(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return guarded(() -> service.attest(tenantId, id, actorId, actorType, str(body, "verifyingOrgRef")));
    }

    @PostMapping("/{id}/reject")
    public AssistanceRequestEntity reject(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return guarded(() -> service.reject(tenantId, id, actorId, actorType,
                body == null ? null : str(body, "reason")));
    }

    @PostMapping("/{id}/revoke")
    public AssistanceRequestEntity revoke(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return guarded(() -> service.revoke(tenantId, id, actorId, actorType,
                body == null ? null : str(body, "reason")));
    }

    @PostMapping("/{id}/cancel")
    public AssistanceRequestEntity cancel(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @PathVariable UUID id) {
        return guarded(() -> service.cancel(tenantId, id, actorId));
    }

    @PostMapping("/{id}/release")
    public AssistanceRequestEntity release(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return guarded(() -> service.release(tenantId, id, actorId,
                body == null ? null : decimal(body, "amount")));
    }

    // ---- helpers ----

    private interface Action { AssistanceRequestEntity run(); }

    private AssistanceRequestEntity guarded(Action action) {
        try {
            return action.run();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        String s = v == null ? null : v.toString().trim();
        return s == null || s.isEmpty() ? null : s;
    }

    private static BigDecimal decimal(Map<String, Object> body, String key) {
        String s = str(body, key);
        return s == null ? null : new BigDecimal(s);
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        String s = str(body, key);
        return s == null ? null : UUID.fromString(s);
    }

    private static OffsetDateTime odt(Map<String, Object> body, String key) {
        String s = str(body, key);
        return s == null ? null : OffsetDateTime.parse(s);
    }
}
