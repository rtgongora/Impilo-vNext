package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.integration.DaidzaiEpisodeClient;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;

/**
 * The reconciliation pass {@link EmergencyAlertSweepJob}'s own class comment cites but never
 * performs: "the reconciler that computes divergence against peers is wired separately and calls
 * raiseAlert directly." Proves the three genuine outcomes — peer agrees (no alert), peer disagrees
 * (CORRELATION_DIVERGENCE raised), peer unreachable (no conclusion, never treated as agreement) —
 * plus that an uncorrelated episode (no traumaEpisodeId) is skipped entirely, since "never linked" is
 * DAIDZAI's own unanchored sweep's concern, not this reconciler's.
 */
class EmergencyReconciliationJobTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private EmergencyEpisodeServiceTest.InMemoryRepo episodeRepo;
    private EmergencyAlertServiceTest.InMemoryAlertRepo alertRepo;
    private StubDaidzaiEpisodeClient daidzaiClient;
    private EmergencyReconciliationJob job;

    @BeforeEach
    void setUp() {
        episodeRepo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        alertRepo = new EmergencyAlertServiceTest.InMemoryAlertRepo();
        var outbox = new EmergencyEpisodeServiceTest.CountingOutbox();
        var ritoClient = new HandoverExpirySweepJobTest.StubRitoSafetyClient();
        var alertSweepJob = new EmergencyAlertSweepJob(episodeRepo, alertRepo, outbox, new ObjectMapper(), ritoClient);
        daidzaiClient = new StubDaidzaiEpisodeClient();
        job = new EmergencyReconciliationJob(episodeRepo, daidzaiClient, alertSweepJob);
    }

    private EmergencyEpisodeEntity seedCorrelatedEpisode(UUID traumaEpisodeId) {
        EmergencyEpisodeEntity e = new EmergencyEpisodeEntity();
        UUID id = UUID.randomUUID();
        e.setEpisodeId(id);
        e.setTenantId(TENANT);
        e.setFacilityId(FACILITY);
        e.setEpisodeReference("EM-RECON-" + id);
        e.setEntryRoute("AMBULANCE");
        e.setArrivedAt(OffsetDateTime.now());
        e.setState(EmergencyEpisodeState.OPEN_IN_CARE.name());
        e.setTraumaEpisodeId(traumaEpisodeId);
        episodeRepo.save(e);
        return e;
    }

    @Test
    @DisplayName("an uncorrelated episode (no traumaEpisodeId) is skipped — never linked is DAIDZAI's unanchored sweep's concern")
    void uncorrelatedEpisodeSkipped() {
        EmergencyEpisodeEntity e = new EmergencyEpisodeEntity();
        e.setEpisodeId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setFacilityId(FACILITY);
        e.setEpisodeReference("EM-RECON-WALKIN");
        e.setEntryRoute("WALK_IN");
        e.setArrivedAt(OffsetDateTime.now());
        e.setState(EmergencyEpisodeState.OPEN_IN_CARE.name());
        episodeRepo.save(e);

        int diverged = job.reconcileAll(OffsetDateTime.now());

        assertThat(diverged).isZero();
        assertThat(daidzaiClient.calls).isEmpty();
    }

    @Test
    @DisplayName("when the peer agrees (same pctEmergencyEpisodeId, no mergedIntoId), no alert is raised")
    void peerAgreesNoAlert() {
        UUID traumaId = UUID.randomUUID();
        EmergencyEpisodeEntity e = seedCorrelatedEpisode(traumaId);
        daidzaiClient.viewFor(traumaId, agreeingView(e.getEpisodeId()));

        int diverged = job.reconcileAll(OffsetDateTime.now());

        assertThat(diverged).isZero();
        assertThat(alertRepo.findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(TENANT, e.getEpisodeId())).isEmpty();
    }

    @Test
    @DisplayName("when the peer is anchored to a DIFFERENT PCT episode, CORRELATION_DIVERGENCE is raised")
    void peerAnchoredElsewhereRaisesDivergence() {
        UUID traumaId = UUID.randomUUID();
        EmergencyEpisodeEntity e = seedCorrelatedEpisode(traumaId);
        Map<String, Object> view = agreeingView(UUID.randomUUID()); // a DIFFERENT episode id
        daidzaiClient.viewFor(traumaId, view);

        int diverged = job.reconcileAll(OffsetDateTime.now());

        assertThat(diverged).isEqualTo(1);
        var alerts = alertRepo.findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(TENANT, e.getEpisodeId());
        assertThat(alerts).anyMatch(a -> "CORRELATION_DIVERGENCE".equals(a.getAlertType()));
    }

    @Test
    @DisplayName("when the peer has been merged into another trauma episode, CORRELATION_DIVERGENCE is raised")
    void peerMergedRaisesDivergence() {
        UUID traumaId = UUID.randomUUID();
        EmergencyEpisodeEntity e = seedCorrelatedEpisode(traumaId);
        Map<String, Object> view = agreeingView(e.getEpisodeId());
        view.put("mergedIntoId", UUID.randomUUID().toString());
        daidzaiClient.viewFor(traumaId, view);

        int diverged = job.reconcileAll(OffsetDateTime.now());

        assertThat(diverged).isEqualTo(1);
    }

    @Test
    @DisplayName("when DAIDZAI is unreachable, no conclusion is drawn — absence is never divergence")
    void unreachablePeerDrawsNoConclusion() {
        UUID traumaId = UUID.randomUUID();
        EmergencyEpisodeEntity e = seedCorrelatedEpisode(traumaId);
        daidzaiClient.simulateUnreachable = true;

        int diverged = job.reconcileAll(OffsetDateTime.now());

        assertThat(diverged).isZero();
        assertThat(alertRepo.findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(TENANT, e.getEpisodeId())).isEmpty();
    }

    @Test
    @DisplayName("one episode's failure does not stop reconciliation for the others")
    void perEpisodeIsolation() {
        UUID trauma1 = UUID.randomUUID();
        EmergencyEpisodeEntity e1 = seedCorrelatedEpisode(trauma1);
        daidzaiClient.throwOnQuery(trauma1); // simulates an unexpected runtime error, not a clean "unreachable"

        UUID trauma2 = UUID.randomUUID();
        EmergencyEpisodeEntity e2 = seedCorrelatedEpisode(trauma2);
        Map<String, Object> view2 = agreeingView(UUID.randomUUID());
        daidzaiClient.viewFor(trauma2, view2);

        int diverged = job.reconcileAll(OffsetDateTime.now());

        assertThat(diverged).isEqualTo(1);
        assertThat(alertRepo.findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(TENANT, e2.getEpisodeId()))
                .anyMatch(a -> "CORRELATION_DIVERGENCE".equals(a.getAlertType()));
    }

    private static Map<String, Object> agreeingView(UUID pctEpisodeId) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("pctEmergencyEpisodeId", pctEpisodeId.toString());
        view.put("mergedIntoId", null);
        return view;
    }

    /** A subclass overriding the read, rather than a real client pointed at an unreachable URL. */
    static final class StubDaidzaiEpisodeClient extends DaidzaiEpisodeClient {
        final List<UUID> calls = new java.util.ArrayList<>();
        private final Map<UUID, Map<String, Object>> views = new LinkedHashMap<>();
        private final java.util.Set<UUID> throwsFor = new java.util.HashSet<>();
        boolean simulateUnreachable = false;

        StubDaidzaiEpisodeClient() {
            super(null, "http://unused");
        }

        void viewFor(UUID traumaEpisodeId, Map<String, Object> view) {
            views.put(traumaEpisodeId, view);
        }

        void throwOnQuery(UUID traumaEpisodeId) {
            throwsFor.add(traumaEpisodeId);
        }

        @Override
        public Map<String, Object> getTraumaEpisode(UUID tenantId, UUID traumaEpisodeId) {
            calls.add(traumaEpisodeId);
            if (throwsFor.contains(traumaEpisodeId)) {
                throw new RuntimeException("simulated peer read failure");
            }
            if (simulateUnreachable) return null;
            return views.get(traumaEpisodeId);
        }
    }
}
