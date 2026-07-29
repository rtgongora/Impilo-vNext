package zw.gov.mohcc.impilo.simba;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.simba.persistence.entity.CoachingRelationshipEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.PlanEnrollmentEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.PlanTaskEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.WellnessPlanEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.CoachingRelationshipRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.EnrollmentTaskRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.PlanEnrollmentRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.PlanTaskRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.WellnessPlanRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wellness DEPTH journey: plan enrolment + task instantiation + progress, habit check-ins,
 * coaching relationship + scoped touchpoint, and wellness-to-care routing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WellnessDepthJourneyTest {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final UUID TENANT_UUID = UUID.fromString(TENANT);
    private static final String CPID = "it-simba-depth-001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private WellnessPlanRepository planRepo;
    @Autowired private PlanTaskRepository planTaskRepo;
    @Autowired private PlanEnrollmentRepository enrollmentRepo;
    @Autowired private EnrollmentTaskRepository enrollmentTaskRepo;
    @Autowired private CoachingRelationshipRepository coachingRepo;

    private static MockHttpServletRequestBuilder trust(MockHttpServletRequestBuilder b) {
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-depth")
                .header("X-Correlation-ID", "corr-depth")
                .header("X-Actor-ID", CPID)
                .header("X-Actor-Type", "CITIZEN")
                .header("Idempotency-Key", "idem-" + UUID.randomUUID());
    }

    private UUID seedPlanWithTasks(boolean caution, int taskCount) {
        WellnessPlanEntity plan = new WellnessPlanEntity();
        plan.setTenantId(TENANT_UUID);
        plan.setPlanCode("IT-PLAN-" + UUID.randomUUID());
        plan.setName("IT Walking Journey");
        plan.setCategory("ACTIVITY");
        plan.setDurationDays(30);
        plan.setClinicalCaution(caution);
        plan = planRepo.save(plan);
        for (int i = 1; i <= taskCount; i++) {
            PlanTaskEntity t = new PlanTaskEntity();
            t.setTenantId(TENANT_UUID);
            t.setPlanId(plan.getPlanId());
            t.setStage(1);
            t.setSeq(i);
            t.setTitle("Task " + i);
            planTaskRepo.save(t);
        }
        return plan.getPlanId();
    }

    @Test
    void planEnrollmentInstantiatesTasksAndTracksProgress() throws Exception {
        UUID planId = seedPlanWithTasks(false, 2);

        // Plan catalogue visible
        mockMvc.perform(trust(get("/internal/v1/wellness/programs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.planId=='" + planId + "')]").exists());

        // Enroll
        MvcResult enroll = mockMvc.perform(trust(post("/internal/v1/wellness/enrollments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan_id\":\"" + planId + "\",\"person_cpid\":\"" + CPID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clinical_caution").value(false))
                .andReturn();
        JsonNode body = MAPPER.readTree(enroll.getResponse().getContentAsString());
        UUID enrollmentId = UUID.fromString(body.get("enrollment").get("enrollmentId").asText());

        // Two enrollment tasks instantiated from template
        assertThat(enrollmentTaskRepo.countByEnrollmentId(enrollmentId)).isEqualTo(2);
        var tasks = enrollmentTaskRepo.findByEnrollmentIdOrderByCreatedAtAsc(enrollmentId);

        // Complete one task → 50%
        mockMvc.perform(trust(post("/internal/v1/wellness/enrollments/tasks/"
                        + tasks.get(0).getEnrollmentTaskId() + "/complete")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPct").value(50));

        // Complete the second → 100% + COMPLETED
        mockMvc.perform(trust(post("/internal/v1/wellness/enrollments/tasks/"
                        + tasks.get(1).getEnrollmentTaskId() + "/complete")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPct").value(100))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        PlanEnrollmentEntity reloaded = enrollmentRepo
                .findByEnrollmentIdAndTenantId(enrollmentId, TENANT_UUID).orElseThrow();
        assertThat(reloaded.getProgressPct()).isEqualTo(100);
    }

    @Test
    void clinicalCautionPlanReturnsCarePrompt() throws Exception {
        UUID planId = seedPlanWithTasks(true, 1);
        mockMvc.perform(trust(post("/internal/v1/wellness/enrollments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan_id\":\"" + planId + "\",\"person_cpid\":\"" + CPID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clinical_caution").value(true))
                .andExpect(jsonPath("$.care_prompt").exists());
    }

    @Test
    void habitCheckInIsIdempotentPerDay() throws Exception {
        String payload = "{\"person_cpid\":\"" + CPID + "\",\"habit_code\":\"MOVE_10MIN\",\"check_in_date\":\"2026-06-30\"}";
        mockMvc.perform(trust(post("/internal/v1/wellness/habits/check-ins"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        // Second check-in same day updates, does not duplicate
        mockMvc.perform(trust(post("/internal/v1/wellness/habits/check-ins"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_cpid\":\"" + CPID + "\",\"habit_code\":\"MOVE_10MIN\",\"check_in_date\":\"2026-06-30\",\"note\":\"done\"}"))
                .andExpect(status().isCreated());

        MvcResult list = mockMvc.perform(trust(get("/internal/v1/wellness/habits/check-ins"))
                        .param("person_cpid", CPID))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = MAPPER.readTree(list.getResponse().getContentAsString());
        long forDay = 0;
        for (JsonNode n : arr) {
            if ("MOVE_10MIN".equals(n.get("habitCode").asText())
                    && "2026-06-30".equals(n.get("checkInDate").asText())) {
                forDay++;
            }
        }
        assertThat(forDay).isEqualTo(1);
    }

    @Test
    void coachingTouchpointDeniedWhenCoachNotAssigned() throws Exception {
        // Assign coach PROV-1 to the participant
        MvcResult rel = mockMvc.perform(trust(post("/internal/v1/wellness/coaching/relationships"))
                        .header("X-Provider-ID", "PROV-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_cpid\":\"" + CPID + "\",\"coach_provider_id\":\"PROV-1\",\"focus\":\"ACTIVITY\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID relationshipId = UUID.fromString(
                MAPPER.readTree(rel.getResponse().getContentAsString()).get("relationshipId").asText());

        // Assigned coach can add a touchpoint
        mockMvc.perform(trust(post("/internal/v1/wellness/coaching/relationships/" + relationshipId + "/touchpoints"))
                        .header("X-Provider-ID", "PROV-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"touchpoint_type\":\"CHECKIN\",\"note\":\"Great progress\"}"))
                .andExpect(status().isCreated());

        // A different provider is NOT assigned → forbidden
        mockMvc.perform(trust(post("/internal/v1/wellness/coaching/relationships/" + relationshipId + "/touchpoints"))
                        .header("X-Provider-ID", "PROV-OTHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"touchpoint_type\":\"NOTE\",\"note\":\"unauthorised\"}"))
                .andExpect(status().isForbidden());

        List<CoachingRelationshipEntity> forCoach =
                coachingRepo.findByTenantIdAndCoachProviderIdOrderByCreatedAtDesc(TENANT_UUID, "PROV-1");
        assertThat(forCoach).isNotEmpty();
    }

    @Test
    void wellnessToCareRoutingPersistsAndEmergencyForcesEmergencyOwner() throws Exception {
        mockMvc.perform(trust(post("/internal/v1/wellness/care-linkages"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_cpid\":\"" + CPID + "\",\"trigger\":\"RED_FLAG\",\"severity\":\"EMERGENCY\",\"reason\":\"chest pain\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetOwner").value("EMERGENCY"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(trust(get("/internal/v1/wellness/care-linkages"))
                        .param("person_cpid", CPID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trigger").value("RED_FLAG"));
    }
}
