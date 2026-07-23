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
     * OF-B16: create a pickup proof with an optional named recipient. For
     * {@link PickupMethod#SMS_REFERENCE} the generated credential is a short
     * opaque reference intended for SMS delivery (never a token; §8.11.3) —
     * the plaintext reference is returned exactly once via the entity's
     * transient deviceFingerprint stash, mirroring the token behaviour.
     */
    PickupProofEntity createProof(UUID orderId, PickupMethod method, String delegatedTo,
                                  String namedRecipient);

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

    /**
     * OF-B16: claim a pickup recording who physically collected and at which
     * server-assigned verification grade (§12.4 proof ladder).
     */
    PickupProofEntity claimProof(String token, String deviceFingerprint,
                                 String collectorIdentity,
                                 String biometricSubjectRef, String biometricModality,
                                 String biometricProbeBase64);

    /**
     * OF-B16 named-recipient/SMS path: resolve an opaque SMS reference
     * server-side to its pending proof (returns the named recipient for the
     * staff identity check). No token is ever derivable from the reference.
     */
    PickupProofEntity resolveSmsReference(String reference);

    /**
     * OF-B16 named-recipient/SMS path: claim by opaque SMS reference. Fail-closed:
     * requires a non-blank collector identity, and when the proof names a
     * recipient the collector identity must match it. Grade
     * {@code SMS_REFERENCE_ID_CHECK}.
     */
    PickupProofEntity claimBySmsReference(String reference, String collectorIdentity,
                                          String deviceFingerprint);
}
