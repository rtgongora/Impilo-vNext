package zw.gov.mohcc.impilo.costa.api.access;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.costa.api.dto.EmergencyDeferredChargeResponse;
import zw.gov.mohcc.impilo.costa.api.dto.FlagEmergencyDeferredChargeRequest;
import zw.gov.mohcc.impilo.costa.api.dto.LinkConfirmedPersonRequest;
import zw.gov.mohcc.impilo.costa.api.dto.ReconcileDeferredChargeRequest;
import zw.gov.mohcc.impilo.costa.domain.enums.ReconciliationState;
import zw.gov.mohcc.impilo.costa.domain.enums.SettlementRoute;
import zw.gov.mohcc.impilo.costa.service.EmergencyReconciliationService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EmergencyReconciliationControllerTest {

    @Mock
    private EmergencyReconciliationService service;

    private MockMvc mockMvc;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();
    private final UUID deferredId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "FACILITY_FINANCE", "PAYMENT",
                "device-1", correlationId, facilityId, null, null, AccessMode.INTERNAL));
        mockMvc = MockMvcBuilders.standaloneSetup(new EmergencyReconciliationController(service))
                .setControllerAdvice(new ServiceAccessApiExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void flag_returnsCreatedEnvelope() throws Exception {
        when(service.flag(any(FlagEmergencyDeferredChargeRequest.class)))
                .thenReturn(sample(ReconciliationState.PENDING, null));

        mockMvc.perform(post("/costa/v1/emergency-reconciliation/deferred-charges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "encounter_id": "enc-emerg-1",
                                  "provisional_person_ref": "PROV-1",
                                  "deferred_amount": 240.00,
                                  "currency": "USD",
                                  "reason": "Acuity 1 resus; identity + payment deferred"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reconciliation_state").value("PENDING"))
                .andExpect(jsonPath("$.data.provisional_person_ref").value("PROV-1"));

        verify(service).flag(any(FlagEmergencyDeferredChargeRequest.class));
    }

    @Test
    void list_filtersByState() throws Exception {
        when(service.list("PENDING", null)).thenReturn(List.of(sample(ReconciliationState.PENDING, null)));

        mockMvc.perform(get("/costa/v1/emergency-reconciliation/deferred-charges").param("state", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reconciliation_state").value("PENDING"));

        verify(service).list(eq("PENDING"), eq(null));
    }

    @Test
    void linkPerson_returnsReconciledIdentity() throws Exception {
        when(service.linkConfirmedPerson(eq(deferredId), any(LinkConfirmedPersonRequest.class)))
                .thenReturn(sample(ReconciliationState.PENDING, null));

        mockMvc.perform(post("/costa/v1/emergency-reconciliation/deferred-charges/" + deferredId + "/link-person")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed_person_ref\":\"CPID-CONFIRMED-9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).linkConfirmedPerson(eq(deferredId), any(LinkConfirmedPersonRequest.class));
    }

    @Test
    void linkPerson_missingConfirmedRef_isBadRequest() throws Exception {
        mockMvc.perform(post("/costa/v1/emergency-reconciliation/deferred-charges/" + deferredId + "/link-person")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reconcile_routesToWaiver() throws Exception {
        when(service.reconcile(eq(deferredId), any(ReconcileDeferredChargeRequest.class)))
                .thenReturn(sample(ReconciliationState.RECONCILED, SettlementRoute.WAIVER));

        mockMvc.perform(post("/costa/v1/emergency-reconciliation/deferred-charges/" + deferredId + "/reconcile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlement_route\":\"WAIVER\",\"reason\":\"hardship\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reconciliation_state").value("RECONCILED"))
                .andExpect(jsonPath("$.data.settlement_route").value("WAIVER"));

        verify(service).reconcile(eq(deferredId), any(ReconcileDeferredChargeRequest.class));
    }

    @Test
    void reconcile_missingRoute_isBadRequest() throws Exception {
        mockMvc.perform(post("/costa/v1/emergency-reconciliation/deferred-charges/" + deferredId + "/reconcile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private EmergencyDeferredChargeResponse sample(ReconciliationState state, SettlementRoute route) {
        return new EmergencyDeferredChargeResponse(
                deferredId, tenantId, null, null, "enc-emerg-1", facilityId,
                "PROV-1", route != null ? "CPID-CONFIRMED-9" : null,
                route != null, route != null ? OffsetDateTime.now() : null,
                new BigDecimal("240.00"), "USD", state, route, null, null,
                "actor-1", route != null ? "actor-1" : null, "AUD-1",
                OffsetDateTime.now(), state == ReconciliationState.RECONCILED ? OffsetDateTime.now() : null);
    }
}
