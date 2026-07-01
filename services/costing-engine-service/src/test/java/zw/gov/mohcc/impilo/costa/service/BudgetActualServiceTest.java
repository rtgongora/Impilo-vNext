package zw.gov.mohcc.impilo.costa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetActualReferenceEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetAllocationEntity;
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
class BudgetActualServiceTest {

    @Mock private BudgetActualReferenceRepository actualRepository;
    @Mock private BudgetLineRepository lineRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetAllocationRepository allocationRepository;
    @Mock private BudgetCommitmentService commitmentService;
    @Mock private BudgetEventEmitter emitter;

    private BudgetActualService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private final UUID lineId = UUID.randomUUID();
    private final UUID allocationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BudgetActualService(actualRepository, lineRepository, budgetRepository,
                allocationRepository, commitmentService, emitter);
        BudgetEntity b = new BudgetEntity();
        b.setBudgetId(budgetId);
        b.setTenantId(tenantId);
        lenient().when(budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)).thenReturn(Optional.of(b));
        BudgetLineEntity line = new BudgetLineEntity();
        line.setLineId(lineId);
        line.setBudgetId(budgetId);
        line.setTenantId(tenantId);
        line.setAllocationId(allocationId);
        lenient().when(lineRepository.findByLineIdAndTenantId(lineId, tenantId)).thenReturn(Optional.of(line));
        lenient().when(actualRepository.save(any(BudgetActualReferenceEntity.class))).thenAnswer(inv -> {
            BudgetActualReferenceEntity e = inv.getArgument(0);
            if (e.getActualRefId() == null) e.setActualRefId(UUID.randomUUID());
            return e;
        });
    }

    private BudgetActualService.NewActual actual(UUID commitmentId) {
        return new BudgetActualService.NewActual(lineId, "MUSHEX_PAYMENT", "PAY-9", commitmentId,
                new BigDecimal("250"), "API");
    }

    @Test void post_new_addsSpentToProjection_emitsEvent() {
        when(actualRepository.findByTenantIdAndSourceAndSourceReference(tenantId, "MUSHEX_PAYMENT", "PAY-9"))
                .thenReturn(Optional.empty());
        BudgetAllocationEntity alloc = new BudgetAllocationEntity();
        alloc.setAllocationId(allocationId);
        alloc.setTenantId(tenantId);
        alloc.setAllocatedAmount(new BigDecimal("1000"));
        alloc.setSpentAmount(BigDecimal.ZERO);
        alloc.setCommittedAmount(BigDecimal.ZERO);
        when(allocationRepository.findByAllocationIdAndTenantId(allocationId, tenantId)).thenReturn(Optional.of(alloc));

        service.post(tenantId, budgetId, actual(null));

        assertEquals(0, new BigDecimal("250").compareTo(alloc.getSpentAmount()));
        assertEquals(0, new BigDecimal("750").compareTo(alloc.getAvailableAmount()));
        verify(emitter).emit(eq("BUDGET_ACTUAL"), any(), eq("BUDGET_ACTUAL_POSTED"), eq(tenantId), any());
    }

    @Test void post_idempotentOnSourceReference() {
        BudgetActualReferenceEntity existing = new BudgetActualReferenceEntity();
        existing.setActualRefId(UUID.randomUUID());
        when(actualRepository.findByTenantIdAndSourceAndSourceReference(tenantId, "MUSHEX_PAYMENT", "PAY-9"))
                .thenReturn(Optional.of(existing));
        BudgetActualReferenceEntity a = service.post(tenantId, budgetId, actual(null));
        assertSame(existing, a);
        verify(actualRepository, never()).save(any());
        verify(emitter, never()).emit(any(), any(), any(), any(), any());
    }

    @Test void post_withCommitment_liquidatesIt() {
        UUID commitmentId = UUID.randomUUID();
        when(actualRepository.findByTenantIdAndSourceAndSourceReference(tenantId, "MUSHEX_PAYMENT", "PAY-9"))
                .thenReturn(Optional.empty());
        when(allocationRepository.findByAllocationIdAndTenantId(allocationId, tenantId)).thenReturn(Optional.empty());

        service.post(tenantId, budgetId, actual(commitmentId));

        verify(commitmentService).liquidate(tenantId, commitmentId, new BigDecimal("250"));
    }

    @Test void post_nonPositiveAmount_rejected() {
        var bad = new BudgetActualService.NewActual(lineId, "MUSHEX_PAYMENT", "PAY-0", null, BigDecimal.ZERO, "API");
        assertThrows(IllegalArgumentException.class, () -> service.post(tenantId, budgetId, bad));
    }
}
