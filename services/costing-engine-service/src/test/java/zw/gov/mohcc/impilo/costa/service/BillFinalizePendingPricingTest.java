package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BillLineEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BillStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.*;
import zw.gov.mohcc.impilo.costa.engine.CostEngineRegistry;
import zw.gov.mohcc.impilo.costa.rules.ChargingRuleEngine;
import zw.gov.mohcc.impilo.costa.exemptions.ExemptionEngine;
import zw.gov.mohcc.impilo.costa.integration.CoverageServiceClient;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Zero-price guardrail: a bill with a line that priced to zero by ABSENCE
 * (no configured tariff → pending_pricing) must not finalize at the wrong
 * total; the explicit allowUnpriced override exists for genuinely-free lines.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillFinalizePendingPricingTest {

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

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                UUID.randomUUID(), "actor-1", "FACILITY_FINANCE", "BILLING",
                "device-1", UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        BillHeaderEntity bill = new BillHeaderEntity();
        bill.setBillId("BILL1");
        bill.setStatus(BillStatus.APPROVED);
        when(billHeaderRepository.findById("BILL1")).thenReturn(Optional.of(bill));
        when(billHeaderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private static BillLineEntity line(String code, boolean pendingPricing) {
        BillLineEntity l = new BillLineEntity();
        l.setLineId("L-" + code);
        l.setBillId("BILL1");
        l.setMsikaCode(code);
        l.setPendingPricing(pendingPricing);
        return l;
    }

    @Test
    void finalize_refusesWhilePendingPricingLineExists() {
        when(billLineRepository.findByBillIdAndVoidedFalse("BILL1"))
                .thenReturn(List.of(line("CONSULT-GP", false), line("LAB-UNMAPPED", true)));

        var ex = assertThrows(IllegalStateException.class, () -> service().finalize("BILL1"));
        assertTrue(ex.getMessage().contains("LAB-UNMAPPED"));
        assertTrue(ex.getMessage().contains("pending pricing"));
    }

    @Test
    void finalize_proceedsWhenAllLinesPriced() {
        when(billLineRepository.findByBillIdAndVoidedFalse("BILL1"))
                .thenReturn(List.of(line("CONSULT-GP", false)));

        BillHeaderEntity result = service().finalize("BILL1");
        assertEquals(BillStatus.FINAL, result.getStatus());
    }

    @Test
    void finalize_allowUnpricedOverride_finalizesFreeLines() {
        when(billLineRepository.findByBillIdAndVoidedFalse("BILL1"))
                .thenReturn(List.of(line("FREE-IMMUNISATION", true)));

        BillHeaderEntity result = service().finalize("BILL1", null, true);
        assertEquals(BillStatus.FINAL, result.getStatus());
    }
}
