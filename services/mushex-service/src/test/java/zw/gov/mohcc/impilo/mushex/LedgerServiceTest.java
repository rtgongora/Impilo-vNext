package zw.gov.mohcc.impilo.mushex;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.mushex.domain.entity.LedgerAccountEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.LedgerEntryEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AccountType;
import zw.gov.mohcc.impilo.mushex.domain.repository.LedgerAccountRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.LedgerEntryRepository;
import zw.gov.mohcc.impilo.mushex.service.LedgerService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LedgerService}.
 *
 * Validates account provisioning (6 default accounts), payment posting
 * with correct debit/credit mappings, refund posting with reversed
 * accounts, and entry amount correctness.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock private LedgerAccountRepository accountRepository;
    @Mock private LedgerEntryRepository entryRepository;

    private LedgerService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LedgerService(accountRepository, entryRepository);
        TrustContextHolder.set(new TrustContext(
            tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
            "device-1", UUID.randomUUID(), facilityId, null, null, AccessMode.INTERNAL
        ));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ---------------------------------------------------------------
    // ensureAccounts
    // ---------------------------------------------------------------

    @Test
    void ensureAccounts_creates6DefaultAccounts() {
        when(accountRepository.findByTenantIdAndName(eq(tenantId), anyString()))
            .thenReturn(Optional.empty());
        when(accountRepository.save(any(LedgerAccountEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.ensureAccounts(tenantId, "USD");

        ArgumentCaptor<LedgerAccountEntity> captor = ArgumentCaptor.forClass(LedgerAccountEntity.class);
        verify(accountRepository, times(6)).save(captor.capture());

        List<LedgerAccountEntity> accounts = captor.getAllValues();
        assertEquals(6, accounts.size());

        // Verify the standard account names exist
        List<String> names = accounts.stream()
            .map(LedgerAccountEntity::getName)
            .toList();
        assertTrue(names.contains("CASH_RECEIVABLE"));
        assertTrue(names.contains("REVENUE"));
        assertTrue(names.contains("REFUND_PAYABLE"));
        assertTrue(names.contains("CLAIM_RECEIVABLE"));
        assertTrue(names.contains("SETTLEMENT_PAYABLE"));
        assertTrue(names.contains("SUSPENSE"));

        // Verify all accounts belong to the correct tenant
        accounts.forEach(acct -> {
            assertEquals(tenantId, acct.getTenantId());
            assertEquals("USD", acct.getCurrency());
            assertNotNull(acct.getAccountId());
            assertNotNull(acct.getAccountType());
        });
    }

    @Test
    void ensureAccounts_skipsExistingAccounts() {
        LedgerAccountEntity existingCash = new LedgerAccountEntity();
        existingCash.setAccountId("EXISTING-CASH");
        existingCash.setName("CASH_RECEIVABLE");
        existingCash.setTenantId(tenantId);

        when(accountRepository.findByTenantIdAndName(eq(tenantId), eq("CASH_RECEIVABLE")))
            .thenReturn(Optional.of(existingCash));
        when(accountRepository.findByTenantIdAndName(eq(tenantId), argThat(n -> !"CASH_RECEIVABLE".equals(n))))
            .thenReturn(Optional.empty());
        when(accountRepository.save(any(LedgerAccountEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.ensureAccounts(tenantId, "USD");

        // Only 5 saves because CASH_RECEIVABLE already existed
        verify(accountRepository, times(5)).save(any(LedgerAccountEntity.class));
    }

    @Test
    void ensureAccounts_accountTypes_areCorrect() {
        when(accountRepository.findByTenantIdAndName(eq(tenantId), anyString()))
            .thenReturn(Optional.empty());
        when(accountRepository.save(any(LedgerAccountEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.ensureAccounts(tenantId, "USD");

        ArgumentCaptor<LedgerAccountEntity> captor = ArgumentCaptor.forClass(LedgerAccountEntity.class);
        verify(accountRepository, times(6)).save(captor.capture());

        for (LedgerAccountEntity acct : captor.getAllValues()) {
            switch (acct.getName()) {
                case "CASH_RECEIVABLE" -> assertEquals(AccountType.ASSET, acct.getAccountType());
                case "REVENUE" -> assertEquals(AccountType.INCOME, acct.getAccountType());
                case "REFUND_PAYABLE" -> assertEquals(AccountType.LIABILITY, acct.getAccountType());
                case "CLAIM_RECEIVABLE" -> assertEquals(AccountType.ASSET, acct.getAccountType());
                case "SETTLEMENT_PAYABLE" -> assertEquals(AccountType.LIABILITY, acct.getAccountType());
                case "SUSPENSE" -> assertEquals(AccountType.LIABILITY, acct.getAccountType());
                default -> fail("Unexpected account name: " + acct.getName());
            }
        }
    }

    // ---------------------------------------------------------------
    // postPayment
    // ---------------------------------------------------------------

    @Test
    void postPayment_createsEntryWithCorrectDebitCreditAccounts() {
        LedgerAccountEntity cashAccount = buildAccount("ACCT-CASH", "CASH_RECEIVABLE", AccountType.ASSET);
        LedgerAccountEntity revenueAccount = buildAccount("ACCT-REVENUE", "REVENUE", AccountType.INCOME);

        when(accountRepository.findByTenantIdAndName(tenantId, "CASH_RECEIVABLE"))
            .thenReturn(Optional.of(cashAccount));
        when(accountRepository.findByTenantIdAndName(tenantId, "REVENUE"))
            .thenReturn(Optional.of(revenueAccount));
        when(entryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.postPayment(tenantId, "INT-500", new BigDecimal("250.00"), "USD");

        ArgumentCaptor<LedgerEntryEntity> captor = ArgumentCaptor.forClass(LedgerEntryEntity.class);
        verify(entryRepository).save(captor.capture());

        LedgerEntryEntity entry = captor.getValue();
        assertNotNull(entry.getEntryId());
        assertEquals(tenantId, entry.getTenantId());
        assertEquals("INT-500", entry.getIntentId());
        assertEquals("ACCT-CASH", entry.getDebitAccount());
        assertEquals("ACCT-REVENUE", entry.getCreditAccount());
        assertEquals(new BigDecimal("250.00"), entry.getAmount());
        assertEquals("USD", entry.getCurrency());
        assertEquals("PAYMENT", entry.getReferenceType());
    }

    @Test
    void postPayment_entriesHaveCorrectAmounts() {
        LedgerAccountEntity cashAccount = buildAccount("ACCT-CASH", "CASH_RECEIVABLE", AccountType.ASSET);
        LedgerAccountEntity revenueAccount = buildAccount("ACCT-REVENUE", "REVENUE", AccountType.INCOME);

        when(accountRepository.findByTenantIdAndName(tenantId, "CASH_RECEIVABLE"))
            .thenReturn(Optional.of(cashAccount));
        when(accountRepository.findByTenantIdAndName(tenantId, "REVENUE"))
            .thenReturn(Optional.of(revenueAccount));
        when(entryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal amount = new BigDecimal("1234.56");
        service.postPayment(tenantId, "INT-501", amount, "USD");

        ArgumentCaptor<LedgerEntryEntity> captor = ArgumentCaptor.forClass(LedgerEntryEntity.class);
        verify(entryRepository).save(captor.capture());
        assertEquals(amount, captor.getValue().getAmount());
    }

    @Test
    void postPayment_setsReferenceIdToIntentId() {
        LedgerAccountEntity cashAccount = buildAccount("ACCT-CASH", "CASH_RECEIVABLE", AccountType.ASSET);
        LedgerAccountEntity revenueAccount = buildAccount("ACCT-REVENUE", "REVENUE", AccountType.INCOME);

        when(accountRepository.findByTenantIdAndName(tenantId, "CASH_RECEIVABLE"))
            .thenReturn(Optional.of(cashAccount));
        when(accountRepository.findByTenantIdAndName(tenantId, "REVENUE"))
            .thenReturn(Optional.of(revenueAccount));
        when(entryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.postPayment(tenantId, "INT-502", new BigDecimal("50.00"), "USD");

        ArgumentCaptor<LedgerEntryEntity> captor = ArgumentCaptor.forClass(LedgerEntryEntity.class);
        verify(entryRepository).save(captor.capture());
        assertEquals("INT-502", captor.getValue().getReferenceId());
    }

    // ---------------------------------------------------------------
    // postRefund
    // ---------------------------------------------------------------

    @Test
    void postRefund_createsEntryWithReversedAccounts() {
        LedgerAccountEntity cashAccount = buildAccount("ACCT-CASH", "CASH_RECEIVABLE", AccountType.ASSET);
        LedgerAccountEntity refundAccount = buildAccount("ACCT-REFUND", "REFUND_PAYABLE", AccountType.LIABILITY);

        when(accountRepository.findByTenantIdAndName(tenantId, "CASH_RECEIVABLE"))
            .thenReturn(Optional.of(cashAccount));
        when(accountRepository.findByTenantIdAndName(tenantId, "REFUND_PAYABLE"))
            .thenReturn(Optional.of(refundAccount));
        when(entryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.postRefund(tenantId, "INT-600", "REF-001", new BigDecimal("75.00"), "USD");

        ArgumentCaptor<LedgerEntryEntity> captor = ArgumentCaptor.forClass(LedgerEntryEntity.class);
        verify(entryRepository).save(captor.capture());

        LedgerEntryEntity entry = captor.getValue();
        // Refund reverses: debit refund_payable, credit cash_receivable
        assertEquals("ACCT-REFUND", entry.getDebitAccount());
        assertEquals("ACCT-CASH", entry.getCreditAccount());
        assertEquals(new BigDecimal("75.00"), entry.getAmount());
        assertEquals("USD", entry.getCurrency());
        assertEquals("REFUND", entry.getReferenceType());
        assertEquals("REF-001", entry.getReferenceId());
        assertEquals("INT-600", entry.getIntentId());
    }

    @Test
    void postRefund_entriesHaveCorrectAmounts() {
        LedgerAccountEntity cashAccount = buildAccount("ACCT-CASH", "CASH_RECEIVABLE", AccountType.ASSET);
        LedgerAccountEntity refundAccount = buildAccount("ACCT-REFUND", "REFUND_PAYABLE", AccountType.LIABILITY);

        when(accountRepository.findByTenantIdAndName(tenantId, "CASH_RECEIVABLE"))
            .thenReturn(Optional.of(cashAccount));
        when(accountRepository.findByTenantIdAndName(tenantId, "REFUND_PAYABLE"))
            .thenReturn(Optional.of(refundAccount));
        when(entryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal refundAmount = new BigDecimal("999.99");
        service.postRefund(tenantId, "INT-601", "REF-002", refundAmount, "USD");

        ArgumentCaptor<LedgerEntryEntity> captor = ArgumentCaptor.forClass(LedgerEntryEntity.class);
        verify(entryRepository).save(captor.capture());
        assertEquals(refundAmount, captor.getValue().getAmount());
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Test
    void postPayment_missingAccount_throws() {
        when(accountRepository.findByTenantIdAndName(tenantId, "CASH_RECEIVABLE"))
            .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
            () -> service.postPayment(tenantId, "INT-700", new BigDecimal("100.00"), "USD"));
    }

    @Test
    void postRefund_missingAccount_throws() {
        when(accountRepository.findByTenantIdAndName(tenantId, "CASH_RECEIVABLE"))
            .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
            () -> service.postRefund(tenantId, "INT-701", "REF-010", new BigDecimal("50.00"), "USD"));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private LedgerAccountEntity buildAccount(String accountId, String name, AccountType type) {
        LedgerAccountEntity account = new LedgerAccountEntity();
        account.setAccountId(accountId);
        account.setTenantId(tenantId);
        account.setName(name);
        account.setAccountType(type);
        account.setCurrency("USD");
        return account;
    }
}
