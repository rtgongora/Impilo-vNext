package zw.gov.mohcc.impilo.daidzai.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.daidzai.integration.PctPreArrivalClient;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsMissionEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmsMissionRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EventOutboxRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain coverage for EMS clinical dispatch: idempotent dispatch, the validated state-machine walk
 * to HANDOVER, outbox emission, episode phase stamping, and illegal-transition rejection.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmsDispatchServiceTest {

    @Autowired private EmsDispatchService ems;
    @Autowired private EmergencyService emergency;
    @Autowired private TraumaEpisodeService episodes;
    @Autowired private EmsMissionRepository missionRepo;
    @Autowired private EventOutboxRepository outboxRepo;
    @MockBean private PctPreArrivalClient pctPreArrival; // don't hit the network on HANDOVER

    private EmergencyIncidentEntity newIncident(UUID tenant) {
        return emergency.escalateToIncident(tenant, "TRAUMA", "CRITICAL", "RTC",
                UUID.randomUUID(), "ANONYMOUS", null, null, null, "highway", "actor-1");
    }

    @Test
    void dispatchIsIdempotentPerIncident() {
        UUID tenant = UUID.randomUUID();
        EmergencyIncidentEntity inc = newIncident(tenant);

        EmsMissionEntity first = ems.dispatch(tenant, inc.getId(), "AMB-1", "ASSET-9", null, "CRITICAL");
        EmsMissionEntity again = ems.dispatch(tenant, inc.getId(), "AMB-2", "ASSET-2", null, "CRITICAL");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(missionRepo.findByTenantIdAndIncidentId(tenant, inc.getId())).isPresent();
        assertThat(first.getState()).isEqualTo("DISPATCHED");
        assertThat(first.getTraumaEpisodeId()).isEqualTo(inc.getTraumaEpisodeId());
        assertThat(first.getMissionReference()).startsWith("EMS-");
    }

    @Test
    void stateMachineWalksToHandoverAndEmitsEmsEvents() {
        UUID tenant = UUID.randomUUID();
        EmergencyIncidentEntity inc = newIncident(tenant);
        EmsMissionEntity m = ems.dispatch(tenant, inc.getId(), "AMB-7", "ASSET-7", null, "HIGH");

        String[] walk = {"ACKNOWLEDGED", "ACCEPTED", "EN_ROUTE_SCENE", "ON_SCENE",
                "PATIENT_CONTACT", "DEPARTED_SCENE", "EN_ROUTE_FACILITY", "ARRIVED_FACILITY", "HANDOVER"};
        for (String s : walk) {
            m = ems.advance(tenant, m.getId(), s, null, "HANDOVER".equals(s) ? "PCT-ENC-1" : null);
        }
        final EmsMissionEntity mission = m;
        assertThat(mission.getState()).isEqualTo("HANDOVER");
        assertThat(mission.getHandoverAt()).isNotNull();
        assertThat(mission.getOnSceneAt()).isNotNull();
        assertThat(mission.getPctEncounterRef()).isEqualTo("PCT-ENC-1");

        // Every transition wrote a daidzai.ems.* outbox row.
        long emsEvents = outboxRepo.findAll().stream()
                .filter(e -> e.getEventType().startsWith("daidzai.ems.")
                        && e.getAggregateId().equals(mission.getId().toString()))
                .count();
        assertThat(emsEvents).isGreaterThanOrEqualTo(walk.length); // + mission_created + dispatched
        assertThat(outboxRepo.findAll()).anyMatch(e -> e.getEventType().equals("daidzai.ems.handover"));

        // The trauma-episode timeline carries the DISPATCH + PREHOSPITAL phases from the mission.
        var view = episodes.episodeView(tenant, inc.getTraumaEpisodeId());
        @SuppressWarnings("unchecked")
        var timeline = (java.util.List<java.util.Map<String, Object>>) view.get("timeline");
        assertThat(timeline).anyMatch(p -> "DISPATCH".equals(p.get("phase")));
        assertThat(timeline).anyMatch(p -> "PREHOSPITAL".equals(p.get("phase")));
    }

    @Test
    void illegalTransitionIsRejected() {
        UUID tenant = UUID.randomUUID();
        EmergencyIncidentEntity inc = newIncident(tenant);
        EmsMissionEntity m = ems.dispatch(tenant, inc.getId(), "AMB-3", null, null, "HIGH");
        // DISPATCHED -> HANDOVER skips the whole prehospital sequence.
        assertThatThrownBy(() -> ems.advance(tenant, m.getId(), "HANDOVER", null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reassertingCurrentStateIsANoOp() {
        UUID tenant = UUID.randomUUID();
        EmergencyIncidentEntity inc = newIncident(tenant);
        EmsMissionEntity m = ems.dispatch(tenant, inc.getId(), "AMB-4", null, null, "HIGH");
        EmsMissionEntity same = ems.advance(tenant, m.getId(), "DISPATCHED", null, null);
        assertThat(same.getState()).isEqualTo("DISPATCHED");
    }
}
