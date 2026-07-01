package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetAllocationEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetCommitmentEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetLineEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.CommitmentOwnerSystem;
import zw.gov.mohcc.impilo.costa.domain.enums.CommitmentStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records and liquidates budget commitments. A commitment is a reference to an
 * owner-system obligation (Oros PO, Dura requisition, …) reserved against a
 * budget line. Maintains the committed/liquidated amounts and keeps the flat
 * availability projection in sync. Truth stays with the owner system.
 */
@Service
public class BudgetCommitmentService {

    private final BudgetCommitmentRepository commitmentRepository;
    private final BudgetLineRepository lineRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetAllocationRepository allocationRepository;
    private final BudgetEventEmitter emitter;

    public BudgetCommitmentService(BudgetCommitmentRepository commitmentRepository,
                                   BudgetLineRepository lineRepository,
                                   BudgetRepository budgetRepository,
                                   BudgetAllocationRepository allocationRepository,
                                   BudgetEventEmitter emitter) {
        this.commitmentRepository = commitmentRepository;
        this.lineRepository = lineRepository;
        this.budgetRepository = budgetRepository;
        this.allocationRepository = allocationRepository;
        this.emitter = emitter;
    }

    public record NewCommitment(UUID budgetLineId, String ownerSystem, String ownerReference,
                                String description, BigDecimal committedAmount,
                                BigDecimal obligatedAmount, OffsetDateTime expiresAt) {}

