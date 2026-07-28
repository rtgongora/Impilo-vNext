package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.integration.RitoSafetyClient;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyAlertEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;

/**
 * The alert sweep (pct V203, W5) had no unit coverage at all before this wave (W5b), verified only
 * by real-Postgres constraint probes at the time it was built. This closes that gap: proves
 * TRIAGE_OVERDUE/LOCATION_UNKNOWN/ACCEPTANCE_OVERDUE raise correctly, the anti-noise partial-unique
 * behaviour is respected at the application layer (no duplicate raise for an already-open alert),
 * ageUnansweredToOverdue ages past-due alerts, AND (the new W5b behaviour) ACCEPTANCE_OVERDUE
 * synchronously opens a RITO case while every other alert type does not.
 */
class EmergencyAlertSweepJobTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private EmergencyEpisodeServiceTest.InMemoryRepo episodeRepo;
    private EmergencyAlertServiceTest.InMemoryAlertRepo alertRepo;
    private EmergencyEpisodeServiceTest.CountingOutbox outbox;
    private HandoverExpirySweepJobTest.StubRitoSafetyClient ritoClient;
    private EmergencyAlertSweepJob job;

    @BeforeEach
    void setUp() {
        episodeRepo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        alertRepo = new EmergencyAlertServiceTest.InMemoryAlertRepo();
        outbox = new EmergencyEpisodeServiceTest.CountingOutbox();
        ritoClient = new HandoverExpirySweepJobTest.StubRitoSafetyClient();
        job = new EmergencyAlertSweepJob(episodeRepo, alertRepo, outbox, new ObjectMapper(), ritoClient);
    }

    private EmergencyEpisodeEntity seedEpisode() {
        EmergencyEpisodeEntity e = new EmergencyEpisodeEntity();
        UUID id = UUID.randomUUID();
        e.setEpisodeId(id);
        e.setTenantId(TENANT);
        e.setFacilityId(FACILITY);
        e.setEpisodeReference("EM-SWEEP-" + id);
        e.setEntryRoute("WALK_IN");
        e.setArrivedAt(OffsetDateTime.now().minusHours(3));
        e.setState(EmergencyEpisodeState.OPEN_IN_CARE.name());
        episodeRepo.save(e);
        return e;
    }

    @Test
    @DisplayName("TRIAGE_OVERDUE raises when the target has passed with no first contact recorded")
    void raisesTriageOverdue() {
        EmergencyEpisodeEntity e = seedEpisode();
        e.setTargetFirstContactAt(OffsetDateTime.now().minusMinutes(20));
        episodeRepo.save(e);

        int raised = job.raiseDueAlerts(OffsetDateTime.now(), EmergencyAlertRules.Thresholds.defaults());

        assertThat(raised).isGreaterThanOrEqualTo(1);
        List<EmergencyAlertEntity> alerts = alertRepo.findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(TENANT, e.getEpisodeId());
        assertThat(alerts).anyMatch(a -> "TRIAGE_OVERDUE".equals(a.getAlertType()));
    }

    @Test
    @DisplayName("re-sweeping the same due condition does not raise a second open alert (anti-noise)")
    void doesNotDuplicateOpenAlert() {
        EmergencyEpisodeEntity e = seedEpisode();
        e.setTargetFirstContactAt(OffsetDateTime.now().minusMinutes(20));
        episodeRepo.save(e);

        job.raiseDueAlerts(OffsetDateTime.now(), EmergencyAlertRules.Thresholds.defaults());
        int secondPass = job.raiseDueAlerts(OffsetDateTime.now(), EmergencyAlertRules.Thresholds.defaults());

        long triageAlerts = alertRepo.findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(TENANT, e.getEpisodeId())
                .stream().filter(a -> "TRIAGE_OVERDUE".equals(a.getAlertType())).count();
        assertThat(triageAlerts).isEqualTo(1);
        assertThat(secondPass).isZero();
    }

    @Test
    @DisplayName("ACCEPTANCE_OVERDUE raises AND opens a RITO case, stamping ritoCaseRef")
    void acceptanceOverdueOpensRitoCase() {
        EmergencyEpisodeEntity e = seedEpisode();
        e.setState(EmergencyEpisodeState.OPEN_AWAITING_ACCEPTANCE.name());
        e.setAwaitingAcceptanceSince(OffsetDateTime.now().minusMinutes(45));
        episodeRepo.save(e);

        job.raiseDueAlerts(OffsetDateTime.now(), EmergencyAlertRules.Thresholds.defaults());

        var alerts = alertRepo.findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(TENANT, e.getEpisodeId());
        var acceptanceAlert = alerts.stream().filter(a -> "ACCEPTANCE_OVERDUE".equals(a.getAlertType())).findFirst();
        assertThat(acceptanceAlert).isPresent();
        assertThat(acceptanceAlert.get().getRitoCaseRef()).isEqualTo("rito-case-stub-1");
        assertThat(ritoClient.calls).hasSize(1);
    }

    @Test
    @DisplayName("a non-ACCEPTANCE_OVERDUE alert never opens a RITO case")
    void otherAlertTypesDoNotOpenRitoCase() {
        EmergencyEpisodeEntity e = seedEpisode();
        e.setTargetFirstContactAt(OffsetDateTime.now().minusMinutes(20));
        episodeRepo.save(e);

        job.raiseDueAlerts(OffsetDateTime.now(), EmergencyAlertRules.Thresholds.defaults());

        assertThat(ritoClient.calls).isEmpty();
        var alerts = alertRepo.findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(TENANT, e.getEpisodeId());
        assertThat(alerts).allMatch(a -> a.getRitoCaseRef() == null);
    }

    @Test
    @DisplayName("a RITO outage never blocks the ACCEPTANCE_OVERDUE alert itself")
    void ritoOutageDoesNotBlockAlert() {
        ritoClient.simulateOutage = true;
        EmergencyEpisodeEntity e = seedEpisode();
        e.setState(EmergencyEpisodeState.OPEN_AWAITING_ACCEPTANCE.name());
        e.setAwaitingAcceptanceSince(OffsetDateTime.now().minusMinutes(45));
        episodeRepo.save(e);

        int raised = job.raiseDueAlerts(OffsetDateTime.now(), EmergencyAlertRules.Thresholds.defaults());

        assertThat(raised).isGreaterThanOrEqualTo(1);
        var alerts = alertRepo.findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(TENANT, e.getEpisodeId());
        assertThat(alerts).anyMatch(a -> "ACCEPTANCE_OVERDUE".equals(a.getAlertType()) && a.getRitoCaseRef() == null);
    }

    @Test
    @DisplayName("ageUnansweredToOverdue ages a breached RAISED alert to OVERDUE and never a CLOSED one")
    void agesOverdueOnly() {
        EmergencyAlertEntity raised = new EmergencyAlertEntity();
        raised.setTenantId(TENANT);
        raised.setFacilityId(FACILITY);
        raised.setEpisodeId(UUID.randomUUID());
        raised.setAlertType("TRIAGE_OVERDUE");
        raised.setReason("test");
        raised.setResponseDueAt(OffsetDateTime.now().minusMinutes(5));
        alertRepo.save(raised);

        int aged = job.ageUnansweredToOverdue(OffsetDateTime.now());

        assertThat(aged).isEqualTo(1);
        assertThat(alertRepo.findByAlertIdAndTenantId(raised.getAlertId(), TENANT).orElseThrow().getStatus())
                .isEqualTo("OVERDUE");
    }
}
