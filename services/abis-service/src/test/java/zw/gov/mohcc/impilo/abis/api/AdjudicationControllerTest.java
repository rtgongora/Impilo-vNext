package zw.gov.mohcc.impilo.abis.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.abis.core.AdjudicationService;
import zw.gov.mohcc.impilo.abis.persistence.entity.AdjudicationCaseEntity;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adjudication console API contract (standalone MockMvc): the queue lists, a reviewer
 * assigns + decides, and a merge is dual-control — a same-reviewer or non-confirmed merge
 * is rejected. Never an automatic merge.
 */
class AdjudicationControllerTest {

    private static final String TENANT = "00000000-0000-0000-0000-000000000001";

    private AdjudicationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AdjudicationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdjudicationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AdjudicationCaseEntity kase(String status) {
        AdjudicationCaseEntity c = new AdjudicationCaseEntity();
        c.setId(101L);
        c.setTenantId(UUID.fromString(TENANT));
        c.setSubjectRef("probe-subj");
        c.setModality("FINGERPRINT");
        c.setReason("ENROLMENT");
        c.setStatus(status);
        c.setCandidateCount(1);
        return c;
    }

    @Test
    void listsTheQueue() throws Exception {
        when(service.listQueue(eq(UUID.fromString(TENANT)), eq("OPEN"), any()))
                .thenReturn(new PageImpl<>(List.of(kase("OPEN"))));

        mockMvc.perform(get("/v1/abis/adjudication/cases")
                        .header("X-Tenant-ID", TENANT)
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void recordDecisionForwardsActorAsReviewer() throws Exception {
        when(service.recordDecision(eq(UUID.fromString(TENANT)), eq(101L),
                eq("CONFIRMED_DUPLICATE"), eq("reviewer-a"), any()))
                .thenReturn(kase("RESOLVED"));

        mockMvc.perform(post("/v1/abis/adjudication/cases/101/decision")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Actor-ID", "reviewer-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRMED_DUPLICATE\",\"reason\":\"clear dup\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        verify(service).recordDecision(eq(UUID.fromString(TENANT)), eq(101L),
                eq("CONFIRMED_DUPLICATE"), eq("reviewer-a"), any());
    }

    @Test
    void mergeDualControlViolationSurfacesAsConflict() throws Exception {
        when(service.merge(eq(UUID.fromString(TENANT)), eq(101L), eq("cand-a"), eq("reviewer-a")))
                .thenThrow(new IllegalStateException(
                        "dual control violated: the merge reviewer must differ from the decision reviewer"));

        mockMvc.perform(post("/v1/abis/adjudication/cases/101/merge")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Actor-ID", "reviewer-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mergeTargetSubjectRef\":\"cand-a\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void mergeForwardsActorAsSecondReviewer() throws Exception {
        when(service.merge(eq(UUID.fromString(TENANT)), eq(101L), eq("cand-a"), eq("reviewer-b")))
                .thenReturn(kase("RESOLVED"));

        mockMvc.perform(post("/v1/abis/adjudication/cases/101/merge")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Actor-ID", "reviewer-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mergeTargetSubjectRef\":\"cand-a\"}"))
                .andExpect(status().isOk());

        verify(service).merge(eq(UUID.fromString(TENANT)), eq(101L), eq("cand-a"), eq("reviewer-b"));
    }

    @Test
    void mergeWithoutActorHeaderPassesNullSecondReviewer() throws Exception {
        // No X-Actor-ID → service receives null and enforces dual-control fail-closed.
        when(service.merge(eq(UUID.fromString(TENANT)), eq(101L), eq("cand-a"), isNull()))
                .thenThrow(new IllegalArgumentException(
                        "a second reviewer (X-Actor-ID) is required — merge is dual-control"));

        mockMvc.perform(post("/v1/abis/adjudication/cases/101/merge")
                        .header("X-Tenant-ID", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mergeTargetSubjectRef\":\"cand-a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));

        verify(service, never()).recordDecision(any(), any(), any(), any(), any());
    }
}
