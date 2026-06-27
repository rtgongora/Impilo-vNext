package zw.gov.mohcc.impilo.vito.core.card;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.core.CardStatus;
import zw.gov.mohcc.impilo.vito.core.RevocationReason;
import zw.gov.mohcc.impilo.vito.core.did.SovereignIdGenerator;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.persistence.entity.CardStateTransitionEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.SmartCardEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.CardStateTransitionRepository;
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
    private final CardStateTransitionRepository transitionRepository;
    private final SovereignIdGenerator didGenerator;
    private final VitoProperties properties;
    private final EventOutboxRepository outboxRepository;

    public CardLifecycleService(SmartCardRepository cardRepository,
                                 CardStateTransitionRepository transitionRepository,
                                 SovereignIdGenerator didGenerator,
                                 VitoProperties properties,
                                 EventOutboxRepository outboxRepository) {
        this.cardRepository = cardRepository;
        this.transitionRepository = transitionRepository;
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

        // G032: do NOT fabricate a key. A REQUESTED card legitimately has no key yet — the
        // secure-element keypair is generated at print time and provisioned via markPrinted.
        // Storing a real key here is allowed (e.g. recovery supplies the new key up front).
        String resolvedPublicKey = (publicKey != null && !publicKey.isBlank()) ? publicKey : null;
        String resolvedRequestedBy = (requestedBy != null && !requestedBy.isBlank())
                ? requestedBy
                : "system";

        SmartCardEntity card = new SmartCardEntity();
        card.setTenantId(tenantId);
        card.setHealthId(healthId);
        card.setCardNumber(cardNumber);
        card.setDidUri(did);
        card.setPublicKey(resolvedPublicKey);
        card.setStatus(CardStatus.REQUESTED);
        card.setRequestedBy(resolvedRequestedBy);
        card.setExpiresAt(OffsetDateTime.now().plusYears(properties.getCard().getExpiryYears()));

        card = cardRepository.save(card);
        recordTransition(card, null, CardStatus.REQUESTED, resolvedRequestedBy, null);

        publishEvent("SMART_CARD", card.getId().toString(), "CARD_REQUESTED",
                String.format("{\"cardId\":%d,\"healthId\":\"%s\",\"did\":\"%s\"}",
                        card.getId(), healthId, did));

        return card;
    }

    /**
     * Transition: REQUESTED → PRINTED (card has been produced).
     *
     * <p>G032: the card's secure-element keypair is generated when the card is personalized,
     * so this is the point at which the real public key is provisioned. {@code publicKey} may
     * be {@code null} only if the card already carries a real key (e.g. supplied at request).
     */
    @Transactional
    public SmartCardEntity markPrinted(UUID tenantId, Long cardId, String publicKey) {
        SmartCardEntity card = getCard(tenantId, cardId);
        assertStatus(card, CardStatus.REQUESTED, "print");

        if (publicKey != null && !publicKey.isBlank()) {
            card.setPublicKey(publicKey);
        }

        CardStatus from = card.getStatus();
        card.setStatus(CardStatus.PRINTED);
        card.setPrintedAt(OffsetDateTime.now());
        card = cardRepository.save(card);
        recordTransition(card, from, CardStatus.PRINTED, resolveActor(), null);
        return card;
    }

    /**
     * Transition: PRINTED → ACTIVE (card has been handed to client and activated).
     *
     * <p>G032: fails closed — a card cannot become usable without a real, provisioned public
     * key. A missing key or a legacy {@code DEV-PLACEHOLDER-} value is rejected.
     */
    @Transactional
    public SmartCardEntity activate(UUID tenantId, Long cardId) {
        SmartCardEntity card = getCard(tenantId, cardId);
        assertStatus(card, CardStatus.PRINTED, "activate");

        if (!hasRealPublicKey(card)) {
            throw new IllegalStateException(
                    "Cannot activate card " + cardId + ": no real public key provisioned. "
                            + "Provision the card's public key at print before activation.");
        }

        CardStatus from = card.getStatus();
        card.setStatus(CardStatus.ACTIVE);
        card.setActivatedAt(OffsetDateTime.now());
        card = cardRepository.save(card);
        recordTransition(card, from, CardStatus.ACTIVE, resolveActor(), null);

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

        CardStatus from = card.getStatus();
        card.setStatus(CardStatus.INACTIVE);
        card.setInactivatedAt(OffsetDateTime.now());
        card = cardRepository.save(card);
        recordTransition(card, from, CardStatus.INACTIVE, resolveActor(), null);
        return card;
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

        CardStatus from = card.getStatus();
        card.setStatus(CardStatus.REVOKED);
        card.setRevokedAt(OffsetDateTime.now());
        card.setRevocationReason(reason.name());
        card = cardRepository.save(card);
        recordTransition(card, from, CardStatus.REVOKED, resolveActor(), reason.name());

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

    /**
     * G032: a card has a real, usable public key only if one is present and it is not a legacy
     * {@code DEV-PLACEHOLDER-} fabrication (rows created before the fail-closed fix).
     */
    private boolean hasRealPublicKey(SmartCardEntity card) {
        String key = card.getPublicKey();
        return key != null && !key.isBlank() && !key.startsWith("DEV-PLACEHOLDER-");
    }

    private String generateCardNumber() {
        // Format: IMP-YYYY-XXXXXXXX (8 random hex chars)
        return String.format("IMP-%d-%s",
                OffsetDateTime.now().getYear(),
                UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private void recordTransition(SmartCardEntity card, CardStatus from, CardStatus to,
                                    String actor, String reason) {
        CardStateTransitionEntity t = new CardStateTransitionEntity();
        t.setTenantId(card.getTenantId());
        t.setCardId(card.getId());
        t.setFromStatus(from != null ? from.name() : null);
        t.setToStatus(to.name());
        t.setTransitionedBy(actor);
        t.setReason(reason);
        transitionRepository.save(t);
    }

    private String resolveActor() {
        var ctx = TrustContextHolder.get();
        return ctx != null ? ctx.actorId() : "SYSTEM";
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
