package zw.gov.mohcc.impilo.pct;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueItemRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HONEST GAP TEST — virtual queues do not exist.
 *
 * <p>The telemedicine referral/teleconsult flow
 * ({@code TelemedicineOrchestrationService}, POST /v1/referrals) is entirely
 * disjoint from the physical queue engine: it creates a referral row and an
 * outbox event, but no journey and no {@code pct_queue_items} row. A remote
 * patient waiting for a teleconsult therefore has no token, no position, no
 * wait-time visibility, and never triggers QUEUE_CITIZEN_* notifications.</p>
 *
 * <p>This test pins the CURRENT behaviour so the gap stays visible and
 * deliberate. When virtual-queue support lands (teleconsult requests entering
 * a queue with WAITING/CALLED lifecycle), this test MUST fail and be replaced
 * by positive assertions on the virtual queue item.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VirtualQueueGapIT {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final UUID FACILITY = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private static final String CPID = "it-virtual-queue-gap-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueueItemRepository queueItemRepository;

    @Autowired
    private JourneyRepository journeyRepository;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withTrustHeaders(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-pct-virtual-gap")
                .header("X-Correlation-ID", "corr-pct-virtual-gap")
                .header("X-Actor-ID", "provider-it-001")
                .header("X-Actor-Type", "PROVIDER")
                .header("X-Purpose-Of-Use", "TREATMENT")
                .header("X-Facility-ID", FACILITY.toString());
    }

    @Test
    void virtualQueue_gapIsHonest_teleconsultRequestCreatesNoQueueItem() throws Exception {
        long queueItemsBefore = queueItemRepository.count();

        mockMvc.perform(withTrustHeaders(post("/v1/referrals"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": "%s",
                                  "reason": "Teleconsult requested for persistent rash",
                                  "specialty": "DERMATOLOGY",
                                  "urgency": "ROUTINE",
                                  "modality": "virtual",
                                  "virtual_mode": "video",
                                  "facilityId": "%s"
                                }
                                """.formatted(CPID, FACILITY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        // The gap: a teleconsult request produces NO queue placement at all.
        assertThat(queueItemRepository.count())
                .as("teleconsult referral must not (yet) create any pct_queue_items row")
                .isEqualTo(queueItemsBefore);
        assertThat(queueItemRepository.findAll())
                .noneMatch(item -> CPID.equals(item.getPatientCpid()));

        // No journey is started either — the remote patient is invisible to
        // the queue/journey lifecycle and its notifications.
        assertThat(journeyRepository.findByTenantIdAndPatientCpid(UUID.fromString(TENANT), CPID))
                .isEmpty();
    }
}
