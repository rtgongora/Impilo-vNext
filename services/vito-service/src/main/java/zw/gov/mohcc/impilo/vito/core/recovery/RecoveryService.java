package zw.gov.mohcc.impilo.vito.core.recovery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.core.CardStatus;
import zw.gov.mohcc.impilo.vito.core.RevocationReason;
import zw.gov.mohcc.impilo.vito.core.card.CardLifecycleService;
import zw.gov.mohcc.impilo.vito.persistence.entity.SmartCardEntity;

import java.util.UUID;

/**
 * Secure Handover Service — replacement of the SMART Card as an IDENTITY credential.
 *
 * Process:
 *   1. Revoke the old card (ACTIVE → REVOKED)
 *   2. Request a new card (generates new DID, creates REQUESTED card)
 *   3. The did:impilo from the new card becomes the client's active DID
 *
 * <p><b>Money is mushe-wallet-service's SoR (G28).</b> This flow no longer touches a wallet: VITO
 * owns the card only as an identity artifact, so the card's stored value + balance-preservation on
 * replacement are handled by {@code mushe-wallet-service} ({@code /internal/v1/cards/{id}/replace},
 * which keeps the wallet and swaps the card). VITO's own wallet ledger is deprecated and now has no
 * business caller. (Cross-service card-identity↔money-card linkage — so one physical-card replacement
 * drives both — remains an open orchestration decision; see the SoR map / execution report.)
 */
@Service
public class RecoveryService {

    private final CardLifecycleService cardService;

    public RecoveryService(CardLifecycleService cardService) {
        this.cardService = cardService;
    }

    /**
     * Execute Secure Handover of the identity card: revoke old, issue new (money wallet untouched —
     * that is mushe-wallet-service's responsibility).
     *
     * @param tenantId  tenant context
     * @param healthId  client health_id
     * @param oldCardId the card being replaced
     * @param reason    revocation reason
     * @param newPublicKey the new card's public key
     * @param requestedBy  actor performing the handover
     * @return the new card in REQUESTED state
     */
    @Transactional
    public SmartCardEntity secureHandover(UUID tenantId, UUID healthId,
                                           Long oldCardId, RevocationReason reason,
                                           String newPublicKey, String requestedBy) {
        // Step 1: Revoke old card (identity credential)
        cardService.revoke(tenantId, oldCardId, reason);

        // Step 2: Request new card (new DID)
        return cardService.requestCard(tenantId, healthId, newPublicKey, requestedBy);
    }
}
