package zw.gov.mohcc.impilo.abis.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.abis.core.AbisTemplateService;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine.IdentificationCandidate;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine.VerificationDecision;
import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;
import zw.gov.mohcc.impilo.abis.core.BiometricModality;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API contract slice (standalone MockMvc — security is exercised by the OAuth2 resource-server
 * config at runtime, not in this slice):
 * enroll returns 201; verify surfaces the fail-closed UNAVAILABLE decision; identify REQUIRES
 * X-Identify-Reason ∈ {ENROLMENT, RECOVERY, DEDUPLICATION} and never returns a merge decision.
 */
class AbisControllerTest {

    private static final String TENANT = "00000000-0000-0000-0000-000000000001";
    private static final String PROBE_B64 = Base64.getEncoder().encodeToString("probe".getBytes());

    private AbisTemplateService templateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        templateService = mock(AbisTemplateService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AbisController(templateService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void enrollReturns201WithStoredTemplateMetadata() throws Exception {
        TemplateEntity saved = new TemplateEntity();
        saved.setId(11L);
        saved.setSubjectRef("subj-opaque-1");
        saved.setModality(BiometricModality.FINGERPRINT);
        saved.setPosition("LEFT_INDEX");
        saved.setVersion(1);
        when(templateService.enroll(eq(UUID.fromString(TENANT)), eq("subj-opaque-1"),
                eq(BiometricModality.FINGERPRINT), eq("LEFT_INDEX"), eq(87), eq("vendor-x-3.1"), any()))
                .thenReturn(new AbisTemplateService.EnrollOutcome(saved, null));

        mockMvc.perform(post("/v1/abis/templates")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"subj-opaque-1","modality":"FINGERPRINT",
                                 "position":"LEFT_INDEX","qualityScore":87,
                                 "algorithmVersion":"vendor-x-3.1","templateBase64":"%s"}
                                """.formatted(PROBE_B64)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.subjectRef").value("subj-opaque-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1))
                // No duplicate case opened → duplicateReview is null (not an auto-merge).
                .andExpect(jsonPath("$.duplicateReview").doesNotExist());
    }

    @Test
    void verifySurfacesFailClosedUnavailableDecision() throws Exception {
        when(templateService.verify(any(), any(), any(), any(), any(), any()))
                .thenReturn(new VerificationDecision("UNAVAILABLE", 0.0, "fail closed"));

        mockMvc.perform(post("/v1/abis/verify")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"subj-opaque-1","modality":"FINGERPRINT",
                                 "probeBase64":"%s"}
                                """.formatted(PROBE_B64)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.confidence").value(0.0));
    }

    @Test
    void identifyWithoutReasonHeaderIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/v1/abis/identify")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modality":"FINGERPRINT","probeBase64":"%s"}
                                """.formatted(PROBE_B64)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("IDENTIFY_REASON_REQUIRED"));

        verify(templateService, never()).identify(any(), any(), any(), any(), any());
    }

    @Test
    void identifyWithDisallowedReasonIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/v1/abis/identify")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Identify-Reason", "SURVEILLANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modality":"FINGERPRINT","probeBase64":"%s"}
                                """.formatted(PROBE_B64)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("IDENTIFY_REASON_REQUIRED"));

        verify(templateService, never()).identify(any(), any(), any(), any(), any());
    }

    @Test
    void identifyReturnsCandidatesForAdjudicationNeverAMerge() throws Exception {
        when(templateService.identify(any(), any(), any(), eq("DEDUPLICATION"), any()))
                .thenReturn(List.of(new IdentificationCandidate("subj-opaque-2", 0.91, "vendor match")));

        mockMvc.perform(post("/v1/abis/identify")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Identify-Reason", "DEDUPLICATION")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modality":"FINGERPRINT","probeBase64":"%s"}
                                """.formatted(PROBE_B64)))
                .andExpect(status().isOk())
                // The decision is ALWAYS adjudication — a merge decision never appears in this API.
                .andExpect(jsonPath("$.decision").value("CANDIDATES_FOR_ADJUDICATION"))
                .andExpect(jsonPath("$.candidates", hasSize(1)))
                .andExpect(jsonPath("$.candidates[0].subjectRef").value("subj-opaque-2"))
                // Candidates are opaque subject refs + confidence only — no merge/heal fields.
                .andExpect(jsonPath("$.candidates[0].merge").doesNotExist())
                .andExpect(jsonPath("$.mergedInto").doesNotExist());
    }
}
