package zw.gov.mohcc.impilo.wallet.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.wallet.persistence.entity.DepositIntentEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.FundingSourceEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.DepositIntentRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.FundingSourceRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.WalletRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Collection-intent truth: an external-source deposit NEVER credits the wallet
 * at request time — it creates a PENDING intent with a quotable reference code,
 * and only confirmation (provider callback / statement match) credits, exactly
 * once, idempotency-keyed per deposit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FundingServiceCollectionIntentTest {

    @Mock FundingSourceRepository sourceRepo;
    @Mock WalletRepository walletRepo;
    @Mock WalletService walletService;
    @Mock DepositIntentRepository depositRepo;

    FundingService service;

    private final UUID walletId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FundingService(sourceRepo, walletRepo, walletService, depositRepo);

        WalletEntity wallet = new WalletEntity();
        wallet.setWalletId(walletId);
        wallet.setTenantId(tenantId);
        wallet.setCurrency("USD");
        wallet.setStatus("ACTIVE");
        when(walletRepo.findByWalletId(walletId)).thenReturn(Optional.of(wallet));

        FundingSourceEntity source = new FundingSourceEntity();
        source.setSourceId(sourceId);
        source.setWalletId(walletId);
        source.setTenantId(tenantId);
        source.setSourceType("MOBILE_MONEY");
        source.setProvider("EcoCash");
        source.setStatus("ACTIVE");
        when(sourceRepo.findBySourceId(sourceId)).thenReturn(Optional.of(source));

        when(depositRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(depositRepo.findByReferenceCode(any())).thenReturn(Optional.empty());
    }

    @Test
    void requestDeposit_createsPendingIntent_neverCreditsWallet() {
        DepositIntentEntity intent = service.requestDepositFromSource(
                walletId, sourceId, new BigDecimal("50.00"), "citizen ref");

        assertEquals(DepositIntentEntity.STATUS_PENDING, intent.getStatus());
        assertEquals("MOBILE_MONEY", intent.getChannel());
        assertNotNull(intent.getReferenceCode());
        assertTrue(intent.getReferenceCode().startsWith("IMP-"));
        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void confirmDeposit_creditsExactlyOnce_withDeterministicIdempotencyKey() {
        DepositIntentEntity intent = pendingIntent();
        when(depositRepo.findByDepositId(intent.getDepositId())).thenReturn(Optional.of(intent));
        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(UUID.randomUUID());
        when(walletService.credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(txn);

        DepositIntentEntity confirmed = service.confirmDeposit(intent.getDepositId(), "ZIPIT-123", "recon");

        assertEquals(DepositIntentEntity.STATUS_CONFIRMED, confirmed.getStatus());
        assertEquals(txn.getTxnId(), confirmed.getCreditedTxnId());
        verify(walletService).credit(eq(walletId), eq(new BigDecimal("50.00")), eq("DEPOSIT"),
                any(), any(), any(), eq("ZIPIT-123"), any(), any(),
                eq("deposit:" + intent.getDepositId()));

        // Replay: already CONFIRMED → returns unchanged, no second credit.
        DepositIntentEntity replay = service.confirmDeposit(intent.getDepositId(), "ZIPIT-123", "recon");
        assertEquals(DepositIntentEntity.STATUS_CONFIRMED, replay.getStatus());
        verify(walletService, times(1)).credit(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void confirmByReferenceCode_matchesStatementLineEntryPoint() {
        DepositIntentEntity intent = pendingIntent();
        when(depositRepo.findByReferenceCode(intent.getReferenceCode()))
                .thenReturn(Optional.of(intent));
        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(UUID.randomUUID());
        when(walletService.credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(txn);

        DepositIntentEntity confirmed = service.confirmDepositByReference(
                intent.getReferenceCode(), "stmt-line-9", "statement-import");

        assertEquals(DepositIntentEntity.STATUS_CONFIRMED, confirmed.getStatus());
        assertEquals("stmt-line-9", confirmed.getExternalRef());
    }

    @Test
    void cancelledDeposit_cannotBeConfirmed() {
        DepositIntentEntity intent = pendingIntent();
        when(depositRepo.findByDepositId(intent.getDepositId())).thenReturn(Optional.of(intent));
        service.cancelDeposit(intent.getDepositId(), "citizen");

        assertThrows(IllegalStateException.class,
                () -> service.confirmDeposit(intent.getDepositId(), "x", "recon"));
        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void cashDeposit_staysImmediate_andLeavesConfirmedTrail() {
        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(UUID.randomUUID());
        when(walletService.credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(txn);

        service.depositCash(walletId, "cashier-1", UUID.randomUUID(),
                new BigDecimal("20.00"), "receipt-1");

        verify(walletService, times(1)).credit(any(), any(), eq("CASH_DEPOSIT"), any(), any(), any(),
                any(), any(), any(), any());
        verify(depositRepo).save(argThat(d ->
                DepositIntentEntity.STATUS_CONFIRMED.equals(d.getStatus())
                        && "CASH".equals(d.getChannel())));
    }

    private DepositIntentEntity pendingIntent() {
        DepositIntentEntity intent = service.requestDepositFromSource(
                walletId, sourceId, new BigDecimal("50.00"), "citizen ref");
        intent.setDepositId(UUID.randomUUID());
        return intent;
    }
}
