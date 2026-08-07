package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zw.gov.mohcc.impilo.mushex.domain.entity.WalletAccountEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.WalletAccountRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.WalletTransactionRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

/**
 * Custodial wallet credit/debit mint or burn balance, so they are restricted to
 * platform finance actor types. A citizen/provider actor reaching the endpoint
 * (even mesh-trusted) must be refused; unset actor types stay permissive per the
 * estate idiom (ext_authz already gated the call).
 */
class WalletPlatformServiceAuthzTest {

    private final UUID tenantId = UUID.randomUUID();
    private final WalletAccountRepository walletRepo = Mockito.mock(WalletAccountRepository.class);
    private final WalletTransactionRepository txnRepo = Mockito.mock(WalletTransactionRepository.class);
    private final EventOutboxRepository outboxRepo = Mockito.mock(EventOutboxRepository.class);
    private final WalletPlatformService service =
            new WalletPlatformService(walletRepo, txnRepo, outboxRepo, new ObjectMapper());

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void actAs(String actorType) {
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", actorType, "FINANCE",
                "device-1", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    private void stubWallet() {
        WalletAccountEntity w = new WalletAccountEntity();
        w.setWalletId("WLT1");
        w.setTenantId(tenantId);
        w.setCurrency("USD");
        w.setAvailableBalance(BigDecimal.TEN);
        w.setLedgerBalance(BigDecimal.TEN);
        when(walletRepo.findById("WLT1")).thenReturn(Optional.of(w));
        when(walletRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txnRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void patientActor_cannotCreditWallet() {
        actAs("CITIZEN");
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.credit("WLT1", BigDecimal.ONE, "ADJUSTMENT", "ref"));
        assertTrue(ex.getMessage().contains("CITIZEN"));
    }

    @Test
    void providerActor_cannotDebitWallet() {
        actAs("PROVIDER");
        assertThrows(ResponseStatusException.class,
                () -> service.debit("WLT1", BigDecimal.ONE, "ADJUSTMENT", "ref"));
    }

    /**
     * Was {@code actAs("FACILITY_FINANCE")}, which is not an actor type any client emits — so this
     * test asserted that a value no real request can carry is admitted, while the real finance
     * operator was refused. OPERATOR is what the admin consoles present.
     */
    @Test
    void financeActor_canCreditWallet() {
        actAs("OPERATOR");
        stubWallet();
        assertDoesNotThrow(() -> service.credit("WLT1", BigDecimal.ONE, "ADJUSTMENT", "ref"));
    }

    /**
     * This test was named {@code unsetActorType_staysPermissive_estateIdiom} and asserted that a
     * caller presenting no actor type could move custodial money. The "estate idiom" it cited was
     * "ext_authz already gated the call" — but Envoy routes only to experience-bff and has no route
     * to mushex, so nothing had gated it. Combined with a role set whose only emittable member was
     * SYSTEM, omitting the header was the sole way a human could credit or debit a custodial
     * wallet. The behaviour is now inverted, and so is this test.
     */
    @Test
    void unsetActorType_isNowRefusedRatherThanPermitted() {
        actAs(null);
        assertThrows(ResponseStatusException.class,
                () -> service.credit("WLT1", BigDecimal.ONE, "ADJUSTMENT", "ref"));
    }
}
