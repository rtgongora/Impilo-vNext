package zw.gov.mohcc.impilo.gl.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.gl.persistence.entity.AccountEntity;
import zw.gov.mohcc.impilo.gl.persistence.entity.FiscalPeriodEntity;
import zw.gov.mohcc.impilo.gl.persistence.entity.JournalEntryEntity;
import zw.gov.mohcc.impilo.gl.persistence.entity.JournalLineEntity;
import zw.gov.mohcc.impilo.gl.persistence.repository.FiscalPeriodRepository;
import zw.gov.mohcc.impilo.gl.persistence.repository.JournalEntryRepository;
import zw.gov.mohcc.impilo.gl.persistence.repository.JournalLineRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JournalService {

    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final FiscalPeriodService fiscalPeriodService;
    private final ChartOfAccountsService chartOfAccountsService;
    private final GlOutboxWriter glOutboxWriter;

    public JournalService(JournalEntryRepository journalEntryRepository,
                          JournalLineRepository journalLineRepository,
                          FiscalPeriodRepository fiscalPeriodRepository,
                          FiscalPeriodService fiscalPeriodService,
                          ChartOfAccountsService chartOfAccountsService,
                          GlOutboxWriter glOutboxWriter) {
        this.journalEntryRepository = journalEntryRepository;
        this.journalLineRepository = journalLineRepository;
        this.fiscalPeriodRepository = fiscalPeriodRepository;
        this.fiscalPeriodService = fiscalPeriodService;
        this.chartOfAccountsService = chartOfAccountsService;
        this.glOutboxWriter = glOutboxWriter;
    }

    public List<JournalEntryEntity> findByPeriod(UUID tenantId, UUID periodId) {
        return journalEntryRepository.findByTenantIdAndFiscalPeriodIdOrderByEntryDateDesc(tenantId, periodId);
    }

    @Transactional
    public JournalEntryEntity createDraft(UUID tenantId, LocalDate entryDate, UUID fiscalPeriodId,
                                        String description, String sourceModule, String sourceRef,
                                        List<LineDraft> lines) {
        JournalEntryEntity je = new JournalEntryEntity();
        je.setTenantId(tenantId);
        je.setEntryNumber(nextEntryNumber(tenantId));
        je.setEntryDate(entryDate);
        je.setFiscalPeriodId(fiscalPeriodId);
        je.setDescription(description);
        je.setSourceModule(sourceModule);
        je.setSourceRef(sourceRef);
        je.setStatus("DRAFT");
        for (LineDraft ld : lines) {
            JournalLineEntity jl = new JournalLineEntity();
            jl.setAccountId(ld.accountId());
            jl.setDebitAmount(ld.debit() != null ? ld.debit() : BigDecimal.ZERO);
            jl.setCreditAmount(ld.credit() != null ? ld.credit() : BigDecimal.ZERO);
            jl.setDescription(ld.lineDescription());
            jl.setFacilityId(ld.facilityId());
            je.addLine(jl);
        }
        validateBalanced(je);
        return journalEntryRepository.save(je);
    }

    @Transactional
    public JournalEntryEntity postEntry(UUID tenantId, UUID entryId, String postedBy) {
        JournalEntryEntity je = journalEntryRepository.findById(entryId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (!"DRAFT".equals(je.getStatus())) {
            throw new IllegalStateException("Only DRAFT entries can be posted");
        }
        FiscalPeriodEntity p = fiscalPeriodRepository.findById(je.getFiscalPeriodId())
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Fiscal period not found"));
        if (!"OPEN".equals(p.getStatus())) {
            throw new IllegalStateException("Fiscal period is not open for posting");
        }
        validateBalanced(je);
        je.setStatus("POSTED");
        je.setPostedBy(postedBy);
        je.setPostedAt(OffsetDateTime.now());
        JournalEntryEntity saved = journalEntryRepository.save(je);
        glOutboxWriter.publish(tenantId, "JOURNAL_ENTRY", saved.getEntryId().toString(), "journal", "posted",
                "gl:journal:posted:" + saved.getEntryId(),
                Map.of("entryId", saved.getEntryId().toString(), "entryNumber", saved.getEntryNumber()));
        return saved;
    }

    @Transactional
    public JournalEntryEntity reverseEntry(UUID tenantId, UUID entryId, String reversedBy) {
        JournalEntryEntity orig = journalEntryRepository.findById(entryId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (!"POSTED".equals(orig.getStatus())) {
            throw new IllegalStateException("Only POSTED entries can be reversed");
        }
        orig.setStatus("REVERSED");
        orig.setReversedBy(reversedBy);
        orig.setReversedAt(OffsetDateTime.now());
        journalEntryRepository.save(orig);

        JournalEntryEntity rev = new JournalEntryEntity();
        rev.setTenantId(tenantId);
        rev.setEntryNumber(nextEntryNumber(tenantId));
        rev.setEntryDate(LocalDate.now());
        rev.setFiscalPeriodId(orig.getFiscalPeriodId());
        rev.setDescription("Reversal of " + orig.getEntryNumber());
        rev.setSourceModule(orig.getSourceModule());
        rev.setSourceRef("REV:" + orig.getEntryId());
        rev.setStatus("DRAFT");
        for (JournalLineEntity ol : orig.getLines()) {
            JournalLineEntity nl = new JournalLineEntity();
            nl.setAccountId(ol.getAccountId());
            nl.setDebitAmount(ol.getCreditAmount());
            nl.setCreditAmount(ol.getDebitAmount());
            nl.setDescription("Reversal");
            rev.addLine(nl);
        }
        rev = journalEntryRepository.save(rev);
        return postEntry(tenantId, rev.getEntryId(), reversedBy);
    }

    /**
     * Idempotent auto-posting for integration events (COSTA, MUSHEX, payroll, procurement).
     */
    @Transactional
    public JournalEntryEntity createPostedAutoEntry(UUID tenantId, LocalDate entryDate, String module,
                                                    String sourceRef, String description,
                                                    List<AutoLine> lines) {
        chartOfAccountsService.seedDefaultHealthFacilityChart(tenantId);
        if (journalEntryRepository.findByTenantIdAndSourceModuleAndSourceRef(tenantId, module, sourceRef)
                .isPresent()) {
            return journalEntryRepository.findByTenantIdAndSourceModuleAndSourceRef(tenantId, module, sourceRef).get();
        }
        FiscalPeriodEntity period = fiscalPeriodService.ensurePeriodForDate(tenantId, entryDate);
        if (!"OPEN".equals(period.getStatus())) {
            throw new IllegalStateException("Cannot auto-post into closed period");
        }
        JournalEntryEntity je = new JournalEntryEntity();
        je.setTenantId(tenantId);
        je.setEntryNumber("AUTO-" + UUID.randomUUID().toString().substring(0, 8));
        je.setEntryDate(entryDate);
        je.setFiscalPeriodId(period.getPeriodId());
        je.setDescription(description);
        je.setSourceModule(module);
        je.setSourceRef(sourceRef);
        je.setStatus("DRAFT");
        for (AutoLine al : lines) {
            AccountEntity acc = chartOfAccountsService.findByCode(tenantId, al.accountCode())
                    .orElseThrow(() -> new IllegalStateException("Unknown account code: " + al.accountCode()));
            JournalLineEntity jl = new JournalLineEntity();
            jl.setAccountId(acc.getAccountId());
            jl.setDebitAmount(al.debit() != null ? al.debit() : BigDecimal.ZERO);
            jl.setCreditAmount(al.credit() != null ? al.credit() : BigDecimal.ZERO);
            jl.setFacilityId(al.facilityId());
            je.addLine(jl);
        }
        validateBalanced(je);
        journalEntryRepository.save(je);
        return postEntry(tenantId, je.getEntryId(), "integration");
    }

    public BigDecimal sumNetIncomeForPeriod(UUID tenantId, UUID periodId) {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        List<JournalLineRepository.AccountAggregate> aggs =
                journalLineRepository.aggregateByAccount(tenantId, periodId);
        for (JournalLineRepository.AccountAggregate a : aggs) {
            AccountEntity acc = chartOfAccountsService.listAccounts(tenantId).stream()
                    .filter(x -> x.getAccountId().equals(a.getAccountId()))
                    .findFirst()
                    .orElse(null);
            if (acc == null) {
                continue;
            }
            BigDecimal net = a.getTotalDebit().subtract(a.getTotalCredit());
            if ("REVENUE".equals(acc.getAccountType())) {
                revenue = revenue.add(net.negate());
            } else if ("EXPENSE".equals(acc.getAccountType())) {
                expense = expense.add(net);
            }
        }
        return revenue.subtract(expense);
    }

    private static void validateBalanced(JournalEntryEntity je) {
        BigDecimal d = BigDecimal.ZERO;
        BigDecimal c = BigDecimal.ZERO;
        List<JournalLineEntity> list = je.getLines() != null ? je.getLines() : new ArrayList<>();
        for (JournalLineEntity jl : list) {
            d = d.add(jl.getDebitAmount() != null ? jl.getDebitAmount() : BigDecimal.ZERO);
            c = c.add(jl.getCreditAmount() != null ? jl.getCreditAmount() : BigDecimal.ZERO);
        }
        if (d.compareTo(c) != 0) {
            throw new IllegalArgumentException("Journal is not balanced: debits=" + d + " credits=" + c);
        }
    }

    private String nextEntryNumber(UUID tenantId) {
        return "JE-" + System.currentTimeMillis() + "-" + tenantId.toString().substring(0, 4);
    }

    public record LineDraft(UUID accountId, BigDecimal debit, BigDecimal credit, String lineDescription,
                            UUID facilityId) {
    }

    public record AutoLine(String accountCode, BigDecimal debit, BigDecimal credit, UUID facilityId) {
    }
}
