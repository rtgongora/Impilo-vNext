package zw.gov.mohcc.impilo.gl.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.gl.persistence.entity.AccountEntity;
import zw.gov.mohcc.impilo.gl.persistence.repository.AccountRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChartOfAccountsService {

    public static final Map<String, SeedAccount> DEFAULT_CHART = new LinkedHashMap<>();

    static {
        put("1010", "CASH", "ASSET", "DEBIT", null);
        put("1020", "BANK", "ASSET", "DEBIT", null);
        put("1200", "TRADE_RECEIVABLES", "ASSET", "DEBIT", null);
        put("1300", "INVENTORY", "ASSET", "DEBIT", null);
        put("1400", "MEDICAL_EQUIPMENT", "ASSET", "DEBIT", null);
        put("2000", "TRADE_PAYABLES", "LIABILITY", "CREDIT", null);
        put("2100", "ACCRUALS", "LIABILITY", "CREDIT", null);
        put("2200", "LOANS", "LIABILITY", "CREDIT", null);
        put("2300", "DEDUCTIONS_PAYABLE", "LIABILITY", "CREDIT", null);
        put("3000", "RETAINED_EARNINGS", "EQUITY", "CREDIT", null);
        put("3100", "RESERVES", "EQUITY", "CREDIT", null);
        put("4010", "REVENUE_OPD", "REVENUE", "CREDIT", null);
        put("4020", "REVENUE_IPD", "REVENUE", "CREDIT", null);
        put("4030", "REVENUE_PHARMACY", "REVENUE", "CREDIT", null);
        put("4040", "REVENUE_LAB", "REVENUE", "CREDIT", null);
        put("5010", "EXPENSE_SALARIES", "EXPENSE", "DEBIT", null);
        put("5020", "EXPENSE_SUPPLIES", "EXPENSE", "DEBIT", null);
        put("5030", "EXPENSE_UTILITIES", "EXPENSE", "DEBIT", null);
        put("5040", "EXPENSE_MAINTENANCE", "EXPENSE", "DEBIT", null);
    }

    private static void put(String code, String name, String type, String normal, String parent) {
        DEFAULT_CHART.put(code, new SeedAccount(code, name, type, normal, parent));
    }

    private final AccountRepository accountRepository;

    public ChartOfAccountsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<AccountEntity> listAccounts(UUID tenantId) {
        return accountRepository.findByTenantIdOrderByAccountCodeAsc(tenantId);
    }

    public Optional<AccountEntity> findByCode(UUID tenantId, String code) {
        return accountRepository.findByTenantIdAndAccountCode(tenantId, code);
    }

    @Transactional
    public AccountEntity createAccount(UUID tenantId, String code, String name, String accountType,
                                       String normalBalance, UUID parentAccountId) {
        validateHierarchy(tenantId, parentAccountId, null);
        AccountEntity a = new AccountEntity();
        a.setTenantId(tenantId);
        a.setAccountCode(code);
        a.setName(name);
        a.setAccountType(accountType);
        a.setNormalBalance(normalBalance);
        a.setParentAccountId(parentAccountId);
        a.setActive(true);
        a.setSystem(false);
        return accountRepository.save(a);
    }

    @Transactional
    public AccountEntity updateAccount(UUID tenantId, UUID accountId, String name, Boolean active, UUID parentId) {
        AccountEntity a = accountRepository.findById(accountId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        validateHierarchy(tenantId, parentId, accountId);
        if (name != null) {
            a.setName(name);
        }
        if (active != null) {
            a.setActive(active);
        }
        if (parentId != null) {
            a.setParentAccountId(parentId);
        }
        return accountRepository.save(a);
    }

    @Transactional
    public int seedDefaultHealthFacilityChart(UUID tenantId) {
        if (accountRepository.findByTenantIdOrderByAccountCodeAsc(tenantId).stream()
                .anyMatch(AccountEntity::isSystem)) {
            return 0;
        }
        List<AccountEntity> saved = new ArrayList<>();
        for (SeedAccount s : DEFAULT_CHART.values()) {
            AccountEntity a = new AccountEntity();
            a.setTenantId(tenantId);
            a.setAccountCode(s.code());
            a.setName(s.name());
            a.setAccountType(s.accountType());
            a.setNormalBalance(s.normalBalance());
            a.setSystem(true);
            a.setActive(true);
            if (s.parentCode() != null) {
                accountRepository.findByTenantIdAndAccountCode(tenantId, s.parentCode())
                        .ifPresent(p -> a.setParentAccountId(p.getAccountId()));
            }
            saved.add(accountRepository.save(a));
        }
        return saved.size();
    }

    private void validateHierarchy(UUID tenantId, UUID parentId, UUID selfId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new IllegalArgumentException("Account cannot be its own parent");
        }
        AccountEntity parent = accountRepository.findById(parentId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Parent account not found"));
        if (!parent.isActive()) {
            throw new IllegalArgumentException("Parent account is inactive");
        }
    }

    public record SeedAccount(String code, String name, String accountType, String normalBalance, String parentCode) {
    }
}
