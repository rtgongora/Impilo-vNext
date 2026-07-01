package zw.gov.mohcc.impilo.costa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class BudgetRevisionServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetVersionRepository versionRepository;
    @Mock private BudgetLineRepository lineRepository;
    @Mock private BudgetRevisionRepository revisionRepository;
    @Mock private BudgetProjectionSyncer projectionSyncer;
    @Mock private BudgetEventEmitter emitter;

    private BudgetRevisionService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private final UUID v0Id = UUID.randomUUID();
    private final UUID lineA = UUID.randomUUID();
    private final UUID lineB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BudgetRevisionService(budgetRepository, versionRepository, lineRepository,
                revisionRepository, projectionSyncer, emitter);
        BudgetEntity b = new BudgetEntity();
        b.setBudgetId(budgetId);
        b.setTenantId(tenantId);
        b.setStatus("ACTIVE");
        b.setPeriodYear(2026);
        b.setCurrentVersionId(v0Id);
        when(budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)).thenReturn(Optional.of(b));

        BudgetVersionEntity v0 = new BudgetVersionEntity();
        v0.setVersionId(v0Id);
        v0.setBudgetId(budgetId);
        v0.setTenantId(tenantId);
        v0.setVersionNo(1);
        v0.setCurrent(true);
        v0.setTotalAmount(new BigDecimal("1000"));
        lenient().when(versionRepository.findByBudgetIdAndTenantIdAndCurrentTrue(budgetId, tenantId))
                .thenReturn(Optional.of(v0));

        lenient().when(lineRepository.findByVersionIdAndTenantIdOrderByLineOrderAscCreatedAtAsc(v0Id, tenantId))
                .thenReturn(List.of(line(lineA, "600"), line(lineB, "400")));

        lenient().when(versionRepository.save(any(BudgetVersionEntity.class))).thenAnswer(inv -> {
            BudgetVersionEntity v = inv.getArgument(0);
            if (v.getVersionId() == null) v.setVersionId(UUID.randomUUID());
            return v;
        });
        lenient().when(lineRepository.save(any(BudgetLineEntity.class))).thenAnswer(inv -> {
            BudgetLineEntity l = inv.getArgument(0);
            if (l.getLineId() == null) l.setLineId(UUID.randomUUID());
            return l;
        });
        lenient().when(revisionRepository.save(any(BudgetRevisionEntity.class))).thenAnswer(inv -> {
            BudgetRevisionEntity r = inv.getArgument(0);
            if (r.getRevisionId() == null) r.setRevisionId(UUID.randomUUID());
            return r;
        });
    }

    private BudgetLineEntity line(UUID id, String amount) {
        BudgetLineEntity l = new BudgetLineEntity();
        l.setLineId(id);
        l.setVersionId(v0Id);
        l.setBudgetId(budgetId);
        l.setTenantId(tenantId);
        l.setBudgetCategory("CAT-" + id.toString().substring(0, 4));
        l.setAllocatedAmount(new BigDecimal(amount));
        return l;
    }

    private BudgetRevisionService.RevisionRequest virement(String aAmt, String bAmt) {
        return new BudgetRevisionService.RevisionRequest("VIREMENT", "reallocate", List.of(
                new BudgetRevisionService.LineAdjustment(lineA, new BigDecimal(aAmt)),
                new BudgetRevisionService.LineAdjustment(lineB, new BigDecimal(bAmt))));
    }

    @Test void virement_mustNetToZero() {
        var req = new BudgetRevisionService.RevisionRequest("VIREMENT", "x", List.of(
                new BudgetRevisionService.LineAdjustment(lineA, new BigDecimal("500")))); // -100 net
        assertThrows(IllegalArgumentException.class, () -> service.apply(tenantId, budgetId, "actor", req));
    }

    @Test void virement_netZero_appliesAndKeepsTotal() {
        var rev = service.apply(tenantId, budgetId, "actor", virement("500", "500"));
        assertEquals("APPLIED", rev.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(rev.getNetChange()));

        ArgumentCaptor<BudgetVersionEntity> cap = ArgumentCaptor.forClass(BudgetVersionEntity.class);
        verify(versionRepository, atLeast(1)).save(cap.capture());
        BudgetVersionEntity v1 = cap.getAllValues().stream()
                .filter(v -> v.getVersionNo() == 2).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1000").compareTo(v1.getTotalAmount()));
        verify(projectionSyncer).sync(eq(tenantId), any(), eq(v1.getVersionId()));
        verify(emitter).emit(eq("BUDGET"), eq(budgetId.toString()), eq("BUDGET_REVISED"), eq(tenantId), any());
    }

    @Test void reprogramming_allowsNonZeroNetChange() {
        var req = new BudgetRevisionService.RevisionRequest("REPROGRAMMING", "top-up", List.of(
                new BudgetRevisionService.LineAdjustment(lineA, new BigDecimal("700")))); // +100 net
        var rev = service.apply(tenantId, budgetId, "actor", req);
        assertEquals("APPLIED", rev.getStatus());
        assertEquals(0, new BigDecimal("100").compareTo(rev.getNetChange()));
    }

    @Test void revision_onNonActiveBudget_rejected() {
        BudgetEntity draft = new BudgetEntity();
        draft.setBudgetId(budgetId);
        draft.setStatus("DRAFT");
        when(budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)).thenReturn(Optional.of(draft));
        var req = virement("500", "500");
        assertThrows(IllegalStateException.class, () -> service.apply(tenantId, budgetId, "actor", req));
    }
}
