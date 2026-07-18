package zw.gov.mohcc.impilo.rito.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RitoCaseControllerWebTest {

    @Autowired private MockMvc mockMvc;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Mandatory trust headers for a staff (non-client) actor. */
    private MockHttpServletRequestBuilder staff(MockHttpServletRequestBuilder b, UUID tenant) {
        return b.header("X-Tenant-ID", tenant.toString())
                .header("X-Pod-ID", "pod-1")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", "operator-1")
                .header("X-Actor-Type", "OPERATOR");
    }

    /** Creates a case at a facility and returns its id. */
    private String createCaseAtFacility(UUID tenant, String facilityId, String subjectCpid) throws Exception {
        String body = "{\"caseType\":\"COMPLAINT\",\"title\":\"t\",\"description\":\"d\",\"severity\":\"MODERATE\","
                + "\"source\":\"WEB_PORTAL\",\"facilityId\":\"" + facilityId + "\","
                + "\"subjectCpid\":\"" + subjectCpid + "\"}";
        String json = mockMvc.perform(staff(post("/internal/v1/rito/cases"), tenant)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(json).get("id").asText();
    }

    @Test
    void facility_scope_obligation_narrows_staff_list_to_own_facility() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID facA = UUID.randomUUID();
        UUID facB = UUID.randomUUID();
        String idA = createCaseAtFacility(tenant, facA.toString(), "HID-A");
        String idB = createCaseAtFacility(tenant, facB.toString(), "HID-B");

        // Without the obligation, staff sees both facilities.
        mockMvc.perform(staff(get("/internal/v1/rito/cases"), tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + idA + "')]").exists())
                .andExpect(jsonPath("$[?(@.id=='" + idB + "')]").exists());

        // FACILITY_SCOPE + X-Facility-ID=facA -> only facA's case is visible.
        mockMvc.perform(staff(get("/internal/v1/rito/cases"), tenant)
                        .header("x-max-scope", "FACILITY_SCOPE")
                        .header("X-Facility-ID", facA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + idA + "')]").exists())
                .andExpect(jsonPath("$[?(@.id=='" + idB + "')]").doesNotExist());

        // FACILITY_SCOPE with no facility on the context -> deny exposure (empty).
        mockMvc.perform(staff(get("/internal/v1/rito/cases"), tenant)
                        .header("x-max-scope", "FACILITY_SCOPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void suppress_fields_obligation_redacts_case_identity_on_read() throws Exception {
        UUID tenant = UUID.randomUUID();
        String id = createCaseAtFacility(tenant, UUID.randomUUID().toString(), "CPID-SECRET");

        // No obligation -> subject identity visible.
        mockMvc.perform(staff(get("/internal/v1/rito/cases/" + id), tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectCpid").value("CPID-SECRET"));

        // suppressFields obligation -> subject identity removed from the representation.
        mockMvc.perform(staff(get("/internal/v1/rito/cases/" + id), tenant)
                        .header("x-suppress-fields", "subjectCpid,reporterActorId"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.subjectCpid").doesNotExist())
                .andExpect(jsonPath("$.reporterActorId").doesNotExist());
    }

    @Test
    void createComplimentThenListReturnsOk() throws Exception {
        UUID tenant = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/rito/cases")
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Pod-ID", "pod-1")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-ID", "citizen-1")
                        .header("X-Actor-Type", "CITIZEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseType\":\"COMPLIMENT\",\"title\":\"Great care\",\"source\":\"WEB_PORTAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseReference").exists())
                .andExpect(jsonPath("$.caseReference").value(org.hamcrest.Matchers.startsWith("RITO-C")));

        mockMvc.perform(get("/internal/v1/rito/cases")
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Pod-ID", "pod-1")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }
}
