package zw.gov.mohcc.impilo.wallet.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.wallet.core.WalletService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the offline stored-value purse (Phase 3) works with a SOFTWARE P-256 key — exactly what a
 * VIRTUAL card (wallet pass on the phone) uses. The verification is identical to a physical secure
 * element, so no hardware is needed to build or test this.
 */
class OfflineTransactionServiceTest {

    private KeyPair cardKey;
    private String publicKeyPem;
    private CardRepository cardRepository;
    private WalletService walletService;
    private OfflineTransactionService service;
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        cardKey = kpg.generateKeyPair();
        publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(cardKey.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        cardRepository = mock(CardRepository.class);
        walletService = mock(WalletService.class);
        service = new OfflineTransactionService(cardRepository, walletService, new ObjectMapper());
        when(walletService.debit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new TransactionEntity());
    }

    private CardEntity linkedCard(long lastCounter) {
        CardEntity c = new CardEntity();
        c.setCardId(UUID.randomUUID());
        c.setTenantId(UUID.randomUUID());
        c.setWalletId(walletId);
        c.setVitoCardNumber("VITO-1");
        c.setVitoCardPublicKey(publicKeyPem);
        c.setStatus("ACTIVE");
        c.setLastOfflineCounter(lastCounter);
        return c;
    }

    private String sign(String payload) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(cardKey.getPrivate());
        s.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(s.sign());
    }

    @Test
    void valid_offline_txn_is_verified_and_reconciled_into_the_ledger() throws Exception {
        CardEntity card = linkedCard(0);
        when(cardRepository.findByVitoCardNumber("VITO-1")).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        String payload = "{\"amount\":\"10.00\",\"counter\":1,\"nonce\":\"n1\",\"currency\":\"USD\"}";

        service.redeem("VITO-1", payload, sign(payload));

        // Debited via the ledger with the nonce as the idempotency key; counter advanced.
        verify(walletService).debit(eq(walletId), eq(new BigDecimal("10.00")), eq("OFFLINE_PURSE"),
                eq("CARD_OFFLINE"), eq("n1"), any(), any(), any(), any(), eq("n1"));
        assertEquals(1L, card.getLastOfflineCounter());
    }

    @Test
    void tampered_payload_fails_signature_verification() throws Exception {
        CardEntity card = linkedCard(0);
        when(cardRepository.findByVitoCardNumber("VITO-1")).thenReturn(Optional.of(card));
        String signed = "{\"amount\":\"10.00\",\"counter\":1,\"nonce\":\"n1\"}";
        String tampered = "{\"amount\":\"9999.00\",\"counter\":1,\"nonce\":\"n1\"}";

        assertThrows(OfflineTransactionService.OfflineTransactionRejected.class,
                () -> service.redeem("VITO-1", tampered, sign(signed)));
        verify(walletService, never()).debit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void stale_or_replayed_counter_is_rejected() throws Exception {
        CardEntity card = linkedCard(5); // last accepted counter = 5
        when(cardRepository.findByVitoCardNumber("VITO-1")).thenReturn(Optional.of(card));
        String payload = "{\"amount\":\"1.00\",\"counter\":5,\"nonce\":\"n5\"}";

        assertThrows(OfflineTransactionService.OfflineTransactionRejected.class,
                () -> service.redeem("VITO-1", payload, sign(payload)));
        verify(walletService, never()).debit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unlinked_card_is_rejected() {
        when(cardRepository.findByVitoCardNumber("VITO-X")).thenReturn(Optional.empty());
        assertThrows(OfflineTransactionService.OfflineTransactionRejected.class,
                () -> service.redeem("VITO-X", "{\"counter\":1,\"nonce\":\"n\"}", "sig"));
    }

    @Test
    void blocked_card_is_rejected() throws Exception {
        CardEntity card = linkedCard(0);
        card.setStatus("BLOCKED");
        when(cardRepository.findByVitoCardNumber("VITO-1")).thenReturn(Optional.of(card));
        String payload = "{\"amount\":\"1.00\",\"counter\":1,\"nonce\":\"n1\"}";

        assertThrows(OfflineTransactionService.OfflineTransactionRejected.class,
                () -> service.redeem("VITO-1", payload, sign(payload)));
    }
}
