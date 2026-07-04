package zw.gov.mohcc.impilo.pct;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueItemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private JourneyRepository journeyRepository;

    @Autowired
    private QueueItemRepository queueItemRepository;

    @Autowired
    private EventOutboxRepository outboxRepository;

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

    /**
     * Queue → encounter-start handoff, end to end against the DB.
     *
     * <p>The handoff has two coupled legs: (1) the queue item is moved to
     * IN_SERVICE (the BFF "begin service" action → POST
     * /v1/queue-items/{id}/status), which transitions the journey to
     * IN_SERVICE inside QueueEngine; (2) the clinical encounter is started
     * against the journey. Note the seam: encounter start alone does NOT
     * flip the queue item — the queue-side transition is driven explicitly
     * by the status call, so this test exercises both legs and asserts the
     * queue item, journey, and outbox chain all agree at the end.</p>
     */
    @Test
    void queueHandoff_inServiceStatus_syncsJourney_andEncounterStartCompletesChain() throws Exception {
        String cpid = "it-pct-handoff-001";

        var journeyResult = mockMvc.perform(withTrustHeaders(post("/v1/journeys/start"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "facilityId": "%s",
                                  "patientCpid": "%s",
                                  "referralSource": "WALK_IN"
                                }
                                """.formatted(FACILITY, cpid)))
                .andExpect(status().isOk())
                .andReturn();
        String journeyId = journeyResult.getResponse().getContentAsString()
                .replaceAll(".*\"journeyId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(withTrustHeaders(post("/v1/queues/" + QUEUE_ID + "/enqueue"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "journeyId": "%s",
                                  "priority": 4
                                }
                                """.formatted(journeyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        UUID itemId = queueItemRepository
                .findByTenantIdAndJourneyId(UUID.fromString(TENANT), journeyId)
                .get(0).getItemId();

        mockMvc.perform(withTrustHeaders(post("/v1/queues/" + QUEUE_ID + "/call-next")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.journeyId").value(journeyId))
                .andExpect(jsonPath("$.data.status").value("CALLED"));

        // Leg 1: provider begins service — queue item IN_SERVICE, journey follows
        mockMvc.perform(withTrustHeaders(post("/v1/queue-items/" + itemId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_SERVICE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_SERVICE"));

        JourneyEntity journeyAfterStatus = journeyRepository.findByJourneyId(journeyId).orElseThrow();
        assertThat(journeyAfterStatus.getState()).isEqualTo(JourneyState.IN_SERVICE);

        // Leg 2: the encounter starts against the already-IN_SERVICE journey
        mockMvc.perform(withTrustHeaders(post("/v1/journeys/" + journeyId + "/encounter/start"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "encounterType": "OUTPATIENT",
                                  "modality": "IN_PERSON"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("STARTED"));

        // Final state: queue item and journey both IN_SERVICE
        QueueItemEntity item = queueItemRepository.findById(itemId).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(QueueItemStatus.IN_SERVICE);
        assertThat(item.getServiceStartedAt()).isNotNull();
        assertThat(journeyRepository.findByJourneyId(journeyId).orElseThrow().getState())
                .isEqualTo(JourneyState.IN_SERVICE);

        // Outbox chain: every hop of the handoff emitted its event
        List<EventOutboxEntity> events = outboxRepository.findAll().stream()
                .filter(e -> e.getPayload() != null && e.getPayload().contains(journeyId))
                .toList();
        assertThat(events)
                .extracting(EventOutboxEntity::getEventType)
                .contains("JOURNEY_CREATED", "QUEUE_ITEM_ENQUEUED", "QUEUE_ITEM_CALLED",
                        "QUEUE_ITEM_UPDATED", "ENCOUNTER_STARTED");
        assertThat(events)
                .anyMatch(e -> "QUEUE_ITEM_UPDATED".equals(e.getEventType())
                        && e.getPayload().contains("\"newStatus\":\"IN_SERVICE\"")
                        && e.getPayload().contains("\"tenantId\":\"" + TENANT + "\"")
                        && e.getPayload().contains("\"patientCpid\":\"" + cpid + "\""));
    }
}
