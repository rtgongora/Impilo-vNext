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
import zw.gov.mohcc.impilo.costa.api.dto.ServiceAccessDecisionCreateRequest;
import zw.gov.mohcc.impilo.costa.api.dto.ServiceAccessDecisionResponse;
import zw.gov.mohcc.impilo.costa.domain.enums.BillingTimingMode;
import zw.gov.mohcc.impilo.costa.domain.enums.ServiceAccessStatus;
import zw.gov.mohcc.impilo.costa.service.ServiceAccessDecisionService;
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
class ServiceAccessDecisionControllerTest {

    @Mock
    private ServiceAccessDecisionService decisionService;

    private MockMvc mockMvc;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();
    private final UUID decisionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "FACILITY_FINANCE", "PAYMENT",
                "device-1", correlationId, facilityId, null, null, AccessMode.INTERNAL));
        mockMvc = MockMvcBuilders.standaloneSetup(new ServiceAccessDecisionController(decisionService))
                .setControllerAdvice(new ServiceAccessApiExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void postCreate_returnsCreatedEnvelope() throws Exception {
        ServiceAccessDecisionResponse dto = sampleResponse();
        when(decisionService.create(any(ServiceAccessDecisionCreateRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/costa/v1/service-access-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "access_status": "PAYMENT_REQUIRED_BEFORE_SERVICE",
                                  "encounter_id": "enc-1",
                                  "patient_cpid": "CPID-1",
                                  "billing_timing_mode": "PRE_SERVICE",
                                  "estimated_cost": 120.50
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.access_status").value("PAYMENT_REQUIRED_BEFORE_SERVICE"))
                .andExpect(jsonPath("$.data.encounter_id").value("enc-1"));

        verify(decisionService).create(any(ServiceAccessDecisionCreateRequest.class));
    }

    @Test
    void getList_returnsArray() throws Exception {
        when(decisionService.listRecent("enc-9")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/costa/v1/service-access-decisions").param("encounter_id", "enc-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].encounter_id").value("enc-1"));

        verify(decisionService).listRecent(eq("enc-9"));
    }

    @Test
    void getById_returnsOne() throws Exception {
        when(decisionService.require(decisionId)).thenReturn(sampleResponse());

        mockMvc.perform(get("/costa/v1/service-access-decisions/" + decisionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.service_access_decision_id").value(decisionId.toString()));
    }

    private ServiceAccessDecisionResponse sampleResponse() {
        return new ServiceAccessDecisionResponse(
                decisionId,
                tenantId,
                "CPID-1",
                "enc-1",
                facilityId,
                null,
                "SVC-1",
                "Consult",
                "OPD",
                new BigDecimal("120.50"),
                99L,
                new BigDecimal("120.50"),
                BillingTimingMode.PRE_SERVICE,
                ServiceAccessStatus.PAYMENT_REQUIRED_BEFORE_SERVICE,
                null,
                new BigDecimal("120.50"),
                null,
                null,
                null,
                null,
                null,
                null,
                "Policy",
                "actor-1",
                OffsetDateTime.parse("2026-04-11T10:15:30+02:00"),
                "AUD-1"
        );
    }
}
