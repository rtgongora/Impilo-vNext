package zw.gov.mohcc.impilo.gl.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.gl.persistence.entity.BudgetLineEntity;
import zw.gov.mohcc.impilo.gl.persistence.repository.BudgetLineRepository;
import zw.gov.mohcc.impilo.gl.persistence.repository.JournalLineRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class BudgetService {

    private final BudgetLineRepository budgetLineRepository;
    private final JournalLineRepository journalLineRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    public BudgetService(BudgetLineRepository budgetLineRepository,
                         JournalLineRepository journalLineRepository,
                         ChartOfAccountsService chartOfAccountsService) {
        this.budgetLineRepository = budgetLineRepository;
        this.journalLineRepository = journalLineRepository;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    public List<BudgetLineEntity> list(UUID tenantId, int fiscalYear) {
        return budgetLineRepository.findByTenantIdAndFiscalYearOrderByAccountId(tenantId, fiscalYear);
    }

    @Transactional
    public BudgetLineEntity upsert(UUID tenantId, UUID accountId, int fiscalYear, BigDecimal budgetAmount) {
        return budgetLineRepository.findByTenantIdAndAccountIdAndFiscalYear(tenantId, accountId, fiscalYear)
                .map(b -> {
                    b.setBudgetAmount(budgetAmount);
                    return budgetLineRepository.save(b);
                })
                .orElseGet(() -> {
                    BudgetLineEntity b = new BudgetLineEntity();
                    b.setTenantId(tenantId);
                    b.setAccountId(accountId);
                    b.setFiscalYear(fiscalYear);
                    b.setBudgetAmount(budgetAmount);
                    return budgetLineRepository.save(b);
                });
    }

    @Transactional
    public void refreshActuals(UUID tenantId, int fiscalYear, UUID fiscalPeriodId) {
        chartOfAccountsService.listAccounts(tenantId);
        List<JournalLineRepository.AccountAggregate> aggs =
                journalLineRepository.aggregateByAccount(tenantId, fiscalPeriodId);
        for (JournalLineRepository.AccountAggregate a : aggs) {
            budgetLineRepository.findByTenantIdAndAccountIdAndFiscalYear(tenantId, a.getAccountId(), fiscalYear)
                    .ifPresent(b -> {
                        BigDecimal movement = a.getTotalDebit().subtract(a.getTotalCredit()).abs();
                        b.setActualAmount(b.getActualAmount().add(movement));
                        budgetLineRepository.save(b);
                    });
        }
    }
}
