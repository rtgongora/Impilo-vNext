package zw.gov.mohcc.impilo.pct;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
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

/**
 * Integration test for appointment provenance on PCT journeys (V031).
 *
 * <p>A journey started from a booking-service check-in carries the source
 * {@code appointmentId}; the value must be persisted on
 * {@code pct_journeys.appointment_id}, surfaced in the JOURNEY_CREATED
 * outbox event, and the journey must remain fully queueable (token
 * assignment, WAITING status) exactly like a walk-in.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentProvenanceJourneyIT {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final UUID FACILITY = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private static final UUID QUEUE_ID = UUID.fromString("11111111-1111-1111-1111-111111111301");
    private static final String CPID = "it-appt-prov-001";
    private static final UUID APPOINTMENT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0301");

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
            queue.setName("IT Appointment Provenance Queue");
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
                .header("X-Request-ID", "req-pct-appt-prov")
                .header("X-Correlation-ID", "corr-pct-appt-prov")
                .header("X-Actor-ID", "provider-it-001")
                .header("X-Actor-Type", "PROVIDER")
                .header("X-Purpose-Of-Use", "TREATMENT")
                .header("X-Facility-ID", FACILITY.toString());
    }

    @Test
    void journeyStartedWithAppointmentId_persistsProvenance_andEnqueuesWithToken() throws Exception {
        var journeyResult = mockMvc.perform(withTrustHeaders(post("/v1/journeys/start"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "facilityId": "%s",
                                  "patientCpid": "%s",
                                  "referralSource": "SCHEDULED",
                                  "appointmentId": "%s"
                                }
                                """.formatted(FACILITY, CPID, APPOINTMENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientCpid").value(CPID))
                .andExpect(jsonPath("$.data.appointmentId").value(APPOINTMENT_ID.toString()))
                .andReturn();

        String journeyBody = journeyResult.getResponse().getContentAsString();
        String journeyId = journeyBody.replaceAll(".*\"journeyId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        // DB proof: pct_journeys.appointment_id (V031) is persisted, not just echoed
        JourneyEntity persisted = journeyRepository.findByJourneyId(journeyId).orElseThrow();
        assertThat(persisted.getAppointmentId()).isEqualTo(APPOINTMENT_ID);
        assertThat(persisted.getPatientCpid()).isEqualTo(CPID);

        // The JOURNEY_CREATED outbox event carries the appointment provenance downstream
        assertThat(outboxRepository.findAll())
                .anyMatch(e -> "JOURNEY_CREATED".equals(e.getEventType())
                        && journeyId.equals(e.getAggregateId())
                        && e.getPayload().contains("\"appointmentId\":\"" + APPOINTMENT_ID + "\""));

        // A scheduled journey is queueable exactly like a walk-in
        mockMvc.perform(withTrustHeaders(post("/v1/queues/" + QUEUE_ID + "/enqueue"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "journeyId": "%s",
                                  "priority": 3
                                }
                                """.formatted(journeyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.tokenNumber").isNumber());

        List<QueueItemEntity> items = queueItemRepository
                .findByTenantIdAndJourneyId(UUID.fromString(TENANT), journeyId);
        assertThat(items).hasSize(1);
        QueueItemEntity item = items.get(0);
        assertThat(item.getStatus()).isEqualTo(QueueItemStatus.WAITING);
        assertThat(item.getTokenNumber()).isNotNull().isPositive();
        assertThat(item.getPatientCpid()).isEqualTo(CPID);
    }

    @Test
    void walkInJourneyWithoutAppointmentId_persistsNullProvenance() throws Exception {
        var journeyResult = mockMvc.perform(withTrustHeaders(post("/v1/journeys/start"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "facilityId": "%s",
                                  "patientCpid": "it-appt-prov-walkin-001",
                                  "referralSource": "WALK_IN"
                                }
                                """.formatted(FACILITY)))
                .andExpect(status().isOk())
                .andReturn();

        String journeyId = journeyResult.getResponse().getContentAsString()
                .replaceAll(".*\"journeyId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        JourneyEntity persisted = journeyRepository.findByJourneyId(journeyId).orElseThrow();
        assertThat(persisted.getAppointmentId()).isNull();
    }
}
