package zw.gov.mohcc.impilo.costa.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.costa.domain.entity.InsurancePlanEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.InsurancePlanRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InsurancePlanControllerTest {

    @Mock
    private InsurancePlanRepository repository;

    private MockMvc mockMvc;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                tenantId, "finance-1", "FINANCE", "BILLING",
                "dev", UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        mockMvc = MockMvcBuilders.standaloneSetup(new InsurancePlanController(repository)).build();
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private InsurancePlanEntity plan(String code, String status) {
        InsurancePlanEntity p = new InsurancePlanEntity();
        p.setPlanCode(code);
        p.setPlanName("Sample plan");
        p.setStatus(status);
        p.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        return p;
    }

    @Test
    void list_pendingTerms_filtersByStatus() throws Exception {
        when(repository.findByTenantIdAndStatus(tenantId, "PENDING_TERMS"))
                .thenReturn(List.of(plan("AHFOZ-GOLD", "PENDING_TERMS")));

        mockMvc.perform(get("/costa/v1/insurance-plans").param("status", "PENDING_TERMS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].planCode").value("AHFOZ-GOLD"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_TERMS"));
        verify(repository).findByTenantIdAndStatus(tenantId, "PENDING_TERMS");
    }

    @Test
    void list_noFilter_returnsAllForTenant() throws Exception {
        when(repository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(plan("AHFOZ-GOLD", "ACTIVE")));
        mockMvc.perform(get("/costa/v1/insurance-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].planCode").value("AHFOZ-GOLD"));
    }

    @Test
    void configure_activatesPendingPlan() throws Exception {
        when(repository.findByTenantIdAndPlanCode(tenantId, "AHFOZ-GOLD"))
                .thenReturn(List.of(plan("AHFOZ-GOLD", "PENDING_TERMS")));
        when(repository.save(any(InsurancePlanEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/costa/v1/insurance-plans/AHFOZ-GOLD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"coveragePct":70,"copayPct":30,"deductible":10,"annualCap":5000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.coveragePct").value(70));

        ArgumentCaptor<InsurancePlanEntity> saved = ArgumentCaptor.forClass(InsurancePlanEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getValue().getCoveragePct()).isEqualByComparingTo(new BigDecimal("70"));
        assertThat(saved.getValue().getCopayPct()).isEqualByComparingTo(new BigDecimal("30"));
    }

    @Test
    void configure_missingTerms_isBadRequest() throws Exception {
        mockMvc.perform(put("/costa/v1/insurance-plans/AHFOZ-GOLD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deductible\":10}"))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @Test
    void configure_outOfRange_isBadRequest() throws Exception {
        mockMvc.perform(put("/costa/v1/insurance-plans/AHFOZ-GOLD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coveragePct\":150,\"copayPct\":30}"))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @Test
    void configure_unknownPlan_isNotFound() throws Exception {
        when(repository.findByTenantIdAndPlanCode(eq(tenantId), any()))
                .thenReturn(List.of());
        mockMvc.perform(put("/costa/v1/insurance-plans/NOPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coveragePct\":70,\"copayPct\":30}"))
                .andExpect(status().isNotFound());
    }
}
