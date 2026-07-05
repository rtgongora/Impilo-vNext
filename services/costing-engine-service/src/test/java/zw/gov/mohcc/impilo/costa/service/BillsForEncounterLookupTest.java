package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BillStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.*;
import zw.gov.mohcc.impilo.costa.engine.CostEngineRegistry;
import zw.gov.mohcc.impilo.costa.exemptions.ExemptionEngine;
import zw.gov.mohcc.impilo.costa.integration.CoverageServiceClient;
import zw.gov.mohcc.impilo.costa.rules.ChargingRuleEngine;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Read-only bills-by-encounter lookup used by the clinical billing
 * visibility seam (BFF {@code GET /internal/v1/encounters/{id}/billing}).
 * Must be tenant-scoped and newest-first.
 */
@ExtendWith(MockitoExtension.class)
class BillsForEncounterLookupTest {

    @Mock private BillHeaderRepository billHeaderRepository;
    @Mock private BillLineRepository billLineRepository;
    @Mock private BillPartyRepository billPartyRepository;
    @Mock private EncounterRepository encounterRepository;
    @Mock private ApprovalRepository approvalRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private OperationalFinanceService operationalFinanceService;
    @Mock private CostEngineRegistry costEngineRegistry;
    @Mock private ChargingRuleEngine chargingRuleEngine;
    @Mock private ExemptionEngine exemptionEngine;
    @Mock private PatientAccountService patientAccountService;
    @Mock private CoverageServiceClient coverageServiceClient;
    @Mock private InsurancePlanRepository insurancePlanRepository;

    private BillService service() {
        return new BillService(billHeaderRepository, billLineRepository, billPartyRepository,
                encounterRepository, approvalRepository, outboxRepository, operationalFinanceService,
                costEngineRegistry, chargingRuleEngine, exemptionEngine, new ObjectMapper(),
                patientAccountService, coverageServiceClient, insurancePlanRepository);
    }

    private static BillHeaderEntity bill(String billId, UUID tenantId, OffsetDateTime createdAt) {
        BillHeaderEntity bill = new BillHeaderEntity();
        bill.setBillId(billId);
        bill.setTenantId(tenantId);
        bill.setEncounterId("enc-1");
        bill.setStatus(BillStatus.FINAL);
        // createdAt is @PrePersist-managed (no setter) — set directly for ordering assertions.
        try {
            var field = BillHeaderEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(bill, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return bill;
    }

    @Test
    void getBillsForEncounter_isTenantScopedAndNewestFirst() {
        UUID myTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        when(billHeaderRepository.findByEncounterId("enc-1")).thenReturn(List.of(
                bill("BILL-OLD", myTenant, now.minusDays(2)),
                bill("BILL-OTHER-TENANT", otherTenant, now),
                bill("BILL-NEW", myTenant, now.minusHours(1))));

        List<BillHeaderEntity> result = service().getBillsForEncounter(myTenant, "enc-1");

        assertEquals(2, result.size(), "other tenants' bills must never leak");
        assertEquals("BILL-NEW", result.get(0).getBillId());
        assertEquals("BILL-OLD", result.get(1).getBillId());
    }
}
