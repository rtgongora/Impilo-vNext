package zw.gov.mohcc.impilo.vito.core.card;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.vito.core.CardStatus;
import zw.gov.mohcc.impilo.vito.core.qr.QrSigningService;
import zw.gov.mohcc.impilo.vito.persistence.entity.SmartCardEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.SmartCardRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The card resolve seam turns each presentation form into {healthId, cpid,
 * cardStatus}, and never fabricates identity for an unknown/unverifiable one.
 */
@ExtendWith(MockitoExtension.class)
class CardResolveServiceTest {

    @Mock SmartCardRepository cardRepository;
    @Mock QrSigningService qrSigningService;
    @Mock CardAssertionVerifier assertionVerifier;

    HmacService hmacService = new HmacService("card-resolve-test-pepper-32-bytes-min");
    CardResolveService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID healthId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CardResolveService(cardRepository, qrSigningService, assertionVerifier, hmacService);
    }

    private SmartCardEntity activeCard() {
        SmartCardEntity c = new SmartCardEntity();
        c.setTenantId(tenantId);
        c.setHealthId(healthId);
        c.setCardNumber("CARD-0001");
        c.setStatus(CardStatus.ACTIVE);
        c.setCardForm("PHYSICAL");
        c.setCardPointer(hmacService.computeLookupHash(healthId.toString()));
        return c;
    }

    @Test
    @DisplayName("card number → resolves with cpid == healthId")
    void byCardNumber() {
        when(cardRepository.findByTenantIdAndCardNumber(tenantId, "CARD-0001"))
                .thenReturn(Optional.of(activeCard()));

        var res = service.resolve(tenantId, "CARD-0001", null, null);

        assertTrue(res.isPresent());
        assertEquals(healthId, res.get().healthId());
        assertEquals(healthId.toString(), res.get().cpid());
        assertEquals("ACTIVE", res.get().cardStatus());
        assertEquals(CardResolveService.ResolvedVia.CARD_NUMBER, res.get().resolvedVia());
    }

    @Test
    @DisplayName("vito QR → verified, pointer dereferenced to card")
    void byVitoQr() {
        String pointer = hmacService.computeLookupHash(healthId.toString());
        when(qrSigningService.verify("qr-jwt")).thenReturn(Optional.of(
                new QrSigningService.QrClaims("jti", tenantId.toString(), "HEALTH_ID", pointer, 0, 0)));
        when(cardRepository.findByTenantIdAndCardPointerOrderByCreatedAtDesc(tenantId, pointer))
                .thenReturn(List.of(activeCard()));

        var res = service.resolve(tenantId, null, "qr-jwt", null);

        assertTrue(res.isPresent());
        assertEquals(healthId, res.get().healthId());
        assertEquals(CardResolveService.ResolvedVia.VITO_QR, res.get().resolvedVia());
    }

    @Test
    @DisplayName("invalid QR → unresolved (no fabrication)")
    void invalidQr() {
        when(qrSigningService.verify("bad")).thenReturn(Optional.empty());
        assertTrue(service.resolve(tenantId, null, "bad", null).isEmpty());
    }

    @Test
    @DisplayName("printed assertion → verified subject mapped to card")
    void byPrintedAssertion() {
        when(assertionVerifier.verify("assertion")).thenReturn(Optional.of(
                new CardAssertionVerifier.VerifiedAssertion("CLIENT", healthId.toString(), "job-1")));
        String pointer = hmacService.computeLookupHash(healthId.toString());
        when(cardRepository.findByTenantIdAndCardPointerOrderByCreatedAtDesc(tenantId, pointer))
                .thenReturn(List.of(activeCard()));

        var res = service.resolve(tenantId, null, null, "assertion");

        assertTrue(res.isPresent());
        assertEquals(healthId, res.get().healthId());
        assertEquals(CardResolveService.ResolvedVia.PRINTED_ASSERTION, res.get().resolvedVia());
    }

    @Test
    @DisplayName("assertion subjectId not a UUID → unresolved")
    void assertionNonUuid() {
        when(assertionVerifier.verify("assertion")).thenReturn(Optional.of(
                new CardAssertionVerifier.VerifiedAssertion("CLIENT", "not-a-uuid", "job-1")));
        assertTrue(service.resolve(tenantId, null, null, "assertion").isEmpty());
    }

    @Test
    @DisplayName("empty presentation → unresolved")
    void emptyPresentation() {
        assertTrue(service.resolve(tenantId, null, null, null).isEmpty());
        assertTrue(service.resolve(tenantId, "", "", "").isEmpty());
    }

    @Test
    @DisplayName("unknown card number → unresolved")
    void unknownCardNumber() {
        when(cardRepository.findByTenantIdAndCardNumber(eq(tenantId), any()))
                .thenReturn(Optional.empty());
        assertTrue(service.resolve(tenantId, "NOPE", null, null).isEmpty());
    }
}
