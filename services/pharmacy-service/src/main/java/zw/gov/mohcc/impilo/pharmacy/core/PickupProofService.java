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
}
