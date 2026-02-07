package zw.gov.mohcc.impilo.vito.core.recovery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.core.CardStatus;
import zw.gov.mohcc.impilo.vito.core.RevocationReason;
import zw.gov.mohcc.impilo.vito.core.card.CardLifecycleService;
import zw.gov.mohcc.impilo.vito.core.wallet.WalletService;
import zw.gov.mohcc.impilo.vito.persistence.entity.SmartCardEntity;

import java.util.UUID;

/**
 * Secure Handover Service — card replacement with DID and wallet transfer.
 *
 * Process:
 *   1. Revoke the old card (ACTIVE → REVOKED)
 *   2. Request a new card (generates new DID, creates REQUESTED card)
 *   3. Transfer wallet balance from old card's wallet to new card's wallet
 *   4. The did:impilo from the new card becomes the client's active DID
 *
 * This ensures continuity of identity and financial state across card
 * replacements without exposing PII during the transfer.
 */
@Service
public class RecoveryService {

    private final CardLifecycleService cardService;
    private final WalletService walletService;

    public RecoveryService(CardLifecycleService cardService, WalletService walletService) {
        this.cardService = cardService;
        this.walletService = walletService;
    }

    /**
     * Execute Secure Handover: revoke old card, issue new card, transfer wallet.
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
        // Step 1: Revoke old card
        SmartCardEntity revokedCard = cardService.revoke(tenantId, oldCardId, reason);

        // Step 2: Request new card
        SmartCardEntity newCard = cardService.requestCard(tenantId, healthId, newPublicKey, requestedBy);

        // Step 3: Transfer wallet balance
        String txRef = "HANDOVER-" + UUID.randomUUID().toString().substring(0, 8);
        walletService.transferBalance(tenantId, healthId, healthId, "ZWL", txRef);

        // Link new card to wallet if it exists
        walletService.getWallet(tenantId, healthId)
                .ifPresent(wallet -> {
                    wallet.setCardId(newCard.getId());
                });

        return newCard;
    }
}
