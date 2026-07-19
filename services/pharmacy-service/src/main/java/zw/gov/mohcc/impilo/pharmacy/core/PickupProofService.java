package zw.gov.mohcc.impilo.pharmacy.core;

import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PickupProofEntity;

import java.util.UUID;

/**
 * Service managing pickup proof creation and claim workflows.
 *
 * <p>Creates proof records with OTP tokens and manages the claim
 * lifecycle including token validation and expiry.</p>
 */
public interface PickupProofService {

    /**
     * Create a new pickup proof for a dispense order.
     */
    PickupProofEntity createProof(UUID orderId, PickupMethod method, String delegatedTo);

    /**
     * Claim a pickup using a token and device fingerprint.
     */
    PickupProofEntity claimProof(String token, String deviceFingerprint);

    /**
     * Claim a pickup, optionally verifying the collector's identity through the shared
     * biometric seam. When a probe is supplied: MATCH → the proof is marked collected
     * via {@link zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod#BIOMETRIC}; NO_MATCH →
     * the collection is rejected; UNAVAILABLE / NO_REFERENCE → fall back to the token check.
     * Absent biometric args ⇒ identical to {@link #claimProof(String, String)}.
     */
    PickupProofEntity claimProof(String token, String deviceFingerprint,
                                 String biometricSubjectRef, String biometricModality,
                                 String biometricProbeBase64);
}
