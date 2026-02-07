package zw.gov.mohcc.impilo.tshepo.consent.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ConsentDirectiveDto;
import zw.gov.mohcc.impilo.tshepo.consent.service.ConsentCrudService;

import java.util.UUID;

/**
 * Patient-facing portal endpoints for consent self-service.
 *
 * <p>These endpoints are consumed by the patient portal (UI) and allow patients
 * to view their active consents and self-service revoke them.</p>
 *
 * <p>The patient identity is derived from the trust context (x-actor-id header),
 * which represents the authenticated patient in the portal context.</p>
 */
@RestController
@RequestMapping("/v1/consent/portal")
public class PortalConsentController {

    private final ConsentCrudService consentService;

    public PortalConsentController(ConsentCrudService consentService) {
        this.consentService = consentService;
    }

    /**
     * Lists the authenticated patient's active consent directives.
     * The patient identity is extracted from the trust context headers.
     */
    @GetMapping("/my-consents")
    public ResponseEntity<ApiResponse<PagedResponse<ConsentDirectiveDto>>> myConsents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        TrustContext ctx = TrustContextHolder.get();
        UUID tenantId = resolveTenantId(ctx);
        String patientRef = resolveActorId(ctx);
        String correlationId = resolveCorrelationId(ctx);

        Page<ConsentDirectiveDto> results = consentService.findByPatient(
                tenantId, patientRef, "ACTIVE",
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        PagedResponse<ConsentDirectiveDto> paged = PagedResponse.of(
                results.getContent(), results.getNumber(), results.getSize(), results.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    /**
     * Patient self-service consent revocation.
     * The patient can only revoke their own consents (verified via trust context).
     */
    @PostMapping("/revoke/{id}")
    public ResponseEntity<ApiResponse<ConsentDirectiveDto>> revoke(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.get();
        String patientRef = resolveActorId(ctx);
        String correlationId = resolveCorrelationId(ctx);

        // Verify the consent belongs to this patient before revoking
        ConsentDirectiveDto existing = consentService.findById(id);
        if (!existing.patientRef().equals(patientRef)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("FORBIDDEN",
                            "Cannot revoke consent belonging to another patient", 403, correlationId));
        }

        ConsentDirectiveDto dto = consentService.revoke(id, "Patient self-service revocation", patientRef);
        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId));
    }

    // --- Helpers ---

    private UUID resolveTenantId(TrustContext ctx) {
        if (ctx != null && ctx.tenantId() != null) {
            return ctx.tenantId();
        }
        throw new IllegalStateException("Tenant ID not available in trust context");
    }

    private String resolveActorId(TrustContext ctx) {
        if (ctx != null && ctx.actorId() != null) {
            return ctx.actorId();
        }
        throw new IllegalStateException("Actor ID not available in trust context");
    }

    private String resolveCorrelationId(TrustContext ctx) {
        if (ctx != null && ctx.correlationId() != null) {
            return ctx.correlationId().toString();
        }
        return UUID.randomUUID().toString();
    }
}
