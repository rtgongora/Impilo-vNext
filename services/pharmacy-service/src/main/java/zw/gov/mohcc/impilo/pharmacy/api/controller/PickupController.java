package zw.gov.mohcc.impilo.pharmacy.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pharmacy.api.dto.PickupClaimRequest;
import zw.gov.mohcc.impilo.pharmacy.api.dto.PickupCreateRequest;
import zw.gov.mohcc.impilo.pharmacy.api.dto.PickupProofDto;
import zw.gov.mohcc.impilo.pharmacy.core.PickupProofService;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PickupProofEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

/**
 * REST controller for medication pickup proof operations.
 *
 * <p>Supports creating pickup proofs (OTP, biometric, ID-check, delegated, waiver)
 * and claiming them via token verification.</p>
 */
@RestController
@RequestMapping("/v1")
public class PickupController {

    private static final Logger log = LoggerFactory.getLogger(PickupController.class);

    private final PickupProofService pickupProofService;

    public PickupController(PickupProofService pickupProofService) {
        this.pickupProofService = pickupProofService;
    }

    /**
     * Create a pickup proof for a dispense order.
     *
     * <p>Generates a proof record with the specified verification method.
     * For OTP method, a one-time pin is generated and should be sent to the patient.</p>
     */
    @PostMapping("/dispense-orders/{id}/pickup/create")
    public ResponseEntity<ApiResponse<PickupProofDto>> createPickupProof(
            @PathVariable UUID id,
            @Valid @RequestBody PickupCreateRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        PickupProofEntity proof = pickupProofService.createProof(
                id, request.method(), request.delegatedTo(), request.namedRecipient());

        log.info("Pickup proof created: orderId={}, method={}, proofId={}",
                id, request.method(), proof.getProofId());

        // M3 BUG-5: surface the freshly generated plaintext credential ONCE as
        // oneTimeCredential (creation response only; only the HMAC hash is
        // stored; reads/claims map through PickupProofDto.from and never carry it).
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(PickupProofDto.created(proof, proof.getOneTimeCredential()), correlationId));
    }

    /**
     * Claim a medication pickup using a token.
     *
     * <p>Validates the provided token against the proof record and marks it
     * as claimed if valid and not expired.</p>
     */
    @PostMapping("/pickup/claim")
    public ResponseEntity<ApiResponse<PickupProofDto>> claimPickup(
            @Valid @RequestBody PickupClaimRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        PickupProofEntity proof = pickupProofService.claimProof(
                request.token(), request.deviceFingerprint(),
                request.collectorIdentity(),
                request.biometricSubjectRef(), request.biometricModality(),
                request.biometricProbeBase64());

        log.info("Pickup claimed: proofId={}, claimedBy={}",
                proof.getProofId(), proof.getClaimedBy());

        return ResponseEntity.ok(ApiResponse.ok(PickupProofDto.from(proof), correlationId));
    }

    public record SmsReferenceResolveRequest(@jakarta.validation.constraints.NotBlank String reference) {}

    public record SmsReferenceClaimRequest(
            @jakarta.validation.constraints.NotBlank String reference,
            @jakarta.validation.constraints.NotBlank String collectorIdentity,
            String deviceFingerprint) {}

    /**
     * OF-B16 named-recipient/SMS path: resolve an opaque SMS reference
     * server-side (staff-side lookup — returns the named recipient for the
     * identity check; no token exists in the SMS channel).
     */
    @PostMapping("/pickup/resolve-reference")
    public ResponseEntity<ApiResponse<PickupProofDto>> resolveReference(
            @Valid @RequestBody SmsReferenceResolveRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        PickupProofEntity proof = pickupProofService.resolveSmsReference(request.reference());

        return ResponseEntity.ok(ApiResponse.ok(PickupProofDto.from(proof), correlationId));
    }

    /**
     * OF-B16 named-recipient/SMS path: claim by opaque reference after the staff
     * identity check of the named recipient. Fail-closed on identity mismatch.
     */
    @PostMapping("/pickup/claim-by-reference")
    public ResponseEntity<ApiResponse<PickupProofDto>> claimByReference(
            @Valid @RequestBody SmsReferenceClaimRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        PickupProofEntity proof = pickupProofService.claimBySmsReference(
                request.reference(), request.collectorIdentity(), request.deviceFingerprint());

        log.info("Pickup claimed by SMS reference: proofId={}, collector={}",
                proof.getProofId(), proof.getCollectorIdentity());

        return ResponseEntity.ok(ApiResponse.ok(PickupProofDto.from(proof), correlationId));
    }
}
