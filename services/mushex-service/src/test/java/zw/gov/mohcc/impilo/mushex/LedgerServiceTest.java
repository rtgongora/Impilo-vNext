package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LedgerService}.
 *
 * Validates account provisioning (6 default accounts), payment posting
 * with correct debit/credit mappings, refund posting with reversed
 * accounts, settlement posting, and entry amount correctness.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock private LedgerAccountRepository accountRepository;
    @Mock private LedgerEntryRepository entryRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;

    private LedgerService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        service = new LedgerService(accountRepository, entryRepository, outboxRepository, objectMapper);
        TrustContextHolder.set(new TrustContext(
            tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
            "device-1", UUID.randomUUID(), facilityId, null, null, AccessMode.INTERNAL
        ));
        // Default: objectMapper returns empty JSON
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
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

        service.ensureAccounts(tenantId);

        ArgumentCaptor<LedgerAccountEntity> captor = ArgumentCaptor.forClass(LedgerAccountEntity.class);
        verify(accountRepository, times(6)).save(captor.capture());

        List<LedgerAccountEntity> accounts = captor.getAllValues();
        assertEquals(6, accounts.size());

        // Verify the standard account names exist
        List<String> names = accounts.stream()
            .map(LedgerAccountEntity::getName)
            .toList();
        assertTrue(names.contains("PATIENT_CASH"));
        assertTrue(names.contains("FACILITY_REVENUE"));
        assertTrue(names.contains("PLATFORM_FEES"));
        assertTrue(names.contains("INSURER_RECEIVABLE"));
        assertTrue(names.contains("REFUNDS_PAYABLE"));
        assertTrue(names.contains("SETTLEMENT_PAYABLE"));

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
        existingCash.setName("PATIENT_CASH");
        existingCash.setTenantId(tenantId);

        when(accountRepository.findByTenantIdAndName(eq(tenantId), eq("PATIENT_CASH")))
            .thenReturn(Optional.of(existingCash));
        when(accountRepository.findByTenantIdAndName(eq(tenantId), argThat(n -> !"PATIENT_CASH".equals(n))))
            .thenReturn(Optional.empty());
        when(accountRepository.save(any(LedgerAccountEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.ensureAccounts(tenantId);

        // Only 5 saves because PATIENT_CASH already existed
        verify(accountRepository, times(5)).save(any(LedgerAccountEntity.class));
    }

    @Test
    void ensureAccounts_accountTypes_areCorrect() {
        when(accountRepository.findByTenantIdAndName(eq(tenantId), anyString()))
            .thenReturn(Optional.empty());
        when(accountRepository.save(any(LedgerAccountEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.ensureAccounts(tenantId);

        ArgumentCaptor<LedgerAccountEntity> captor = ArgumentCaptor.forClass(LedgerAccountEntity.class);
        verify(accountRepository, times(6)).save(captor.capture());

        for (LedgerAccountEntity acct : captor.getAllValues()) {
            switch (acct.getName()) {
                case "PATIENT_CASH" -> assertEquals(AccountType.ASSET, acct.getAccountType());
                case "FACILITY_REVENUE" -> assertEquals(AccountType.INCOME, acct.getAccountType());
                case "PLATFORM_FEES" -> assertEquals(AccountType.INCOME, acct.getAccountType());
                case "INSURER_RECEIVABLE" -> assertEquals(AccountType.ASSET, acct.getAccountType());
                case "REFUNDS_PAYABLE" -> assertEquals(AccountType.LIABILITY, acct.getAccountType());
                case "SETTLEMENT_PAYABLE" -> assertEquals(AccountType.LIABILITY, acct.getAccountType());
                default -> fail("Unexpected account name: " + acct.getName());
            }
        }
    }

    // ---------------------------------------------------------------
    // postPayment
    // ---------------------------------------------------------------

    @Test
    void postPayment_createsEntryWithCorrectDebitCreditAccounts() {
        setupAccountMocks();
        when(entryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LedgerEntryEntity entry = service.postPayment(tenantId, "INT-500", new BigDecimal("250.00"), "USD");

        assertNotNull(entry.getEntryId());
        assertEquals(tenantId, entry.getTenantId());
        assertEquals("INT-500", entry.getIntentId());
        assertEquals("ACCT-PATIENT-CASH", entry.getDebitAccount());
        assertEquals("ACCT-FACILITY-REVENUE", entry.getCreditAccount());
        assertEquals(new BigDecimal("250.00"), entry.getAmount());
        assertEquals("USD", entry.getCurrency());
        assertEquals("PAYMENT", entry.getReferenceType());
        assertEquals("INT-500", entry.getReferenceId());
    }

    @Test
    void postPayment_entriesHaveCorrectAmounts() {
        setupAccountMocks();
        when(entryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal amount = new BigDecimal("1234.56");
        LedgerEntryEntity entry = service.postPayment(tenantId, "INT-501", amount, "USD");

        assertEquals(amount, entry.getAmount());
    }

    // ---------------------------------------------------------------
    // postRefund
    // ---------------------------------------------------------------

    @Test
    void postRefund_createsEntryWithReversedAccounts() {
        setupAccountMocks();
        when(entryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LedgerEntryEntity entry = service.postRefund(tenantId, "INT-600", new BigDecimal("75.00"), "USD");

        // Refund: debit REFUNDS_PAYABLE, credit PATIENT_CASH
        assertEquals("ACCT-REFUNDS-PAYABLE", entry.getDebitAccount());
        assertEquals("ACCT-PATIENT-CASH", entry.getCreditAccount());
        assertEquals(new BigDecimal("75.00"), entry.getAmount());
        assertEquals("USD", entry.getCurrency());
        assertEquals("REFUND", entry.getReferenceType());
        assertEquals("INT-600", entry.getReferenceId());
        assertEquals("INT-600", entry.getIntentId());
    }

    @Test
    void postRefund_entriesHaveCorrectAmounts() {
        setupAccountMocks();
        when(entryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal refundAmount = new BigDecimal("999.99");
        LedgerEntryEntity entry = service.postRefund(tenantId, "INT-601", refundAmount, "USD");

        assertEquals(refundAmount, entry.getAmount());
    }

    // ---------------------------------------------------------------
    // postSettlement
    // ---------------------------------------------------------------

    @Test
    void postSettlement_createsEntryWithCorrectAccounts() {
        setupAccountMocks();
        when(entryRepository.save(any(LedgerEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LedgerEntryEntity entry = service.postSettlement(tenantId, "SETTLE-001", new BigDecimal("5000.00"), "USD");

        assertEquals("ACCT-SETTLEMENT-PAYABLE", entry.getDebitAccount());
        assertEquals("ACCT-FACILITY-REVENUE", entry.getCreditAccount());
        assertEquals(new BigDecimal("5000.00"), entry.getAmount());
        assertEquals("SETTLEMENT", entry.getReferenceType());
        assertEquals("SETTLE-001", entry.getReferenceId());
        assertNull(entry.getIntentId()); // settlement entries have no intent
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Test
    void postPayment_missingAccount_throws() {
        // ensureAccounts will try to find and create, but then resolveAccount will fail
        // if somehow the account still doesn't exist after creation
        when(accountRepository.findByTenantIdAndName(eq(tenantId), anyString()))
            .thenReturn(Optional.empty());
        when(accountRepository.save(any(LedgerAccountEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        // After saving, findByTenantIdAndName still returns empty (simulating error)
        // Since ensureAccounts creates accounts but resolveAccount uses findByTenantIdAndName
        // which returns empty, it will throw

        assertThrows(IllegalStateException.class,
            () -> service.postPayment(tenantId, "INT-700", new BigDecimal("100.00"), "USD"));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void setupAccountMocks() {
        // For ensureAccounts: all accounts already exist
        when(accountRepository.findByTenantIdAndName(eq(tenantId), eq("PATIENT_CASH")))
            .thenReturn(Optional.of(buildAccount("ACCT-PATIENT-CASH", "PATIENT_CASH", AccountType.ASSET)));
        when(accountRepository.findByTenantIdAndName(eq(tenantId), eq("FACILITY_REVENUE")))
            .thenReturn(Optional.of(buildAccount("ACCT-FACILITY-REVENUE", "FACILITY_REVENUE", AccountType.INCOME)));
        when(accountRepository.findByTenantIdAndName(eq(tenantId), eq("PLATFORM_FEES")))
            .thenReturn(Optional.of(buildAccount("ACCT-PLATFORM-FEES", "PLATFORM_FEES", AccountType.INCOME)));
        when(accountRepository.findByTenantIdAndName(eq(tenantId), eq("INSURER_RECEIVABLE")))
            .thenReturn(Optional.of(buildAccount("ACCT-INSURER-RECEIVABLE", "INSURER_RECEIVABLE", AccountType.ASSET)));
        when(accountRepository.findByTenantIdAndName(eq(tenantId), eq("REFUNDS_PAYABLE")))
            .thenReturn(Optional.of(buildAccount("ACCT-REFUNDS-PAYABLE", "REFUNDS_PAYABLE", AccountType.LIABILITY)));
        when(accountRepository.findByTenantIdAndName(eq(tenantId), eq("SETTLEMENT_PAYABLE")))
            .thenReturn(Optional.of(buildAccount("ACCT-SETTLEMENT-PAYABLE", "SETTLEMENT_PAYABLE", AccountType.LIABILITY)));
    }

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
