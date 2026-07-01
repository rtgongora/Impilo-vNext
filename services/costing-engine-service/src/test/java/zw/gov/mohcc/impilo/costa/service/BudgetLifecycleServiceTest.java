package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.*;
import zw.gov.mohcc.impilo.costa.domain.repository.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetLifecycleServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetVersionRepository versionRepository;
    @Mock private BudgetLineRepository lineRepository;
    @Mock private BudgetApprovalRepository approvalRepository;
    @Mock private BudgetAllocationRepository allocationRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Captor private ArgumentCaptor<EventOutboxEntity> outboxCaptor;

    private BudgetLifecycleService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BudgetProjectionSyncer syncer = new BudgetProjectionSyncer(allocationRepository, lineRepository);
        service = new BudgetLifecycleService(budgetRepository, versionRepository, lineRepository,
                approvalRepository, syncer, outboxRepository, new ObjectMapper(), true);
        lenient().when(budgetRepository.save(any(BudgetEntity.class))).thenAnswer(inv -> {
            BudgetEntity e = inv.getArgument(0);
            if (e.getBudgetId() == null) e.setBudgetId(UUID.randomUUID());
            return e;
        });
        lenient().when(versionRepository.save(any(BudgetVersionEntity.class))).thenAnswer(inv -> {
            BudgetVersionEntity e = inv.getArgument(0);
            if (e.getVersionId() == null) e.setVersionId(UUID.randomUUID());
            return e;
        });
        lenient().when(lineRepository.save(any(BudgetLineEntity.class))).thenAnswer(inv -> {
            BudgetLineEntity e = inv.getArgument(0);
            if (e.getLineId() == null) e.setLineId(UUID.randomUUID());
            return e;
        });
        lenient().when(allocationRepository.save(any(BudgetAllocationEntity.class))).thenAnswer(inv -> {
            BudgetAllocationEntity e = inv.getArgument(0);
            if (e.getAllocationId() == null) e.setAllocationId(UUID.randomUUID());
            return e;
        });
        lenient().when(approvalRepository.findByBudgetIdAndTenantIdOrderByDecidedAtDesc(any(), any()))
                .thenReturn(List.of());
    }

    private BudgetLifecycleService.NewBudget facilityBudget() {
        return new BudgetLifecycleService.NewBudget(facilityId, "FACILITY", 2026,
                null, null, "HIV Programme 2026", null, "HIV", null, "USD", null);
    }

    @Test
    void createBudget_persistsDraftAndVersionOne_emitsCreated() {
        BudgetEntity b = service.createBudget(tenantId, "creator-1", facilityBudget());

        assertEquals("DRAFT", b.getStatus());
        assertEquals("creator-1", b.getCreatedBy());
        assertNotNull(b.getCurrentVersionId());
        verify(versionRepository, atLeastOnce()).save(any(BudgetVersionEntity.class));
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("BUDGET_CREATED", outboxCaptor.getValue().getEventType());
    }

    @Test
    void createBudget_facilityScopeWithoutFacility_rejected() {
        var cmd = new BudgetLifecycleService.NewBudget(null, "FACILITY", 2026,
                null, null, "x", null, null, null, "USD", null);
        assertThrows(IllegalArgumentException.class, () -> service.createBudget(tenantId, "c", cmd));
    }

    @Test
    void addLine_onlyAllowedWhileDraft() {
        BudgetEntity submitted = draftBudget();
        submitted.setStatus("SUBMITTED");
        when(budgetRepository.findByBudgetIdAndTenantId(submitted.getBudgetId(), tenantId))
                .thenReturn(Optional.of(submitted));
        UUID id = submitted.getBudgetId();
        var line = new BudgetLifecycleService.NewLine(null, "PHARMACY", "MEDICINES",
                null, null, null, null, null, new BigDecimal("1000"), 0);
        assertThrows(IllegalStateException.class, () -> service.addLine(tenantId, id, line));
    }

    @Test
    void addLine_draft_recomputesVersionTotal() {
        BudgetEntity b = draftBudget();
        BudgetVersionEntity v = currentVersion(b);
        when(budgetRepository.findByBudgetIdAndTenantId(b.getBudgetId(), tenantId)).thenReturn(Optional.of(b));
        when(versionRepository.findByBudgetIdAndTenantIdAndCurrentTrue(b.getBudgetId(), tenantId))
                .thenReturn(Optional.of(v));
        BudgetLineEntity persisted = new BudgetLineEntity();
        persisted.setAllocatedAmount(new BigDecimal("1500"));
        when(lineRepository.findByVersionIdAndTenantIdOrderByLineOrderAscCreatedAtAsc(v.getVersionId(), tenantId))
                .thenReturn(List.of(persisted));

        service.addLine(tenantId, b.getBudgetId(),
                new BudgetLifecycleService.NewLine(null, "PHARMACY", "MEDICINES",
                        null, null, null, null, "GL-5001", new BigDecimal("1500"), 0));

        assertEquals(0, new BigDecimal("1500").compareTo(v.getTotalAmount()));
    }

    @Test
    void approve_bySameCreator_blockedBySegregationOfDuties() {
        BudgetEntity b = draftBudget();
        b.setStatus("UNDER_REVIEW");
        b.setCreatedBy("creator-1");
        when(budgetRepository.findByBudgetIdAndTenantId(b.getBudgetId(), tenantId)).thenReturn(Optional.of(b));
        UUID id = b.getBudgetId();
        assertThrows(IllegalStateException.class, () -> service.approve(tenantId, id, "creator-1", null));
    }

    @Test
    void approve_byDifferentActor_movesToApproved_emitsApproved() {
        BudgetEntity b = draftBudget();
        b.setStatus("UNDER_REVIEW");
        b.setCreatedBy("creator-1");
        when(budgetRepository.findByBudgetIdAndTenantId(b.getBudgetId(), tenantId)).thenReturn(Optional.of(b));

        BudgetEntity result = service.approve(tenantId, b.getBudgetId(), "approver-2", "looks good");

        assertEquals("APPROVED", result.getStatus());
        verify(approvalRepository).save(any(BudgetApprovalEntity.class));
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("BUDGET_APPROVED", outboxCaptor.getValue().getEventType());
    }

    @Test
    void activate_fromApproved_syncsAllocationProjection_emitsActivated() {
        BudgetEntity b = draftBudget();
        b.setStatus("APPROVED");
        BudgetVersionEntity v = currentVersion(b);
        when(budgetRepository.findByBudgetIdAndTenantId(b.getBudgetId(), tenantId)).thenReturn(Optional.of(b));
        when(versionRepository.findByBudgetIdAndTenantIdAndCurrentTrue(b.getBudgetId(), tenantId))
                .thenReturn(Optional.of(v));
        BudgetLineEntity line = new BudgetLineEntity();
        line.setBudgetId(b.getBudgetId());
        line.setVersionId(v.getVersionId());
        line.setTenantId(tenantId);
        line.setDepartmentId("PHARMACY");
        line.setBudgetCategory("MEDICINES");
        line.setAllocatedAmount(new BigDecimal("5000"));
        when(lineRepository.findByVersionIdAndTenantIdOrderByLineOrderAscCreatedAtAsc(v.getVersionId(), tenantId))
                .thenReturn(List.of(line));
        when(allocationRepository.findForSpendLine(tenantId, facilityId, "PHARMACY", "MEDICINES", 2026))
                .thenReturn(Optional.empty());

        BudgetEntity result = service.activate(tenantId, b.getBudgetId(), "approver-2", null);

        assertEquals("ACTIVE", result.getStatus());
        ArgumentCaptor<BudgetAllocationEntity> allocCaptor = ArgumentCaptor.forClass(BudgetAllocationEntity.class);
        verify(allocationRepository).save(allocCaptor.capture());
        assertEquals(0, new BigDecimal("5000").compareTo(allocCaptor.getValue().getAllocatedAmount()));
        assertEquals(0, new BigDecimal("5000").compareTo(allocCaptor.getValue().getAvailableAmount()));
        verify(outboxRepository, atLeastOnce()).save(outboxCaptor.capture());
        assertTrue(outboxCaptor.getAllValues().stream()
                .anyMatch(e -> "BUDGET_ACTIVATED".equals(e.getEventType())));
    }

    @Test
    void submit_illegalFromActive_throws() {
        BudgetEntity b = draftBudget();
        b.setStatus("ACTIVE");
        when(budgetRepository.findByBudgetIdAndTenantId(b.getBudgetId(), tenantId)).thenReturn(Optional.of(b));
        UUID id = b.getBudgetId();
        assertThrows(IllegalStateException.class, () -> service.submit(tenantId, id, "x", null));
    }

    // ----- fixtures ---------------------------------------------------------

    private BudgetEntity draftBudget() {
        BudgetEntity b = new BudgetEntity();
        b.setBudgetId(UUID.randomUUID());
        b.setTenantId(tenantId);
        b.setFacilityId(facilityId);
        b.setScopeLevel("FACILITY");
        b.setPeriodYear(2026);
        b.setTitle("HIV Programme 2026");
        b.setCurrency("USD");
        b.setStatus("DRAFT");
        b.setCreatedBy("creator-1");
        b.setCurrentVersionId(UUID.randomUUID());
        return b;
    }

    private BudgetVersionEntity currentVersion(BudgetEntity b) {
        BudgetVersionEntity v = new BudgetVersionEntity();
        v.setVersionId(b.getCurrentVersionId());
        v.setBudgetId(b.getBudgetId());
        v.setTenantId(tenantId);
        v.setVersionNo(1);
        v.setCurrent(true);
        v.setStatus("DRAFT");
        v.setTotalAmount(BigDecimal.ZERO);
        return v;
    }
}
