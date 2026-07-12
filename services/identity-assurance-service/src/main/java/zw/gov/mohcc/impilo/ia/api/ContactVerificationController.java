package zw.gov.mohcc.impilo.ia.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ia.core.ContactVerificationService;
import zw.gov.mohcc.impilo.ia.core.ContactVerificationService.ContactVerificationView;
import zw.gov.mohcc.impilo.ia.persistence.entity.AttestationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * R1 "Reachable" contact-verification attestation API (gateway trust ladder §4).
 *
 * <p>Service-to-service: the record endpoint is called by the experience BFF after it has
 * completed an OTP check against the contact channel (the BFF is the verifier; this service
 * is the attestation system of record). The read endpoint is consumed by policy composition
 * and SOS-callback flows ("does account X have a verified contact, and which channel?").</p>
 *
 * <p>Only masked contact values cross this boundary — the raw phone/email never reaches
 * identity-assurance.</p>
 */
@RestController
@RequestMapping("/internal/v1/attestations/contact-verified")
public class ContactVerificationController {

    private final ContactVerificationService contactVerificationService;

    public ContactVerificationController(ContactVerificationService contactVerificationService) {
        this.contactVerificationService = contactVerificationService;
    }

    /**
     * Record a CONTACT_VERIFIED attestation for {@code accountId}. The subject account is
     * carried in the body (during registration the subject is not yet the caller); the
     * recording principal is taken from trust context for audit.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ContactVerificationView>> recordContactVerified(
            @RequestBody RecordContactVerifiedRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        try {
            AttestationEntity attestation = contactVerificationService.recordContactVerified(
                    ctx.tenantId(), request.accountId(), ctx.actorId(), ctx.correlationId(),
                    request.channel(), request.maskedValue(), request.method());
            ContactVerificationView view = contactVerificationService
                    .getContactVerification(ctx.tenantId(), attestation.getActorId());
            return ResponseEntity.status(201).body(
                    ApiResponse.ok(view, ctx.correlationId().toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("VALIDATION", e.getMessage(), 400, ctx.correlationId().toString()));
        }
    }

    /** Does {@code accountId} have a verified contact + which channel(s)? */
    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<ContactVerificationView>> getContactVerification(
            @PathVariable("accountId") String accountId) {
        TrustContext ctx = TrustContextHolder.require();

        ContactVerificationView view =
                contactVerificationService.getContactVerification(ctx.tenantId(), accountId);
        return ResponseEntity.ok(ApiResponse.ok(view, ctx.correlationId().toString()));
    }

    /**
     * @param accountId   subject account (Keycloak user id / actor id)
     * @param channel     PHONE or EMAIL
     * @param maskedValue masked contact value only (e.g. {@code +263*******67}) — never raw PII
     * @param method      verification method; defaults to OTP
     */
    public record RecordContactVerifiedRequest(
            String accountId,
            String channel,
            String maskedValue,
            String method
    ) {}
}
