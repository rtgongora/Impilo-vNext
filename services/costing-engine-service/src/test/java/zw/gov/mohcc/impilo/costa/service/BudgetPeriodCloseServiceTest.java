package zw.gov.mohcc.impilo.costa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetPeriodCloseEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetPeriodCloseRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetPeriodCloseServiceTest {

    @Mock private BudgetPeriodCloseRepository repository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetEventEmitter emitter;

    private BudgetPeriodCloseService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BudgetPeriodCloseService(repository, budgetRepository, emitter);
        lenient().when(repository.save(any(BudgetPeriodCloseEntity.class))).thenAnswer(inv -> {
            BudgetPeriodCloseEntity e = inv.getArgument(0);
            if (e.getCloseId() == null) e.setCloseId(UUID.randomUUID());
            return e;
        });
    }

    @Test void hardClose_movesBudgetToClosed_emitsEvent() {
        BudgetEntity b = new BudgetEntity();
        b.setBudgetId(budgetId);
        b.setTenantId(tenantId);
        b.setStatus("ACTIVE");
        when(budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)).thenReturn(Optional.of(b));
        when(repository.findByTenantIdAndBudgetIdAndFinancialPeriodId(tenantId, budgetId, periodId))
                .thenReturn(Optional.empty());

        var e = service.close(tenantId, budgetId, "cfo",
                new BudgetPeriodCloseService.CloseRequest(periodId, "HARD_CLOSED", null, "year end"));

        assertEquals("HARD_CLOSED", e.getStatus());
        assertEquals("CLOSED", b.getStatus());
        verify(emitter).emit(eq("BUDGET"), eq(budgetId.toString()), eq("BUDGET_PERIOD_CLOSED"), eq(tenantId), any());
    }

    @Test void reopen_hardClosed_rejected() {
        BudgetPeriodCloseEntity e = new BudgetPeriodCloseEntity();
        e.setCloseId(UUID.randomUUID());
        e.setStatus("HARD_CLOSED");
        when(repository.findByCloseIdAndTenantId(e.getCloseId(), tenantId)).thenReturn(Optional.of(e));
        UUID id = e.getCloseId();
        assertThrows(IllegalStateException.class, () -> service.reopen(tenantId, id, "cfo", "oops"));
    }
}
