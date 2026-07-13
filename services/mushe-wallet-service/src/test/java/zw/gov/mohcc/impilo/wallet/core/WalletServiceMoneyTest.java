package zw.gov.mohcc.impilo.wallet.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.TransactionRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.WalletRepository;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * The wallet's core money invariants — the behaviors the wallet-pay rig relies
 * on: balance arithmetic, insufficient-balance refusal, idempotent replay
 * (never double-move), and inactive-wallet refusal.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceMoneyTest {

    @Mock WalletRepository walletRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock EventOutboxRepository outboxRepository;

    WalletService service;
    private final UUID walletId = UUID.randomUUID();
    private WalletEntity wallet;

    @BeforeEach
    void setUp() {
        service = new WalletService(walletRepository, transactionRepository,
                outboxRepository, new ObjectMapper());
        wallet = new WalletEntity();
        wallet.setWalletId(walletId);
        wallet.setTenantId(UUID.randomUUID());
        wallet.setOwnerType("INDIVIDUAL");
        wallet.setOwnerRef("CPID-1");
        wallet.setCurrency("USD");
        wallet.setStatus("ACTIVE");
        wallet.setBalance(new BigDecimal("100.00"));
        wallet.setAvailableBalance(new BigDecimal("100.00"));
        when(walletRepository.findByWalletId(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            TransactionEntity t = inv.getArgument(0);
            if (t.getTxnId() == null) {
                t.setTxnId(UUID.randomUUID());
            }
            return t;
        });
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactionRepository.sumDebitsByDate(any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumDebitsByMonth(any(), anyInt(), anyInt())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void credit_increasesBothBalances_andRecordsCompletedTxn() {
        TransactionEntity txn = service.credit(walletId, new BigDecimal("20.00"),
                "CASH_DEPOSIT", "CASHIER", "ref", "desc", null, null, null, null);

        assertEquals(new BigDecimal("120.00"), wallet.getBalance());
        assertEquals(new BigDecimal("120.00"), wallet.getAvailableBalance());
        assertEquals("CREDIT", txn.getDirection());
        assertEquals("COMPLETED", txn.getStatus());
        assertEquals(new BigDecimal("120.00"), txn.getBalanceAfter());
    }

    @Test
    void debit_decreasesBalances_exactAmount() {
        service.debit(walletId, new BigDecimal("43.14"),
                "PAYMENT", "MUSHEX", "intent-1", "order", null, null, null, "k1");

        assertEquals(new BigDecimal("56.86"), wallet.getBalance());
        assertEquals(new BigDecimal("56.86"), wallet.getAvailableBalance());
    }

    @Test
    void debit_insufficientBalance_refusesAndMovesNothing() {
        assertThrows(IllegalStateException.class, () -> service.debit(walletId,
                new BigDecimal("100.01"), "PAYMENT", "MUSHEX", "intent-2", "order",
                null, null, null, "k2"));
        assertEquals(new BigDecimal("100.00"), wallet.getBalance());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void debit_idempotentReplay_returnsExistingTxn_neverDoubleDebits() {
        TransactionEntity existing = new TransactionEntity();
        existing.setDirection("DEBIT");
        existing.setStatus("COMPLETED");
        when(transactionRepository.findByIdempotencyKey("mushex:wallet-pay:intent-3"))
                .thenReturn(Optional.of(existing));

        TransactionEntity replayed = service.debit(walletId, new BigDecimal("43.14"),
                "PAYMENT", "MUSHEX", "intent-3", "order", null, null, null,
                "mushex:wallet-pay:intent-3");

        assertSame(existing, replayed);
        assertEquals(new BigDecimal("100.00"), wallet.getBalance(), "balance must not move on replay");
    }

    @Test
    void debit_inactiveWallet_refused() {
        wallet.setStatus("FROZEN");
        assertThrows(IllegalStateException.class, () -> service.debit(walletId,
                BigDecimal.ONE, "PAYMENT", "MUSHEX", "intent-4", "order", null, null, null, "k4"));
    }

    @Test
    void credit_unknownWallet_refused() {
        UUID other = UUID.randomUUID();
        when(walletRepository.findByWalletId(other)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.credit(other,
                BigDecimal.ONE, "CASH_DEPOSIT", "CASHIER", "r", "d", null, null, null, null));
    }
}
