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
import zw.gov.mohcc.impilo.costa.api.dto.GrantWaiverRequest;
import zw.gov.mohcc.impilo.costa.api.dto.WaiverDecisionRequest;
import zw.gov.mohcc.impilo.costa.api.dto.WaiverResponse;
import zw.gov.mohcc.impilo.costa.domain.enums.WaiverStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.WaiverType;
import zw.gov.mohcc.impilo.costa.service.WaiverService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

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
class WaiverControllerTest {

    @Mock
    private WaiverService service;

    private MockMvc mockMvc;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID waiverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                tenantId, "clerk-1", "FACILITY_FINANCE", "PAYMENT",
                "dev", UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        mockMvc = MockMvcBuilders.standaloneSetup(new WaiverController(service))
                .setControllerAdvice(new ServiceAccessApiExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void grant_returnsCreated() throws Exception {
        when(service.grant(any(GrantWaiverRequest.class))).thenReturn(sample(WaiverStatus.GRANTED));

        mockMvc.perform(post("/costa/v1/waivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"waiver_type":"FULL","justification":"indigent","bill_id":"BILL-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("GRANTED"))
                .andExpect(jsonPath("$.data.waiver_type").value("FULL"));
    }

    @Test
    void grant_missingJustification_isBadRequest() throws Exception {
        mockMvc.perform(post("/costa/v1/waivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"waiver_type\":\"FULL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_filtersByStatus() throws Exception {
        when(service.list("GRANTED", null)).thenReturn(List.of(sample(WaiverStatus.GRANTED)));
        mockMvc.perform(get("/costa/v1/waivers").param("status", "GRANTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("GRANTED"));
        verify(service).list(eq("GRANTED"), eq(null));
    }

    @Test
    void approve_returnsApproved() throws Exception {
        when(service.approve(eq(waiverId), any())).thenReturn(sample(WaiverStatus.APPROVED));
        mockMvc.perform(post("/costa/v1/waivers/" + waiverId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void revoke_returnsRevoked() throws Exception {
        when(service.revoke(eq(waiverId), any(WaiverDecisionRequest.class)))
                .thenReturn(sample(WaiverStatus.REVOKED));
        mockMvc.perform(post("/costa/v1/waivers/" + waiverId + "/revoke")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"error\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    private WaiverResponse sample(WaiverStatus status) {
        return new WaiverResponse(
                waiverId, tenantId, "WVR-TEST", "CPID-1", "enc-1", "BILL-1", null, null,
                WaiverType.FULL, null, "USD", "HARDSHIP", "indigent", status,
                "clerk-1", OffsetDateTime.now(),
                status == WaiverStatus.APPROVED ? "officer-1" : null,
                status == WaiverStatus.APPROVED ? OffsetDateTime.now() : null,
                null,
                status == WaiverStatus.REVOKED ? "officer-1" : null,
                status == WaiverStatus.REVOKED ? OffsetDateTime.now() : null,
                null, "AUD-1", OffsetDateTime.now(), OffsetDateTime.now());
    }
}
