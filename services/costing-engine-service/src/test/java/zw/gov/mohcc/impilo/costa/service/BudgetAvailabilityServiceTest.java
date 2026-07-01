package zw.gov.mohcc.impilo.costa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetLineEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.AvailabilityDecision;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetActualReferenceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetCommitmentRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetLineRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetAvailabilityServiceTest {

    @Mock private BudgetLineRepository lineRepository;
    @Mock private BudgetCommitmentRepository commitmentRepository;
    @Mock private BudgetActualReferenceRepository actualRepository;
    @Mock private BudgetEventEmitter emitter;

    private BudgetAvailabilityService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID lineId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BudgetAvailabilityService(lineRepository, commitmentRepository,
                actualRepository, emitter, "0.90");
        BudgetLineEntity line = new BudgetLineEntity();
        line.setLineId(lineId);
        line.setBudgetId(UUID.randomUUID());
        line.setTenantId(tenantId);
        line.setAllocatedAmount(new BigDecimal("1000"));
        when(lineRepository.findByLineIdAndTenantId(lineId, tenantId)).thenReturn(Optional.of(line));
    }

    private void ledger(String committed, String actual) {
        when(commitmentRepository.sumOutstandingByLine(tenantId, lineId)).thenReturn(new BigDecimal(committed));
        when(actualRepository.sumActualByLine(tenantId, lineId)).thenReturn(new BigDecimal(actual));
    }

    @Test void allow_whenWithinAndBelowWarn() {
        ledger("0", "0");
        var r = service.check(tenantId, lineId, new BigDecimal("100"), false, false);
        assertEquals(AvailabilityDecision.ALLOW, r.decision());
    }

    @Test void warn_whenWithinButAboveWarnThreshold() {
        ledger("800", "100"); // available 100; util after 50 = 0.95
        var r = service.check(tenantId, lineId, new BigDecimal("50"), false, false);
        assertEquals(AvailabilityDecision.WARN, r.decision());
    }

    @Test void hardStop_whenOverAndNotEmergency() {
        ledger("900", "50"); // available 50
        var r = service.check(tenantId, lineId, new BigDecimal("200"), false, false);
        assertEquals(AvailabilityDecision.HARD_STOP, r.decision());
        verify(emitter, never()).emit(any(), any(), any(), any(), any());
    }

    @Test void emergencyOverride_whenClinicalEmergencyOverBudget_emitsEvent() {
        ledger("900", "50");
        var r = service.check(tenantId, lineId, new BigDecimal("200"), false, true);
        assertEquals(AvailabilityDecision.EMERGENCY_OVERRIDE, r.decision());
        verify(emitter).emit(eq("BUDGET_AVAILABILITY"), eq(lineId.toString()),
                eq("BUDGET_AVAILABILITY_OVERRIDE"), eq(tenantId), any());
    }
}
