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
        return requestCard(tenantId, healthId, publicKey, requestedBy, "PHYSICAL");
    }

    /**
     * Request a card in a given form. A VIRTUAL card is a wallet pass on the phone: the device
     * generates its keypair and supplies the public key up front (no physical print), so it can be
     * provisioned + activated without the card-print-agent. PHYSICAL keeps the print-then-provision flow.
     */
    @Transactional
    public SmartCardEntity requestCard(UUID tenantId, UUID healthId,
                                        String publicKey, String requestedBy, String cardForm) {
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
        card.setCardForm("VIRTUAL".equalsIgnoreCase(cardForm) ? "VIRTUAL" : "PHYSICAL");
        card.setRequestedBy(resolvedRequestedBy);
        card.setExpiresAt(OffsetDateTime.now().plusYears(properties.getCard().getExpiryYears()));

        card = cardRepository.save(card);
        recordTransition(card, null, CardStatus.REQUESTED, resolvedRequestedBy, null);

        publishEvent("SMART_CARD", card.getId().toString(), "CARD_REQUESTED", cardPayload(card));

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
        // Printed = the SE public key is now provisioned — the credential is cryptographically real.
        publishEvent("SMART_CARD", card.getId().toString(), "CARD_PRINTED", cardPayload(card));
        return card;
    }

    /**
     * Transition: PRINTED → ACTIVE (card has been handed to client and activated).
     *
     * <p>VIRTUAL cards may activate from REQUESTED when a device public key was supplied at
     * request time — they have no physical print step. That path records a device-provisioned
     * PRINTED transition then ACTIVATES, so the audit trail stays complete.
     *
     * <p>G032: fails closed — a card cannot become usable without a real, provisioned public
     * key. A missing key or a legacy {@code DEV-PLACEHOLDER-} value is rejected.
     */
    @Transactional
    public SmartCardEntity activate(UUID tenantId, Long cardId) {
        SmartCardEntity card = getCard(tenantId, cardId);

        if (card.getStatus() == CardStatus.REQUESTED
                && "VIRTUAL".equalsIgnoreCase(card.getCardForm())) {
            if (!hasRealPublicKey(card)) {
                throw new IllegalStateException(
                        "Cannot activate virtual card " + cardId + ": no device public key provisioned.");
            }
            card = markPrinted(tenantId, cardId, null);
        }

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

        publishEvent("SMART_CARD", card.getId().toString(), "CARD_ACTIVATED", cardPayload(card));

        return card;
    }

    /**
     * Re-provision the device/SE public key on an existing VIRTUAL card (e.g. citizen re-enrolls
     * a phone). Publishes {@code CARD_PRINTED} so mushe can refresh its verification key cache.
     */
    @Transactional
    public SmartCardEntity provisionDevicePublicKey(UUID tenantId, Long cardId, String publicKey) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("publicKey is required");
        }
        SmartCardEntity card = getCard(tenantId, cardId);
        if (!"VIRTUAL".equalsIgnoreCase(card.getCardForm())) {
            throw new IllegalStateException("Device key re-provisioning is only for VIRTUAL cards");
        }
        card.setPublicKey(publicKey);
        card = cardRepository.save(card);
        publishEvent("SMART_CARD", card.getId().toString(), "CARD_PRINTED", cardPayload(card));
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
        // Suspension must reach the money + PHR facets (e.g. freeze the card's purse / stop PHR sync).
        publishEvent("SMART_CARD", card.getId().toString(), "CARD_SUSPENDED", cardPayload(card));
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

        publishEvent("SMART_CARD", card.getId().toString(), "CARD_REVOKED", cardPayload(card));

        return card;
    }

    /**
     * Link a freshly-requested card as the replacement of a revoked one (Secure Handover) and emit
     * {@code CARD_REPLACED} — carries {@code previousCardId} so downstream facets migrate the money
     * wallet reference and re-sync the PHR to the new card.
     */
    @Transactional
    public SmartCardEntity recordReplacement(UUID tenantId, Long newCardId, Long oldCardId) {
        SmartCardEntity card = getCard(tenantId, newCardId);
        card.setPreviousCardId(oldCardId);
        card = cardRepository.save(card);
        publishEvent("SMART_CARD", card.getId().toString(), "CARD_REPLACED", cardPayload(card));
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

    /**
     * Canonical CardCredential projection carried on every {@code SMART_CARD} lifecycle event — the
     * shared spine other services (mushe-wallet money facet, PHR carrier) reference by
     * {@code cardNumber} without minting a second card identity. See
     * {@code docs/architecture/UNIFIED_SMART_CARD_ARCHITECTURE.md}.
     */
    private static String cardPayload(SmartCardEntity card) {
        return "{"
                + "\"cardId\":" + card.getId()
                + ",\"cardNumber\":" + jsonStr(card.getCardNumber())
                + ",\"healthId\":" + jsonStr(card.getHealthId() != null ? card.getHealthId().toString() : null)
                + ",\"didUri\":" + jsonStr(card.getDidUri())
                + ",\"status\":" + jsonStr(card.getStatus() != null ? card.getStatus().name() : null)
                + ",\"cardForm\":" + jsonStr(card.getCardForm())
                + ",\"publicKey\":" + jsonStr(card.getPublicKey())
                + ",\"previousCardId\":" + (card.getPreviousCardId() != null ? card.getPreviousCardId().toString() : "null")
                + ",\"revocationReason\":" + jsonStr(card.getRevocationReason())
                + "}";
    }

    private static String jsonStr(String v) {
        return v == null ? "null" : "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
