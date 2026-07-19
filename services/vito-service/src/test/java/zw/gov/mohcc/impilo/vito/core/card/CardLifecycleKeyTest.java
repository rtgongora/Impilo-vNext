package zw.gov.mohcc.impilo.vito.core.card;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.core.CardStatus;
import zw.gov.mohcc.impilo.vito.core.did.SovereignIdGenerator;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.SmartCardEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.CardStateTransitionRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.SmartCardRepository;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G032: a SMART card must never carry a fabricated public key, and must not be activated
 * (made usable) without a real, provisioned key.
 */
@ExtendWith(MockitoExtension.class)
class CardLifecycleKeyTest {

    @Mock private SmartCardRepository cardRepository;
    @Mock private CardStateTransitionRepository transitionRepository;
    @Mock private SovereignIdGenerator didGenerator;
    @Mock private EventOutboxRepository outboxRepository;

    private CardLifecycleService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID healthId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        VitoProperties props = new VitoProperties();
        props.getCard().setExpiryYears(5);
        service = new CardLifecycleService(
                cardRepository, transitionRepository, didGenerator, props, outboxRepository,
                new zw.gov.mohcc.impilo.shared.crypto.HmacService("card-lifecycle-key-test-pepper-32b-min"));
        lenient().when(didGenerator.generate(any())).thenReturn("did:impilo:test");
        lenient().when(cardRepository.save(any())).thenAnswer(i -> {
            SmartCardEntity c = i.getArgument(0);
            if (c.getId() == null) c.setId(1L); // DB assigns id on persist
            return c;
        });
        lenient().when(transitionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private SmartCardEntity printedCard(Long id, String publicKey) {
        SmartCardEntity card = new SmartCardEntity();
        card.setId(id);
        card.setTenantId(tenantId);
        card.setHealthId(healthId);
        card.setStatus(CardStatus.PRINTED);
        card.setPublicKey(publicKey);
        return card;
    }

    @Test
    void requestCard_emitsCanonicalCardCredentialEvent() {
        when(cardRepository.findByTenantIdAndHealthIdAndStatus(tenantId, healthId, CardStatus.ACTIVE))
                .thenReturn(Optional.empty());

        service.requestCard(tenantId, healthId, "PUBKEY", "clerk-1");

        ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(ev.capture());
        EventOutboxEntity e = ev.getValue();
        assertEquals("SMART_CARD", e.getAggregateType());
        assertEquals("CARD_REQUESTED", e.getEventType());
        // The canonical CardCredential projection (spine) — cardNumber + didUri + status for consumers.
        assertTrue(e.getPayload().contains("\"cardNumber\""), e.getPayload());
        assertTrue(e.getPayload().contains("\"didUri\""), e.getPayload());
        assertTrue(e.getPayload().contains("\"status\":\"REQUESTED\""), e.getPayload());
        assertTrue(e.getPayload().contains(healthId.toString()), e.getPayload());
    }

    @Test
    void recordReplacement_linksChainAndEmitsReplacedEvent() {
        SmartCardEntity newCard = printedCard(2L, "REAL-KEY");
        newCard.setCardNumber("CARD-NEW");
        newCard.setDidUri("did:impilo:new");
        when(cardRepository.findById(2L)).thenReturn(Optional.of(newCard));

        service.recordReplacement(tenantId, 2L, 1L);

        assertEquals(1L, newCard.getPreviousCardId());
        ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(ev.capture());
        assertEquals("CARD_REPLACED", ev.getValue().getEventType());
        assertTrue(ev.getValue().getPayload().contains("\"previousCardId\":1"), ev.getValue().getPayload());
    }

    @Test
    void requestCard_withoutKey_storesNullNotPlaceholder() {
        when(cardRepository.findByTenantIdAndHealthIdAndStatus(tenantId, healthId, CardStatus.ACTIVE))
                .thenReturn(Optional.empty());

        SmartCardEntity card = service.requestCard(tenantId, healthId, null, "actor-1");

        assertNull(card.getPublicKey(), "no key should be fabricated at request time");
        assertEquals(CardStatus.REQUESTED, card.getStatus());
    }

    @Test
    void markPrinted_provisionsTheRealKey() {
        SmartCardEntity requested = new SmartCardEntity();
        requested.setId(10L);
        requested.setTenantId(tenantId);
        requested.setHealthId(healthId);
        requested.setStatus(CardStatus.REQUESTED);
        when(cardRepository.findById(10L)).thenReturn(Optional.of(requested));

        SmartCardEntity printed = service.markPrinted(tenantId, 10L, "REAL-PEM-KEY-xyz");

        assertEquals("REAL-PEM-KEY-xyz", printed.getPublicKey());
        assertEquals(CardStatus.PRINTED, printed.getStatus());
    }

    @Test
    void activate_failsClosed_whenNoKey() {
        when(cardRepository.findById(20L)).thenReturn(Optional.of(printedCard(20L, null)));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.activate(tenantId, 20L));
        assertTrue(ex.getMessage().contains("no real public key"));
    }

    @Test
    void activate_failsClosed_onLegacyPlaceholderKey() {
        when(cardRepository.findById(21L))
                .thenReturn(Optional.of(printedCard(21L, "DEV-PLACEHOLDER-" + UUID.randomUUID())));
        assertThrows(IllegalStateException.class, () -> service.activate(tenantId, 21L));
    }

    @Test
    void activate_succeeds_withRealKey() {
        when(cardRepository.findById(22L)).thenReturn(Optional.of(printedCard(22L, "REAL-PEM-KEY-xyz")));
        SmartCardEntity active = service.activate(tenantId, 22L);
        assertEquals(CardStatus.ACTIVE, active.getStatus());
    }
}
