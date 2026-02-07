package zw.gov.mohcc.impilo.vito.core.card;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.core.CardStatus;
import zw.gov.mohcc.impilo.vito.core.RevocationReason;
import zw.gov.mohcc.impilo.vito.core.did.SovereignIdGenerator;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.SmartCardEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.SmartCardRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SMART Card Lifecycle Management (CMS).
 *
 * State machine: REQUESTED → PRINTED → ACTIVE → INACTIVE → REVOKED
 *
 * Invariants:
 *   - Only ONE card can be ACTIVE per client per tenant (DB constraint)
 *   - Every state transition is recorded in card_state_transition (audit trail)
 *   - Revocation triggers Secure Handover (wallet + DID transfer to new card)
 *
 * Card contains:
 *   - did:impilo URI (sovereign identity)
 *   - Public key (for offline signature verification)
 *   - Secure Health Summary (JWS/VC, written separately)
 */
@Service
public class CardLifecycleService {

    private final SmartCardRepository cardRepository;
    private final SovereignIdGenerator didGenerator;
    private final VitoProperties properties;
    private final EventOutboxRepository outboxRepository;

    public CardLifecycleService(SmartCardRepository cardRepository,
                                 SovereignIdGenerator didGenerator,
                                 VitoProperties properties,
                                 EventOutboxRepository outboxRepository) {
        this.cardRepository = cardRepository;
        this.didGenerator = didGenerator;
        this.properties = properties;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Request a new SMART Card for a client.
     * Generates a DID and creates a card in REQUESTED state.
     */
    @Transactional
    public SmartCardEntity requestCard(UUID tenantId, UUID healthId,
                                        String publicKey, String requestedBy) {
        // Check for existing active card
        Optional<SmartCardEntity> existing = cardRepository
                .findByTenantIdAndHealthIdAndStatus(tenantId, healthId, CardStatus.ACTIVE);

        if (existing.isPresent()) {
            throw new IllegalStateException("Client already has an active card. Revoke first.");
        }

        String did = didGenerator.generate(healthId);
        String cardNumber = generateCardNumber();

        SmartCardEntity card = new SmartCardEntity();
        card.setTenantId(tenantId);
        card.setHealthId(healthId);
        card.setCardNumber(cardNumber);
        card.setDidUri(did);
        card.setPublicKey(publicKey);
        card.setStatus(CardStatus.REQUESTED);
        card.setRequestedBy(requestedBy);
        card.setExpiresAt(OffsetDateTime.now().plusYears(properties.getCard().getExpiryYears()));

        card = cardRepository.save(card);

        publishEvent("SMART_CARD", card.getId().toString(), "CARD_REQUESTED",
                String.format("{\"cardId\":%d,\"healthId\":\"%s\",\"did\":\"%s\"}",
                        card.getId(), healthId, did));

        return card;
    }

    /**
     * Transition: REQUESTED → PRINTED (card has been produced).
     */
    @Transactional
    public SmartCardEntity markPrinted(UUID tenantId, Long cardId) {
        SmartCardEntity card = getCard(tenantId, cardId);
        assertStatus(card, CardStatus.REQUESTED, "print");

        card.setStatus(CardStatus.PRINTED);
        card.setPrintedAt(OffsetDateTime.now());
        return cardRepository.save(card);
    }

    /**
     * Transition: PRINTED → ACTIVE (card has been handed to client and activated).
     */
    @Transactional
    public SmartCardEntity activate(UUID tenantId, Long cardId) {
        SmartCardEntity card = getCard(tenantId, cardId);
        assertStatus(card, CardStatus.PRINTED, "activate");

        card.setStatus(CardStatus.ACTIVE);
        card.setActivatedAt(OffsetDateTime.now());
        card = cardRepository.save(card);

        publishEvent("SMART_CARD", card.getId().toString(), "CARD_ACTIVATED",
                String.format("{\"cardId\":%d,\"healthId\":\"%s\"}", card.getId(), card.getHealthId()));

        return card;
    }

    /**
     * Transition: ACTIVE → INACTIVE (temporary suspension).
     */
    @Transactional
    public SmartCardEntity inactivate(UUID tenantId, Long cardId) {
        SmartCardEntity card = getCard(tenantId, cardId);
        assertStatus(card, CardStatus.ACTIVE, "inactivate");

        card.setStatus(CardStatus.INACTIVE);
        card.setInactivatedAt(OffsetDateTime.now());
        return cardRepository.save(card);
    }

    /**
     * Transition: ACTIVE|INACTIVE → REVOKED (permanent).
     * Returns the revoked card for Secure Handover processing.
     */
    @Transactional
    public SmartCardEntity revoke(UUID tenantId, Long cardId, RevocationReason reason) {
        SmartCardEntity card = getCard(tenantId, cardId);

        if (card.getStatus() != CardStatus.ACTIVE && card.getStatus() != CardStatus.INACTIVE) {
            throw new IllegalStateException("Can only revoke ACTIVE or INACTIVE cards");
        }

        card.setStatus(CardStatus.REVOKED);
        card.setRevokedAt(OffsetDateTime.now());
        card.setRevocationReason(reason.name());
        card = cardRepository.save(card);

        publishEvent("SMART_CARD", card.getId().toString(), "CARD_REVOKED",
                String.format("{\"cardId\":%d,\"healthId\":\"%s\",\"reason\":\"%s\"}",
                        card.getId(), card.getHealthId(), reason));

        return card;
    }

    @Transactional(readOnly = true)
    public Optional<SmartCardEntity> getActiveCard(UUID tenantId, UUID healthId) {
        return cardRepository.findByTenantIdAndHealthIdAndStatus(tenantId, healthId, CardStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<SmartCardEntity> getCardHistory(UUID tenantId, UUID healthId) {
        return cardRepository.findByTenantIdAndHealthIdOrderByCreatedAtDesc(tenantId, healthId);
    }

    @Transactional(readOnly = true)
    public Page<SmartCardEntity> listByStatus(UUID tenantId, CardStatus status, Pageable pageable) {
        return cardRepository.findByTenantIdAndStatus(tenantId, status, pageable);
    }

    // --- Helpers ---

    private SmartCardEntity getCard(UUID tenantId, Long cardId) {
        return cardRepository.findById(cardId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Card not found"));
    }

    private void assertStatus(SmartCardEntity card, CardStatus expected, String action) {
        if (card.getStatus() != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " card in " + card.getStatus() + " state (expected: " + expected + ")");
        }
    }

    private String generateCardNumber() {
        // Format: IMP-YYYY-XXXXXXXX (8 random hex chars)
        return String.format("IMP-%d-%s",
                OffsetDateTime.now().getYear(),
                UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
