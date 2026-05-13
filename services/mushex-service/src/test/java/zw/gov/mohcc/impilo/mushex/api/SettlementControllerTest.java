package zw.gov.mohcc.impilo.mushex.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.mushex.domain.entity.SettlementEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.SettlementStatus;
import zw.gov.mohcc.impilo.mushex.service.SettlementService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SettlementControllerTest {

    @Mock
    private SettlementService settlementService;

    private MockMvc mockMvc;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
                "device-1", correlationId, facilityId, null, null, AccessMode.INTERNAL));
        mockMvc = MockMvcBuilders.standaloneSetup(new SettlementController(settlementService)).build();
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void listSettlements_withIntentIds_usesBatchFilter() throws Exception {
        SettlementEntity row = new SettlementEntity();
        row.setSettlementId("SET-001");
        row.setTenantId(tenantId);
        row.setPeriodStart(LocalDate.parse("2026-05-01"));
        row.setPeriodEnd(LocalDate.parse("2026-05-31"));
        row.setStatus(SettlementStatus.COMPUTED);
        when(settlementService.listSettlements(eq(List.of("PI-1", "PI-2")))).thenReturn(List.of(row));

        mockMvc.perform(get("/mushex/v1/settlements")
                        .param("intent_ids", "PI-1", "PI-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].settlementId").value("SET-001"));

        verify(settlementService).listSettlements(eq(List.of("PI-1", "PI-2")));
    }
}

