package zw.gov.mohcc.impilo.costa.api.access;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WaiverControllerTest {

    @Mock
    private WaiverService service;

    private MockMvc mockMvc;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID waiverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        callerIs("clerk-1", "OPERATOR");
        mockMvc = MockMvcBuilders.standaloneSetup(new WaiverController(service))
                .setControllerAdvice(new ServiceAccessApiExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    /**
     * This used to read {@code "FACILITY_FINANCE"}, and that is worth recording rather than quietly
     * editing. {@code FACILITY_FINANCE} is not an actor type any client emits: the wire vocabulary
     * is {@code PROVIDER|OPERATOR|CITIZEN|SYSTEM} from {@code ui/shared-ui/lib/contracts.ts}, plus
     * {@code CAREGIVER} and {@code SERVICE} from the {@code ActorType} enum. So the test was
     * exercising the controller with a value no real request can carry, and would have kept passing
     * against a guard that no real finance clerk could satisfy. {@code OPERATOR} is what the admin
     * consoles and experience-bff's session resolution actually emit for facility admins and
     * finance. See {@code ActorTypeGuard} for the full measurement.
     */
    private void callerIs(String actorId, String actorType) {
        TrustContextHolder.set(new TrustContext(
                tenantId, actorId, actorType, "PAYMENT",
                "dev", UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
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

    // ── Authorization ──
    //
    // Until this change these four endpoints had no authorization at all. The class javadoc claimed
    // "Authz enforced upstream by Envoy ext_authz → TSHEPO"; Envoy routes only to experience-bff and
    // has no route to this service, so the claimed control did not exist on this path.

    @ParameterizedTest
    @ValueSource(strings = {"CITIZEN", "PROVIDER", "CAREGIVER"})
    void thePatientWhoseBillItIsCannotWaiveIt(String actorType) throws Exception {
        callerIs("someone", actorType);

        mockMvc.perform(post("/costa/v1/waivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"waiver_type":"FULL","justification":"indigent","bill_id":"BILL-1"}
                                """))
                .andExpect(status().isForbidden());

        verify(service, never()).grant(any(GrantWaiverRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CITIZEN", "PROVIDER", "CAREGIVER"})
    void aPersonalOrClinicalSessionCannotApproveAWaiver(String actorType) throws Exception {
        callerIs("someone", actorType);

        mockMvc.perform(post("/costa/v1/waivers/" + waiverId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(service, never()).approve(any(), any());
    }

    @Test
    void aPersonalSessionCannotRejectAWaiver() throws Exception {
        callerIs("someone", "CITIZEN");

        mockMvc.perform(post("/costa/v1/waivers/" + waiverId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(service, never()).reject(any(), any());
    }

    @Test
    void aPersonalSessionCannotRevokeAWaiver() throws Exception {
        callerIs("someone", "CITIZEN");

        mockMvc.perform(post("/costa/v1/waivers/" + waiverId + "/revoke")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"error\"}"))
                .andExpect(status().isForbidden());

        verify(service, never()).revoke(any(), any());
    }

    /**
     * Checked before the waiver is loaded, so a refused caller cannot use the endpoint to learn
     * which waiver ids exist.
     */
    @Test
    void theRefusalDoesNotDependOnWhetherTheWaiverExists() throws Exception {
        callerIs("someone", "CITIZEN");

        mockMvc.perform(post("/costa/v1/waivers/" + UUID.randomUUID() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(service, never()).require(any());
    }

    @Test
    void aRefusalNamesWhatIsRequiredSoALegitimateOperatorKnowsWhereToLook() throws Exception {
        callerIs("finance-person", "FACILITY_FINANCE");

        mockMvc.perform(post("/costa/v1/waivers/" + waiverId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(result -> {
                    String message = result.getResolvedException().getMessage();
                    org.assertj.core.api.Assertions.assertThat(message)
                            .contains("OPERATOR")
                            .contains("'FACILITY_FINANCE'");
                });
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
