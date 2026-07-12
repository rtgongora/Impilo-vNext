package zw.gov.mohcc.impilo.daidzai.api;

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
class EmergencyControllerWebTest {

    @Autowired private MockMvc mockMvc;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockHttpServletRequestBuilder citizen(MockHttpServletRequestBuilder b, UUID tenant) {
        return b.header("X-Tenant-ID", tenant.toString())
                .header("X-Pod-ID", "pod-1")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", "citizen-1")
                .header("X-Actor-Type", "CITIZEN")
                .header("Idempotency-Key", UUID.randomUUID().toString());
    }

    @Test
    void citizenSosThenTrackThenDispatch() throws Exception {
        UUID tenant = UUID.randomUUID();
        String sos = "{\"requesterType\":\"CITIZEN\",\"subjectIdentityMode\":\"KNOWN\","
                + "\"subjectHealthId\":\"HID-1\",\"emergencyCategory\":\"CARDIAC\",\"severity\":\"CRITICAL\","
                + "\"description\":\"chest pain\",\"lat\":-17.8,\"lng\":31.0,\"channel\":\"WEB\"}";
        String reqJson = mockMvc.perform(citizen(post("/internal/v1/daidzai/requests"), tenant)
                        .contentType(MediaType.APPLICATION_JSON).content(sos))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestReference").exists())
                .andReturn().getResponse().getContentAsString();
        String requestId = MAPPER.readTree(reqJson).get("id").asText();

        // track the request
        mockMvc.perform(citizen(get("/internal/v1/daidzai/requests/" + requestId), tenant))
                .andExpect(status().isOk());

        // triage -> incident
        String incJson = mockMvc.perform(citizen(post("/internal/v1/daidzai/requests/" + requestId + "/triage"), tenant))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.triageCategory").value("RED"))
                .andReturn().getResponse().getContentAsString();
        String incidentId = MAPPER.readTree(incJson).get("id").asText();

        // dispatch need + mission timeline
        mockMvc.perform(citizen(post("/internal/v1/daidzai/incidents/" + incidentId + "/dispatch"), tenant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"go\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(citizen(get("/internal/v1/daidzai/incidents/" + incidentId + "/missions"), tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DISPATCH_REQUESTED"));
    }

    @Test
    void publicAnonymousSosGatesDispatchUntilCallbackVerified() throws Exception {
        UUID tenant = UUID.randomUUID();
        String sos = "{\"requesterType\":\"PUBLIC_ANONYMOUS\",\"subjectIdentityMode\":\"ANONYMOUS\","
                + "\"emergencyCategory\":\"MEDICAL\",\"severity\":\"HIGH\",\"description\":\"collapsed\","
                + "\"channel\":\"PUBLIC_WEB\",\"callbackNumber\":\"+263771234567\"}";
        String reqJson = mockMvc.perform(citizen(post("/internal/v1/daidzai/requests"), tenant)
                        .contentType(MediaType.APPLICATION_JSON).content(sos))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_CALLBACK"))
                .andExpect(jsonPath("$.callbackVerified").value(false))
                .andReturn().getResponse().getContentAsString();
        String requestId = MAPPER.readTree(reqJson).get("id").asText();

        // Triage is refused before callback verification (PD-3 dispatch gate → 409).
        mockMvc.perform(citizen(post("/internal/v1/daidzai/requests/" + requestId + "/triage"), tenant))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CALLBACK_VERIFICATION_REQUIRED"));

        // Dispatcher verifies the callback (authenticated lane).
        mockMvc.perform(citizen(post("/internal/v1/daidzai/requests/" + requestId + "/verify-callback"), tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callbackVerified").value(true))
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        // Now triage succeeds — an incident is created.
        mockMvc.perform(citizen(post("/internal/v1/daidzai/requests/" + requestId + "/triage"), tenant))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentReference").exists());
    }

    @Test
    void missingCategoryIsBadRequest() throws Exception {
        UUID tenant = UUID.randomUUID();
        mockMvc.perform(citizen(post("/internal/v1/daidzai/requests"), tenant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"requesterType\":\"CITIZEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
