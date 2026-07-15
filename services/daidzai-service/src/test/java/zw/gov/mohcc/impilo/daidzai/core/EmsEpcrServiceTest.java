package zw.gov.mohcc.impilo.daidzai.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.daidzai.integration.PctPreArrivalClient;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsMissionEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EventOutboxRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Domain coverage for prehospital ePCR capture: the timed observation series persists, the compact
 * snapshot resolves latest vitals/GCS + interventions, ePCR binds to the trauma episode, and each
 * observation triggers the ED pre-arrival push.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmsEpcrServiceTest {

    @Autowired private EmsEpcrService epcr;
    @Autowired private EmsDispatchService ems;
    @Autowired private EmergencyService emergency;
    @Autowired private EventOutboxRepository outboxRepo;
    @MockBean private PctPreArrivalClient preArrival; // don't hit the network in a unit test

    private EmsMissionEntity dispatched(UUID tenant) {
        EmergencyIncidentEntity inc = emergency.escalateToIncident(tenant, "TRAUMA", "CRITICAL", "RTC",
                UUID.randomUUID(), "ANONYMOUS", null, null, null, "highway", "actor-1");
        return ems.dispatch(tenant, inc.getId(), "AMB-1", "ASSET-1", null, "CRITICAL");
    }

    @Test
    void ePcrTimeSeriesPersistsAndSnapshotResolves() {
        UUID tenant = UUID.randomUUID();
        EmsMissionEntity m = dispatched(tenant);

        epcr.upsertEpcr(tenant, m.getId(), "HID-PROV-1",
                "{\"airway\":\"patent\",\"breathing\":\"tachypnoeic\"}", "RTC, ejected driver");
        epcr.recordEvent(tenant, m.getId(), "VITALS", "OBS", "{\"hr\":120,\"sbp\":90,\"spo2\":88}");
        epcr.recordEvent(tenant, m.getId(), "GCS", "OBS", "{\"gcs\":9,\"e\":2,\"v\":3,\"m\":4}");
        epcr.recordEvent(tenant, m.getId(), "INTERVENTION", "TREAT", "{\"intervention\":\"IV access 18G\"}");
        epcr.recordEvent(tenant, m.getId(), "VITALS", "OBS", "{\"hr\":110,\"sbp\":100,\"spo2\":94}");

        Map<String, Object> view = epcr.getEpcr(tenant, m.getId());
        assertThat(view.get("traumaEpisodeId")).isEqualTo(m.getTraumaEpisodeId().toString());
        assertThat(view.get("patientHealthId")).isEqualTo("HID-PROV-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) view.get("events");
        assertThat(events).hasSize(4);
        assertThat(events).extracting(e -> e.get("eventType"))
                .containsExactly("VITALS", "GCS", "INTERVENTION", "VITALS"); // recorded_at order

        @SuppressWarnings("unchecked")
        Map<String, Object> snap = (Map<String, Object>) view.get("snapshot");
        assertThat(snap.get("obsCount")).isEqualTo(4);
        @SuppressWarnings("unchecked")
        Map<String, Object> latestVitals = (Map<String, Object>) snap.get("latestVitals");
        assertThat(latestVitals.get("sbp")).isEqualTo(100); // last VITALS wins
        assertThat(snap.get("latestGcs")).isNotNull();
        assertThat((List<?>) snap.get("interventions")).hasSize(1);

        // Each obs pushed a pre-arrival snapshot to the ED.
        verify(preArrival, atLeastOnce()).pushPreArrival(any(), any());
        // ePCR events landed in the outbox.
        assertThat(outboxRepo.findAll()).anyMatch(e -> e.getEventType().equals("daidzai.ems.epcr_event"));
    }

    @Test
    void recordingAnObsBeforeThePrimarySurveyAutoCreatesTheEpcr() {
        UUID tenant = UUID.randomUUID();
        EmsMissionEntity m = dispatched(tenant);
        epcr.recordEvent(tenant, m.getId(), "VITALS", "OBS", "{\"hr\":130}");
        Map<String, Object> view = epcr.getEpcr(tenant, m.getId());
        assertThat(view.get("epcrId")).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) view.get("events");
        assertThat(events).hasSize(1);
    }
}
