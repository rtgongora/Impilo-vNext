package zw.gov.mohcc.impilo.pct;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueRepository;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PctQueueEncounterIT {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final UUID FACILITY = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private static final UUID QUEUE_ID = UUID.fromString("11111111-1111-1111-1111-111111111201");
    private static final String CPID = "it-pct-queue-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueueRepository queueRepository;

    @BeforeEach
    void seedQueue() {
        if (queueRepository.findById(QUEUE_ID).isEmpty()) {
            QueueEntity queue = new QueueEntity();
            queue.setQueueId(QUEUE_ID);
            queue.setTenantId(UUID.fromString(TENANT));
            queue.setFacilityId(FACILITY);
            queue.setName("IT OPD Queue");
            queue.setQueueType("FIFO");
            queue.setActive(true);
            queueRepository.save(queue);
        }
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withTrustHeaders(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-pct-trust")
                .header("X-Correlation-ID", "corr-pct-trust")
                .header("X-Actor-ID", "provider-it-001")
                .header("X-Actor-Type", "PROVIDER")
                .header("X-Purpose-Of-Use", "TREATMENT")
                .header("X-Facility-ID", FACILITY.toString());
    }

    @Test
    void journeyStart_enqueue_callNext_startEncounter_roundTrip() throws Exception {
        var journeyResult = mockMvc.perform(withTrustHeaders(post("/v1/journeys/start"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "facilityId": "%s",
                                  "patientCpid": "%s",
                                  "referralSource": "WALK_IN"
                                }
                                """.formatted(FACILITY, CPID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientCpid").value(CPID))
                .andReturn();

        String journeyBody = journeyResult.getResponse().getContentAsString();
        String journeyId = journeyBody.replaceAll(".*\"journeyId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(withTrustHeaders(post("/v1/queues/" + QUEUE_ID + "/enqueue"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "journeyId": "%s",
                                  "priority": 3
                                }
                                """.formatted(journeyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        mockMvc.perform(withTrustHeaders(post("/v1/queues/" + QUEUE_ID + "/call-next")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.journeyId").value(journeyId));

        mockMvc.perform(withTrustHeaders(post("/v1/journeys/" + journeyId + "/encounter/start"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "encounterType": "OUTPATIENT",
                                  "modality": "IN_PERSON"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.journeyId").value(journeyId))
                .andExpect(jsonPath("$.data.status").value("STARTED"));
    }
}
