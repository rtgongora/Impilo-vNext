package zw.gov.mohcc.impilo.vito.core.card;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.vito.core.qr.QrSigningService;
import zw.gov.mohcc.impilo.vito.persistence.entity.SmartCardEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.SmartCardRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * B0 — the ONE card resolve seam. Turns any card presentation into
 * {@code {healthId, cpid, cardStatus}}, unifying the two previously disconnected
 * QR signers so every downstream consumer (identity / pay / check-in / PHR) routes
 * a resolved presentation through a single governed path.
 *
 * <p>Three presentation forms, all real:</p>
 * <ol>
 *   <li><b>card number</b> — direct O(1) lookup.</li>
 *   <li><b>vito QR (JWT)</b> — {@link QrSigningService} verifies signature+expiry;
 *       its pointer (HMAC of health_id) dereferences to the card via the indexed
 *       {@code card_pointer}. This is the portal my-QR / wallet / emergency QR and
 *       the digital card's identity function.</li>
 *   <li><b>printed-card assertion</b> — {@link CardAssertionVerifier} verifies
 *       card-print's Ed25519 signature; the subject health_id maps to the card.</li>
 * </ol>
 *
 * <p>cpid == health_id (the CPID/health_id collapse), so cpid is the health_id
 * string. A resolution never fabricates identity: an unverifiable or unknown
 * presentation returns empty.</p>
 */
@Service
public class CardResolveService {

    private static final Logger log = LoggerFactory.getLogger(CardResolveService.class);

    private final SmartCardRepository cardRepository;
    private final QrSigningService qrSigningService;
    private final CardAssertionVerifier assertionVerifier;
    private final HmacService hmacService;

    public CardResolveService(SmartCardRepository cardRepository,
                              QrSigningService qrSigningService,
                              CardAssertionVerifier assertionVerifier,
                              HmacService hmacService) {
        this.cardRepository = cardRepository;
        this.qrSigningService = qrSigningService;
        this.assertionVerifier = assertionVerifier;
        this.hmacService = hmacService;
    }

    /** How a presentation was resolved (for audit / consumer trust decisions). */
    public enum ResolvedVia { CARD_NUMBER, VITO_QR, PRINTED_ASSERTION }

    public record CardResolution(
            UUID healthId,
            String cpid,
            String cardNumber,
            String cardStatus,
            String cardForm,
            ResolvedVia resolvedVia) {}

    /**
     * Resolve a presentation. Exactly one of {@code cardNumber} / {@code qrToken}
     * / {@code cardAssertion} is expected; the first that resolves wins.
     */
    @Transactional(readOnly = true)
    public Optional<CardResolution> resolve(UUID tenantId, String cardNumber,
                                            String qrToken, String cardAssertion) {
        if (cardNumber != null && !cardNumber.isBlank()) {
            return cardRepository.findByTenantIdAndCardNumber(tenantId, cardNumber.strip())
                    .map(card -> toResolution(card, ResolvedVia.CARD_NUMBER));
        }
        if (qrToken != null && !qrToken.isBlank()) {
            return resolveVitoQr(tenantId, qrToken.strip());
        }
        if (cardAssertion != null && !cardAssertion.isBlank()) {
            return resolvePrintedAssertion(tenantId, cardAssertion);
        }
        return Optional.empty();
    }

    private Optional<CardResolution> resolveVitoQr(UUID tenantId, String qrToken) {
        return qrSigningService.verify(qrToken)
                .flatMap(claims -> {
                    String pointer = claims.pointer();
                    if (pointer == null || pointer.isBlank()) {
                        return Optional.empty();
                    }
                    return firstActiveOrLatest(
                            cardRepository.findByTenantIdAndCardPointerOrderByCreatedAtDesc(tenantId, pointer))
                            .map(card -> toResolution(card, ResolvedVia.VITO_QR));
                });
    }

    private Optional<CardResolution> resolvePrintedAssertion(UUID tenantId, String cardAssertion) {
        return assertionVerifier.verify(cardAssertion)
                .flatMap(a -> {
                    // The client card's subjectId is the health_id (== cpid). Map it
                    // to the card via the same indexed pointer used everywhere else.
                    UUID healthId = parseUuid(a.subjectId());
                    if (healthId == null) {
                        log.warn("Card assertion subjectId is not a health_id UUID: {}", a.subjectType());
                        return Optional.empty();
                    }
                    String pointer = hmacService.computeLookupHash(healthId.toString());
                    return firstActiveOrLatest(
                            cardRepository.findByTenantIdAndCardPointerOrderByCreatedAtDesc(tenantId, pointer))
                            .map(card -> toResolution(card, ResolvedVia.PRINTED_ASSERTION));
                });
    }

    /** Prefer the ACTIVE card; otherwise the most recent (already ordered desc). */
    private Optional<SmartCardEntity> firstActiveOrLatest(List<SmartCardEntity> cards) {
        if (cards == null || cards.isEmpty()) {
            return Optional.empty();
        }
        return cards.stream()
                .filter(c -> "ACTIVE".equals(String.valueOf(c.getStatus())))
                .findFirst()
                .or(() -> Optional.of(cards.get(0)));
    }

    private CardResolution toResolution(SmartCardEntity card, ResolvedVia via) {
        String healthIdStr = card.getHealthId().toString();
        return new CardResolution(
                card.getHealthId(),
                healthIdStr, // cpid == health_id
                card.getCardNumber(),
                String.valueOf(card.getStatus()),
                card.getCardForm(),
                via);
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
