package zw.gov.mohcc.impilo.costa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetCommitmentEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetLineEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetCommitmentServiceTest {

    @Mock private BudgetCommitmentRepository commitmentRepository;
    @Mock private BudgetLineRepository lineRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetAllocationRepository allocationRepository;
    @Mock private BudgetEventEmitter emitter;

    private BudgetCommitmentService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private final UUID lineId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BudgetCommitmentService(commitmentRepository, lineRepository,
                budgetRepository, allocationRepository, emitter);
        lenient().when(commitmentRepository.save(any(BudgetCommitmentEntity.class))).thenAnswer(inv -> {
            BudgetCommitmentEntity e = inv.getArgument(0);
            if (e.getCommitmentId() == null) e.setCommitmentId(UUID.randomUUID());
            return e;
        });
    }

    private void activeBudgetWithLine() {
        BudgetEntity b = new BudgetEntity();
        b.setBudgetId(budgetId);
        b.setTenantId(tenantId);
        b.setStatus("ACTIVE");
        when(budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)).thenReturn(Optional.of(b));
        BudgetLineEntity line = new BudgetLineEntity();
        line.setLineId(lineId);
        line.setBudgetId(budgetId);
        line.setTenantId(tenantId);
        line.setAllocatedAmount(new BigDecimal("5000"));
        when(lineRepository.findByLineIdAndTenantId(lineId, tenantId)).thenReturn(Optional.of(line));
    }

    private BudgetCommitmentService.NewCommitment cmd(String committed, String obligated) {
        return new BudgetCommitmentService.NewCommitment(lineId, "OROS", "PO-123", "desk",
                new BigDecimal(committed), obligated == null ? null : new BigDecimal(obligated), null);
    }

    @Test void record_valid_emitsEvent() {
        activeBudgetWithLine();
        when(commitmentRepository.findByTenantIdAndOwnerSystemAndOwnerReference(tenantId, "OROS", "PO-123"))
                .thenReturn(Optional.empty());
        BudgetCommitmentEntity c = service.record(tenantId, budgetId, cmd("1000", "1000"));
        assertEquals("COMMITTED", c.getStatus());
        verify(emitter).emit(eq("BUDGET_COMMITMENT"), any(), eq("BUDGET_COMMITMENT_RECORDED"), eq(tenantId), any());
    }

    @Test void record_onNonActiveBudget_rejected() {
        BudgetEntity b = new BudgetEntity();
        b.setBudgetId(budgetId);
        b.setStatus("DRAFT");
        when(budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)).thenReturn(Optional.of(b));
        assertThrows(IllegalStateException.class, () -> service.record(tenantId, budgetId, cmd("1000", null)));
    }

    @Test void record_obligatedExceedsCommitted_rejected() {
        activeBudgetWithLine();
        assertThrows(IllegalArgumentException.class, () -> service.record(tenantId, budgetId, cmd("1000", "1500")));
    }

    @Test void record_idempotentOnOwnerReference() {
        activeBudgetWithLine();
        BudgetCommitmentEntity existing = new BudgetCommitmentEntity();
        existing.setCommitmentId(UUID.randomUUID());
        when(commitmentRepository.findByTenantIdAndOwnerSystemAndOwnerReference(tenantId, "OROS", "PO-123"))
                .thenReturn(Optional.of(existing));
        BudgetCommitmentEntity c = service.record(tenantId, budgetId, cmd("1000", null));
        assertSame(existing, c);
        verify(commitmentRepository, never()).save(any());
    }

    @Test void liquidate_partialThenFull_updatesStatus() {
        BudgetCommitmentEntity c = new BudgetCommitmentEntity();
        c.setCommitmentId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setBudgetId(budgetId);
        c.setBudgetLineId(lineId);
        c.setCommittedAmount(new BigDecimal("1000"));
        c.setLiquidatedAmount(BigDecimal.ZERO);
        c.setStatus("COMMITTED");
        when(commitmentRepository.findByCommitmentIdAndTenantId(c.getCommitmentId(), tenantId)).thenReturn(Optional.of(c));
        when(lineRepository.findByLineIdAndTenantId(lineId, tenantId)).thenReturn(Optional.empty());

        service.liquidate(tenantId, c.getCommitmentId(), new BigDecimal("400"));
        assertEquals("PARTIALLY_LIQUIDATED", c.getStatus());
        service.liquidate(tenantId, c.getCommitmentId(), new BigDecimal("600"));
        assertEquals("LIQUIDATED", c.getStatus());
    }

    @Test void liquidate_overCommitted_rejected() {
        BudgetCommitmentEntity c = new BudgetCommitmentEntity();
        c.setCommitmentId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setBudgetId(budgetId);
        c.setBudgetLineId(lineId);
        c.setCommittedAmount(new BigDecimal("1000"));
        c.setLiquidatedAmount(BigDecimal.ZERO);
        c.setStatus("COMMITTED");
        when(commitmentRepository.findByCommitmentIdAndTenantId(c.getCommitmentId(), tenantId)).thenReturn(Optional.of(c));
        UUID id = c.getCommitmentId();
        assertThrows(IllegalArgumentException.class, () -> service.liquidate(tenantId, id, new BigDecimal("1500")));
    }
}
