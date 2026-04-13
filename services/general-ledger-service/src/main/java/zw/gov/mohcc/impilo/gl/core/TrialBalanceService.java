package zw.gov.mohcc.impilo.gl.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.gl.persistence.entity.AccountEntity;
import zw.gov.mohcc.impilo.gl.persistence.entity.TrialBalanceSnapshotEntity;
import zw.gov.mohcc.impilo.gl.persistence.repository.AccountRepository;
import zw.gov.mohcc.impilo.gl.persistence.repository.JournalLineRepository;
import zw.gov.mohcc.impilo.gl.persistence.repository.TrialBalanceSnapshotRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TrialBalanceService {

    private final JournalLineRepository journalLineRepository;
    private final AccountRepository accountRepository;
    private final TrialBalanceSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public TrialBalanceService(JournalLineRepository journalLineRepository,
                               AccountRepository accountRepository,
                               TrialBalanceSnapshotRepository snapshotRepository,
                               ObjectMapper objectMapper) {
        this.journalLineRepository = journalLineRepository;
        this.accountRepository = accountRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> trialBalance(UUID tenantId, UUID fiscalPeriodId) {
        Map<UUID, AccountEntity> accounts = new HashMap<>();
        accountRepository.findByTenantIdOrderByAccountCodeAsc(tenantId)
                .forEach(a -> accounts.put(a.getAccountId(), a));
        List<JournalLineRepository.AccountAggregate> aggs =
                journalLineRepository.aggregateByAccount(tenantId, fiscalPeriodId);
        ArrayNode rows = objectMapper.createArrayNode();
        BigDecimal td = BigDecimal.ZERO;
        BigDecimal tc = BigDecimal.ZERO;
        for (JournalLineRepository.AccountAggregate a : aggs) {
            AccountEntity acc = accounts.get(a.getAccountId());
            ObjectNode row = objectMapper.createObjectNode();
            row.put("accountId", a.getAccountId().toString());
            if (acc != null) {
                row.put("accountCode", acc.getAccountCode());
                row.put("name", acc.getName());
                row.put("accountType", acc.getAccountType());
            }
            row.put("debit", a.getTotalDebit().doubleValue());
            row.put("credit", a.getTotalCredit().doubleValue());
            rows.add(row);
            td = td.add(a.getTotalDebit());
            tc = tc.add(a.getTotalCredit());
        }
        return Map.of("tenantId", tenantId.toString(), "fiscalPeriodId", fiscalPeriodId.toString(),
                "totalDebit", td, "totalCredit", tc, "lines", rows);
    }

    @Transactional
    public TrialBalanceSnapshotEntity snapshotTrialBalance(UUID tenantId, UUID fiscalPeriodId) {
        Map<String, Object> tb = trialBalance(tenantId, fiscalPeriodId);
        TrialBalanceSnapshotEntity s = new TrialBalanceSnapshotEntity();
        s.setTenantId(tenantId);
        s.setFiscalPeriodId(fiscalPeriodId);
        try {
            s.setSnapshotJson(objectMapper.writeValueAsString(tb));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return snapshotRepository.save(s);
    }

    public Map<String, Object> incomeStatement(UUID tenantId, UUID fiscalPeriodId) {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        List<JournalLineRepository.AccountAggregate> aggs =
                journalLineRepository.aggregateByAccount(tenantId, fiscalPeriodId);
        Map<UUID, String> types = new HashMap<>();
        accountRepository.findByTenantIdOrderByAccountCodeAsc(tenantId)
                .forEach(a -> types.put(a.getAccountId(), a.getAccountType()));
        for (JournalLineRepository.AccountAggregate row : aggs) {
            String t = types.get(row.getAccountId());
            if ("REVENUE".equals(t)) {
                revenue = revenue.add(row.getTotalCredit().subtract(row.getTotalDebit()));
            } else if ("EXPENSE".equals(t)) {
                expense = expense.add(row.getTotalDebit().subtract(row.getTotalCredit()));
            }
        }
        return Map.of("revenue", revenue, "expense", expense, "netIncome", revenue.subtract(expense));
    }

    public List<TrialBalanceSnapshotEntity> listSnapshots(UUID tenantId, UUID fiscalPeriodId) {
        return snapshotRepository.findByTenantIdAndFiscalPeriodIdOrderByGeneratedAtDesc(tenantId, fiscalPeriodId);
    }

    public Map<String, Object> balanceSheet(UUID tenantId, UUID fiscalPeriodId) {
        List<JournalLineRepository.AccountAggregate> aggs =
                journalLineRepository.aggregateByAccount(tenantId, fiscalPeriodId);
        Map<UUID, AccountEntity> byId = new HashMap<>();
        accountRepository.findByTenantIdOrderByAccountCodeAsc(tenantId).forEach(a -> byId.put(a.getAccountId(), a));
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        for (JournalLineRepository.AccountAggregate row : aggs) {
            AccountEntity a = byId.get(row.getAccountId());
            if (a == null) {
                continue;
            }
            BigDecimal bal = switch (a.getAccountType()) {
                case "ASSET" -> row.getTotalDebit().subtract(row.getTotalCredit());
                case "LIABILITY" -> row.getTotalCredit().subtract(row.getTotalDebit());
                case "EQUITY", "CONTRA" -> row.getTotalCredit().subtract(row.getTotalDebit());
                default -> BigDecimal.ZERO;
            };
            switch (a.getAccountType()) {
                case "ASSET" -> assets = assets.add(bal);
                case "LIABILITY" -> liabilities = liabilities.add(bal);
                case "EQUITY", "CONTRA" -> equity = equity.add(bal);
                default -> {
                }
            }
        }
        return Map.of("assets", assets, "liabilities", liabilities, "equity", equity);
    }
}
