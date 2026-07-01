package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetActualReferenceEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetLineEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ActualSource;
import zw.gov.mohcc.impilo.costa.domain.repository.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Posts references to actual expenditure against budget lines. This never
 * creates payment/accounting truth — it records a pointer to the owner's record
 * (MusheX payment, COSTA invoice, imported ERP doc) plus the amount. Idempotent
 * on (tenant, source, sourceReference). When a commitment is supplied, the
 * matching commitment is liquidated so committed→actual conversion nets to zero
 * on available balance.
 */
@Service
public class BudgetActualService {

    private final BudgetActualReferenceRepository actualRepository;
    private final BudgetLineRepository lineRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetAllocationRepository allocationRepository;
    private final FundingSourceRepository fundingSourceRepository;
    private final BudgetCommitmentService commitmentService;
    private final BudgetEventEmitter emitter;

    public BudgetActualService(BudgetActualReferenceRepository actualRepository,
                               BudgetLineRepository lineRepository,
                               BudgetRepository budgetRepository,
                               BudgetAllocationRepository allocationRepository,
                               FundingSourceRepository fundingSourceRepository,
                               BudgetCommitmentService commitmentService,
                               BudgetEventEmitter emitter) {
        this.actualRepository = actualRepository;
        this.lineRepository = lineRepository;
        this.budgetRepository = budgetRepository;
        this.allocationRepository = allocationRepository;
        this.fundingSourceRepository = fundingSourceRepository;
        this.commitmentService = commitmentService;
        this.emitter = emitter;
    }

    public record NewActual(UUID budgetLineId, String source, String sourceReference,
                            UUID commitmentId, BigDecimal actualAmount, String ingestSource) {}

    @Transactional
    public BudgetActualReferenceEntity post(UUID tenantId, UUID budgetId, NewActual cmd) {
        BudgetEntity b = budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        BudgetLineEntity line = lineRepository.findByLineIdAndTenantId(cmd.budgetLineId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget line not found"));
        if (!line.getBudgetId().equals(budgetId)) {
            throw new IllegalArgumentException("Budget line does not belong to this budget");
        }
        ActualSource.valueOf(cmd.source()); // validate
        if (cmd.sourceReference() == null || cmd.sourceReference().isBlank()) {
            throw new IllegalArgumentException("sourceReference is required");
        }
        BigDecimal amount = cmd.actualAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("actualAmount must be positive");
        }

        // idempotent
        var existing = actualRepository.findByTenantIdAndSourceAndSourceReference(
                tenantId, cmd.source(), cmd.sourceReference());
        if (existing.isPresent()) {
            return existing.get();
        }

        BudgetActualReferenceEntity a = new BudgetActualReferenceEntity();
        a.setBudgetLineId(cmd.budgetLineId());
        a.setBudgetId(budgetId);
        a.setTenantId(tenantId);
        a.setSource(cmd.source());
        a.setSourceReference(cmd.sourceReference());
        a.setCommitmentId(cmd.commitmentId());
        a.setActualAmount(amount);
        a.setIngestSource(cmd.ingestSource() != null ? cmd.ingestSource() : "API");
        actualRepository.save(a);

        // liquidate the linked commitment (moves committed down); then add spent
        if (cmd.commitmentId() != null) {
            commitmentService.liquidate(tenantId, cmd.commitmentId(), amount);
        }
        addSpentToProjection(tenantId, line, amount);
        absorbFunding(tenantId, line.getFundingSourceId(), amount);
        emitter.emit("BUDGET_ACTUAL", a.getActualRefId().toString(), "BUDGET_ACTUAL_POSTED", tenantId, payload(a));
        return a;
    }

    /** Accrue actual expenditure against the line's funding source absorption. */
    private void absorbFunding(UUID tenantId, UUID fundingSourceId, BigDecimal amount) {
        if (fundingSourceId == null) return;
        fundingSourceRepository.findByFundingSourceIdAndTenantId(fundingSourceId, tenantId).ifPresent(fs -> {
            BigDecimal absorbed = fs.getAbsorbedAmount() != null ? fs.getAbsorbedAmount() : BigDecimal.ZERO;
            fs.setAbsorbedAmount(absorbed.add(amount));
            fundingSourceRepository.save(fs);
        });
    }

    public List<BudgetActualReferenceEntity> list(UUID tenantId, UUID budgetId) {
        return actualRepository.findByBudgetIdAndTenantIdOrderByPostedAtDesc(budgetId, tenantId);
    }

    private void addSpentToProjection(UUID tenantId, BudgetLineEntity line, BigDecimal amount) {
        if (line.getAllocationId() == null) return;
        allocationRepository.findByAllocationIdAndTenantId(line.getAllocationId(), tenantId).ifPresent(alloc -> {
            BigDecimal spent = alloc.getSpentAmount() != null ? alloc.getSpentAmount() : BigDecimal.ZERO;
            alloc.setSpentAmount(spent.add(amount));
            alloc.recomputeAvailable();
            allocationRepository.save(alloc);
        });
    }

    private Map<String, Object> payload(BudgetActualReferenceEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("actualRefId", a.getActualRefId().toString());
        m.put("budgetId", a.getBudgetId().toString());
        m.put("budgetLineId", a.getBudgetLineId().toString());
        m.put("tenantId", a.getTenantId().toString());
        m.put("source", a.getSource());
        m.put("sourceReference", a.getSourceReference());
        m.put("actualAmount", a.getActualAmount());
        if (a.getCommitmentId() != null) m.put("commitmentId", a.getCommitmentId().toString());
        return m;
    }
}
