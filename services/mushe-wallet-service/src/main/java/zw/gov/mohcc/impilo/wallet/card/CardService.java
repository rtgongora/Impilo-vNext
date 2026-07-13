package zw.gov.mohcc.impilo.wallet.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.EventOutboxRepository;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Smart card lifecycle manager.
 *
 * <p>Handles issuance, activation, blocking, replacement, and PIN verification
 * for Mushe smart cards (dual health + payment). Every state mutation writes
 * an outbox event for reliable Kafka publishing via the outbox pattern.</p>
 */
@Service
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    private static final int CARD_NUMBER_LENGTH = 16;
    private static final int EXPIRY_YEARS = 5;
    private static final int DEFAULT_PIN_TRIES = 3;

    private final CardRepository cardRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    public CardService(CardRepository cardRepository,
                       EventOutboxRepository outboxRepository,
                       ObjectMapper objectMapper) {
        this.cardRepository = cardRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.secureRandom = new SecureRandom();
    }

    // ── Issue Card ──────────────────────────────────────────────────────

    /**
     * Issue a new smart card for a wallet.
     *
     * @param walletId    the wallet to link the card to
     * @param cardType    DUAL, PAYMENT_ONLY, or HEALTH_ONLY
     * @param cardNetwork VISA, ZIMSWITCH, or DUAL_NETWORK
     * @return the newly created CardEntity
     */
    @Transactional
    public CardEntity issueCard(UUID walletId, String cardType, String cardNetwork, UUID tenantId) {
        String cardNumber = generateLuhnValidCardNumber();

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiry = now.plusYears(EXPIRY_YEARS);

        CardEntity card = new CardEntity();
        card.setCardId(UUID.randomUUID());
        card.setTenantId(tenantId);
        card.setWalletId(walletId);
        card.setCardNumber(cardNumber);
        card.setCardType(cardType);
        card.setCardNetwork(cardNetwork);
        card.setExpiryMonth(expiry.getMonthValue());
        card.setExpiryYear(expiry.getYear());
        card.setStatus("ISSUED");
        card.setPinTriesRemaining(DEFAULT_PIN_TRIES);
        card.setIssuedAt(now);

        card = cardRepository.save(card);

        writeOutboxEvent(card, "CARD_ISSUED", tenantId, Map.of(
                "cardId", card.getCardId().toString(),
                "walletId", walletId.toString(),
                "cardType", cardType,
                "cardNetwork", cardNetwork,
                "maskedCardNumber", maskCardNumber(cardNumber)
        ));

        log.info("Issued card cardId={} for walletId={}", card.getCardId(), walletId);
        return card;
    }

    // ── Activate Card ───────────────────────────────────────────────────

    /**
     * Activate a card by setting a PIN (BCrypt hashed).
     */
    @Transactional
    public CardEntity activateCard(UUID cardId, String pin, UUID tenantId) {
        CardEntity card = findCardOrThrow(cardId);
        if (!"ISSUED".equals(card.getStatus())) {
            throw new IllegalStateException("Card must be in ISSUED status to activate; current=" + card.getStatus());
        }

        card.setPinHash(passwordEncoder.encode(pin));
        card.setStatus("ACTIVE");
        card.setActivatedAt(OffsetDateTime.now());
        card.setPinTriesRemaining(DEFAULT_PIN_TRIES);
        card = cardRepository.save(card);

        writeOutboxEvent(card, "CARD_ACTIVATED", tenantId, Map.of(
                "cardId", cardId.toString(),
                "walletId", card.getWalletId().toString()
        ));

        log.info("Activated card cardId={}", cardId);
        return card;
    }

    // ── Block Card ──────────────────────────────────────────────────────

    /**
     * Block a card with a reason.
     */
    @Transactional
    public CardEntity blockCard(UUID cardId, String reason, UUID tenantId) {
        CardEntity card = findCardOrThrow(cardId);
        if ("BLOCKED".equals(card.getStatus())) {
            throw new IllegalStateException("Card is already blocked");
        }

        card.setStatus("BLOCKED");
        card.setBlockedAt(OffsetDateTime.now());
        card.setBlockedReason(reason);
        card = cardRepository.save(card);

        writeOutboxEvent(card, "CARD_BLOCKED", tenantId, Map.of(
                "cardId", cardId.toString(),
                "walletId", card.getWalletId().toString(),
                "reason", reason
        ));

        log.info("Blocked card cardId={} reason={}", cardId, reason);
        return card;
    }

    // ── Unblock Card ────────────────────────────────────────────────────

    /**
     * Unblock a card, restoring it to ACTIVE.
     */
    @Transactional
    public CardEntity unblockCard(UUID cardId, UUID tenantId) {
        CardEntity card = findCardOrThrow(cardId);
        if (!"BLOCKED".equals(card.getStatus())) {
            throw new IllegalStateException("Card must be in BLOCKED status to unblock; current=" + card.getStatus());
        }

        card.setStatus("ACTIVE");
        card.setBlockedAt(null);
        card.setBlockedReason(null);
        card.setPinTriesRemaining(DEFAULT_PIN_TRIES);
        card = cardRepository.save(card);

        writeOutboxEvent(card, "CARD_UNBLOCKED", tenantId, Map.of(
                "cardId", cardId.toString(),
                "walletId", card.getWalletId().toString()
        ));

        log.info("Unblocked card cardId={}", cardId);
        return card;
    }

    // ── Unified SMART card: react to VITO CardCredential lifecycle (Phase 2) ──

    /**
     * Link this money card to the canonical VITO CardCredential (its cardNumber) — the identity facet
     * of the same physical SMART card. Establishes the reference VITO card events act on.
     */
    @Transactional
    public CardEntity linkVitoCard(UUID cardId, String vitoCardNumber, UUID tenantId) {
        CardEntity card = findCardOrThrow(cardId);
        card.setVitoCardNumber(vitoCardNumber);
        return cardRepository.save(card);
    }

    /** Cache the linked VITO card's device/SE public key (from card events) for offline-txn verification. */
    @Transactional
    public void cacheVitoCardKey(String vitoCardNumber, String publicKeyPem) {
        if (vitoCardNumber == null || vitoCardNumber.isBlank() || publicKeyPem == null || publicKeyPem.isBlank()) {
            return;
        }
        cardRepository.findByVitoCardNumber(vitoCardNumber).ifPresent(card -> {
            if (!publicKeyPem.equals(card.getVitoCardPublicKey())) {
                card.setVitoCardPublicKey(publicKeyPem);
                cardRepository.save(card);
            }
        });
    }

    /**
     * Freeze the money card linked to a revoked/suspended VITO SMART card (identity facet). Idempotent
     * and best-effort: no-op if no money card is linked to that VITO cardNumber or it is already blocked.
     * Uses the card's own tenant. Drives the money side of a lost/stolen-card revocation.
     */
    @Transactional
    public void freezeForVitoCard(String vitoCardNumber, String reason) {
        if (vitoCardNumber == null || vitoCardNumber.isBlank()) {
            return;
        }
        cardRepository.findByVitoCardNumber(vitoCardNumber).ifPresent(card -> {
            if ("BLOCKED".equals(card.getStatus())) {
                return; // idempotent — already frozen
            }
            blockCard(card.getCardId(), reason, card.getTenantId());
            log.info("Froze money card {} for revoked/suspended VITO card {}", card.getCardId(), vitoCardNumber);
        });
    }

    // ── Replace Card ────────────────────────────────────────────────────

    /**
     * Replace a card: blocks the old card (reason=REPLACED) and issues a new one
     * linked to the same wallet.
     *
     * @return the newly issued replacement card
     */
    @Transactional
    public CardEntity replaceCard(UUID oldCardId, UUID tenantId) {
        CardEntity oldCard = findCardOrThrow(oldCardId);

        // Block the old card
        oldCard.setStatus("REPLACED");
        oldCard.setBlockedAt(OffsetDateTime.now());
        oldCard.setBlockedReason("REPLACED");

        // Issue new card linked to the same wallet
        CardEntity newCard = issueCard(
                oldCard.getWalletId(),
                oldCard.getCardType(),
                oldCard.getCardNetwork(),
                tenantId
        );

        // Link old card to replacement
        oldCard.setReplacedBy(newCard.getCardId());
        cardRepository.save(oldCard);

        writeOutboxEvent(oldCard, "CARD_REPLACED", tenantId, Map.of(
                "oldCardId", oldCardId.toString(),
                "newCardId", newCard.getCardId().toString(),
                "walletId", oldCard.getWalletId().toString()
        ));

        log.info("Replaced card oldCardId={} with newCardId={}", oldCardId, newCard.getCardId());
        return newCard;
    }

    // ── Verify PIN ──────────────────────────────────────────────────────

    /**
     * Verify a card PIN. Decrements pin_tries_remaining on failure, blocks at 0.
     *
     * @return true if PIN matches
     */
    @Transactional
    public boolean verifyPin(UUID cardId, String pin, UUID tenantId) {
        CardEntity card = findCardOrThrow(cardId);

        if (!"ACTIVE".equals(card.getStatus())) {
            throw new IllegalStateException("Card must be ACTIVE to verify PIN; current=" + card.getStatus());
        }

        if (card.getPinHash() == null) {
            throw new IllegalStateException("Card has no PIN set");
        }

        if (passwordEncoder.matches(pin, card.getPinHash())) {
            // Successful verification — reset tries
            card.setPinTriesRemaining(DEFAULT_PIN_TRIES);
            cardRepository.save(card);
            return true;
        }

        // Failed verification
        int remaining = card.getPinTriesRemaining() - 1;
        card.setPinTriesRemaining(remaining);

        if (remaining <= 0) {
            card.setStatus("BLOCKED");
            card.setBlockedAt(OffsetDateTime.now());
            card.setBlockedReason("PIN_TRIES_EXCEEDED");
            cardRepository.save(card);

            writeOutboxEvent(card, "CARD_BLOCKED", tenantId, Map.of(
                    "cardId", cardId.toString(),
                    "walletId", card.getWalletId().toString(),
                    "reason", "PIN_TRIES_EXCEEDED"
            ));

            log.warn("Card blocked due to PIN tries exceeded cardId={}", cardId);
        } else {
            cardRepository.save(card);
        }

        return false;
    }

    // ── Change PIN ───────────────────────────────────────────────────────

    /**
     * Change the PIN on an ACTIVE card. Requires the current PIN to be verified first.
     *
     * @param cardId     the card to change the PIN for
     * @param currentPin the current PIN for verification
     * @param newPin     the new PIN to set
     * @param tenantId   the tenant context
     * @return the updated card entity
     */
    @Transactional
    public CardEntity changePin(UUID cardId, String currentPin, String newPin, UUID tenantId) {
        CardEntity card = findCardOrThrow(cardId);

        if (!"ACTIVE".equals(card.getStatus())) {
            throw new IllegalStateException("Card must be ACTIVE to change PIN; current=" + card.getStatus());
        }

        if (card.getPinHash() == null) {
            throw new IllegalStateException("Card has no PIN set; activate the card first");
        }

        if (!passwordEncoder.matches(currentPin, card.getPinHash())) {
            throw new IllegalArgumentException("Current PIN is incorrect");
        }

        card.setPinHash(passwordEncoder.encode(newPin));
        card.setPinTriesRemaining(DEFAULT_PIN_TRIES);
        card = cardRepository.save(card);

        writeOutboxEvent(card, "CARD_PIN_CHANGED", tenantId, Map.of(
                "cardId", cardId.toString(),
                "walletId", card.getWalletId().toString()
        ));

        log.info("PIN changed for cardId={}", cardId);
        return card;
    }

    // ── Reset PIN (Admin/Supervisor) ────────────────────────────────────

    /**
     * Reset the PIN on a card without requiring the current PIN.
     * This is an elevated-privilege operation intended for admin/supervisor use.
     *
     * @param cardId   the card to reset the PIN for
     * @param newPin   the new PIN to set
     * @param tenantId the tenant context
     * @return the updated card entity
     */
    @Transactional
    public CardEntity resetPin(UUID cardId, String newPin, UUID tenantId) {
        CardEntity card = findCardOrThrow(cardId);

        if ("REPLACED".equals(card.getStatus()) || "EXPIRED".equals(card.getStatus())) {
            throw new IllegalStateException("Cannot reset PIN on a " + card.getStatus() + " card");
        }

        card.setPinHash(passwordEncoder.encode(newPin));
        card.setPinTriesRemaining(DEFAULT_PIN_TRIES);

        // If the card was blocked due to PIN tries, restore it to ACTIVE
        if ("BLOCKED".equals(card.getStatus()) && "PIN_TRIES_EXCEEDED".equals(card.getBlockedReason())) {
            card.setStatus("ACTIVE");
            card.setBlockedAt(null);
            card.setBlockedReason(null);
        }

        card = cardRepository.save(card);

        writeOutboxEvent(card, "CARD_PIN_RESET", tenantId, Map.of(
                "cardId", cardId.toString(),
                "walletId", card.getWalletId().toString()
        ));

        log.info("PIN reset for cardId={}", cardId);
        return card;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Find a card by cardId or throw.
     */
    public CardEntity findCardOrThrow(UUID cardId) {
        return cardRepository.findByCardId(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
    }

    /**
     * Generate a unique 16-digit Luhn-valid card number.
     * Uses a 4-digit Impilo BIN prefix (6366) followed by 11 random digits
     * and a Luhn check digit. Retries until a unique number is produced.
     */
    String generateLuhnValidCardNumber() {
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder sb = new StringBuilder("6366"); // 4-digit BIN prefix
            // Generate 11 random digits (positions 5-15)
            for (int i = 0; i < 11; i++) {
                sb.append(secureRandom.nextInt(10));
            }
            // Compute Luhn check digit (position 16)
            sb.append(luhnCheckDigit(sb.toString()));
            String cardNumber = sb.toString();

            // Ensure uniqueness
            if (cardRepository.findByCardNumber(cardNumber).isEmpty()) {
                return cardNumber;
            }
            log.warn("Card number collision on attempt {}, retrying", attempt + 1);
        }
        throw new IllegalStateException("Failed to generate a unique card number after 100 attempts");
    }

    /**
     * Compute the Luhn check digit for a partial card number.
     */
    static int luhnCheckDigit(String partialNumber) {
        int sum = 0;
        boolean doubleDigit = true; // Start doubling from the rightmost digit of the partial
        for (int i = partialNumber.length() - 1; i >= 0; i--) {
            int digit = partialNumber.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return (10 - (sum % 10)) % 10;
    }

    /**
     * Mask a card number, showing only the last 4 digits.
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "*".repeat(cardNumber.length() - 4) + cardNumber.substring(cardNumber.length() - 4);
    }

    // ── Outbox Event Writing ────────────────────────────────────────────

    private void writeOutboxEvent(CardEntity card, String eventType, UUID tenantId, Map<String, Object> payload) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("CARD");
            outbox.setAggregateId(card.getCardId().toString());
            outbox.setEventType(eventType);
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(tenantId);
            outbox.setPodId("national-spine");
            outbox.setSubjectType("CARD");
            outbox.setSubjectId(card.getCardId().toString());
            outbox.setPartitionKey(card.getWalletId().toString());
            outbox.setIdempotencyKey(eventType + ":" + card.getCardId() + ":" + UUID.randomUUID());
            outbox.setOccurredAt(OffsetDateTime.now());
            outbox.setProducer("mushe-wallet-service");
            outbox.setSchemaVersion(1);

            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event for cardId={}: {}", card.getCardId(), e.getMessage(), e);
            throw new RuntimeException("Failed to write outbox event", e);
        }
    }
}