    @Transactional
    public BudgetCommitmentEntity record(UUID tenantId, UUID budgetId, NewCommitment cmd) {
        BudgetEntity b = budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        if (!BudgetStatus.ACTIVE.name().equals(b.getStatus())) {
            throw new IllegalStateException("Commitments require an ACTIVE budget");
        }
        BudgetLineEntity line = lineRepository.findByLineIdAndTenantId(cmd.budgetLineId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget line not found"));
        if (!line.getBudgetId().equals(budgetId)) {
            throw new IllegalArgumentException("Budget line does not belong to this budget");
        }
        CommitmentOwnerSystem.valueOf(cmd.ownerSystem()); // validate
        if (cmd.ownerReference() == null || cmd.ownerReference().isBlank()) {
            throw new IllegalArgumentException("ownerReference is required");
        }
        BigDecimal committed = cmd.committedAmount();
        if (committed == null || committed.signum() <= 0) {
            throw new IllegalArgumentException("committedAmount must be positive");
        }
        BigDecimal obligated = cmd.obligatedAmount() != null ? cmd.obligatedAmount() : BigDecimal.ZERO;
        if (obligated.compareTo(committed) > 0) {
            throw new IllegalArgumentException("obligated must not exceed committed");
        }

        // idempotent on (owner_system, owner_reference)
        var existing = commitmentRepository.findByTenantIdAndOwnerSystemAndOwnerReference(
                tenantId, cmd.ownerSystem(), cmd.ownerReference());
        if (existing.isPresent()) {
            return existing.get();
        }

        BudgetCommitmentEntity c = new BudgetCommitmentEntity();
        c.setBudgetLineId(cmd.budgetLineId());
        c.setBudgetId(budgetId);
        c.setTenantId(tenantId);
        c.setOwnerSystem(cmd.ownerSystem());
        c.setOwnerReference(cmd.ownerReference());
        c.setDescription(cmd.description());
        c.setCommittedAmount(committed);
        c.setObligatedAmount(obligated);
        c.setLiquidatedAmount(BigDecimal.ZERO);
        c.setStatus(CommitmentStatus.COMMITTED.name());
        c.setExpiresAt(cmd.expiresAt());
        commitmentRepository.save(c);

        adjustProjection(tenantId, line, committed, BigDecimal.ZERO);
        emitter.emit("BUDGET_COMMITMENT", c.getCommitmentId().toString(), "BUDGET_COMMITMENT_RECORDED",
                tenantId, payload(c));
        return c;
    }

    @Transactional
    public BudgetCommitmentEntity liquidate(UUID tenantId, UUID commitmentId, BigDecimal amount) {
        BudgetCommitmentEntity c = require(tenantId, commitmentId);
        if (CommitmentStatus.CANCELLED.name().equals(c.getStatus())
                || CommitmentStatus.EXPIRED.name().equals(c.getStatus())
                || CommitmentStatus.LIQUIDATED.name().equals(c.getStatus())) {
            throw new IllegalStateException("Commitment is not open for liquidation");
        }
        BigDecimal delta = amount != null ? amount : BigDecimal.ZERO;
        if (delta.signum() <= 0) throw new IllegalArgumentException("liquidation amount must be positive");
        BigDecimal newLiquidated = c.getLiquidatedAmount().add(delta);
        if (newLiquidated.compareTo(c.getCommittedAmount()) > 0) {
            throw new IllegalArgumentException("liquidation exceeds committed amount");
        }
        c.setLiquidatedAmount(newLiquidated);
        c.setStatus(newLiquidated.compareTo(c.getCommittedAmount()) == 0
                ? CommitmentStatus.LIQUIDATED.name()
                : CommitmentStatus.PARTIALLY_LIQUIDATED.name());
        commitmentRepository.save(c);

        // move committed → (actual posting adds spent separately)
        lineRepository.findByLineIdAndTenantId(c.getBudgetLineId(), tenantId)
                .ifPresent(line -> adjustProjection(tenantId, line, delta.negate(), BigDecimal.ZERO));
        emitter.emit("BUDGET_COMMITMENT", c.getCommitmentId().toString(), "BUDGET_COMMITMENT_LIQUIDATED",
                tenantId, payload(c));
        return c;
    }

    @Transactional
    public BudgetCommitmentEntity cancel(UUID tenantId, UUID commitmentId) {
        BudgetCommitmentEntity c = require(tenantId, commitmentId);
        if (CommitmentStatus.LIQUIDATED.name().equals(c.getStatus())) {
            throw new IllegalStateException("Liquidated commitment cannot be cancelled");
        }
        BigDecimal outstanding = c.outstanding();
        c.setStatus(CommitmentStatus.CANCELLED.name());
        commitmentRepository.save(c);
        lineRepository.findByLineIdAndTenantId(c.getBudgetLineId(), tenantId)
                .ifPresent(line -> adjustProjection(tenantId, line, outstanding.negate(), BigDecimal.ZERO));
        return c;
    }

    public List<BudgetCommitmentEntity> list(UUID tenantId, UUID budgetId) {
        return commitmentRepository.findByBudgetIdAndTenantIdOrderByCommittedAtDesc(budgetId, tenantId);
    }

    public BudgetCommitmentEntity require(UUID tenantId, UUID commitmentId) {
        return commitmentRepository.findByCommitmentIdAndTenantId(commitmentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Commitment not found"));
    }

    /** Adjust the flat availability projection for a line, if it is linked. */
    private void adjustProjection(UUID tenantId, BudgetLineEntity line,
                                  BigDecimal committedDelta, BigDecimal spentDelta) {
        if (line.getAllocationId() == null) return;
        allocationRepository.findByAllocationIdAndTenantId(line.getAllocationId(), tenantId).ifPresent(alloc -> {
            alloc.setCommittedAmount(nz(alloc.getCommittedAmount()).add(committedDelta).max(BigDecimal.ZERO));
            alloc.setSpentAmount(nz(alloc.getSpentAmount()).add(spentDelta).max(BigDecimal.ZERO));
            alloc.recomputeAvailable();
            allocationRepository.save(alloc);
        });
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private Map<String, Object> payload(BudgetCommitmentEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commitmentId", c.getCommitmentId().toString());
        m.put("budgetId", c.getBudgetId().toString());
        m.put("budgetLineId", c.getBudgetLineId().toString());
        m.put("tenantId", c.getTenantId().toString());
        m.put("ownerSystem", c.getOwnerSystem());
        m.put("ownerReference", c.getOwnerReference());
        m.put("committedAmount", c.getCommittedAmount());
        m.put("liquidatedAmount", c.getLiquidatedAmount());
        m.put("status", c.getStatus());
        return m;
    }
}
