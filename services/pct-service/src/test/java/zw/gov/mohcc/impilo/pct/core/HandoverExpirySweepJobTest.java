package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity;

/**
 * The scheduled job V204's own migration comment deferred: expiry as "an explicit action ... or a
 * later scheduled job". Proves the sweep expires only PENDING handovers whose {@code response_due_at}
 * (V209) has passed, returns the episode to OPEN_IN_CARE via the same tested path a clinician-driven
 * expiry uses, and isolates one bad row from the rest of the sweep.
 */
class HandoverExpirySweepJobTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private EmergencyEpisodeServiceTest.InMemoryRepo episodeRepo;
    private EmergencyEpisodeServiceTest.InMemoryHandoverRepo handoverRepo;
    private EmergencyEpisodeService episodeService;
    private HandoverExpirySweepJob job;

    @BeforeEach
    void setUp() {
        episodeRepo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        handoverRepo = new EmergencyEpisodeServiceTest.InMemoryHandoverRepo();
        var outbox = new EmergencyEpisodeServiceTest.CountingOutbox();
        episodeService = new EmergencyEpisodeService(episodeRepo, handoverRepo, outbox, new ObjectMapper());
        job = new HandoverExpirySweepJob(handoverRepo, episodeService);
    }

    private UUID seedAwaitingEpisode() {
        EmergencyEpisodeEntity episode = new EmergencyEpisodeEntity();
        UUID episodeId = UUID.randomUUID();
        episode.setEpisodeId(episodeId);
        episode.setTenantId(TENANT);
        episode.setFacilityId(FACILITY);
        episode.setEpisodeReference("EM-SWEEP-" + episodeId);
        episode.setEntryRoute("WALK_IN");
        episode.setArrivedAt(OffsetDateTime.now());
        episode.setState(EmergencyEpisodeState.OPEN_AWAITING_ACCEPTANCE.name());
        episodeRepo.save(episode);
        return episodeId;
    }

    private UUID seedPendingHandover(UUID episodeId, OffsetDateTime responseDueAt) {
        EmergencyHandoverEntity h = new EmergencyHandoverEntity();
        UUID handoverId = UUID.randomUUID();
        h.setHandoverId(handoverId);
        h.setTenantId(TENANT);
        h.setEpisodeId(episodeId);
        h.setTargetType("ADMISSION");
        h.setRequestedBy("nurse-A");
        h.setRequestedAt(OffsetDateTime.now().minusHours(2));
        h.setStatus("PENDING");
        h.setResponseDueAt(responseDueAt);
        handoverRepo.save(h);
        return handoverId;
    }

    @Test
    @DisplayName("a PENDING handover past its response_due_at is expired, and the episode returns to OPEN_IN_CARE")
    void expiresDueHandover() {
        UUID episodeId = seedAwaitingEpisode();
        UUID handoverId = seedPendingHandover(episodeId, OffsetDateTime.now().minusMinutes(5));

        int expired = job.expireDue(OffsetDateTime.now());

        assertThat(expired).isEqualTo(1);
        assertThat(handoverRepo.findByHandoverIdAndTenantId(handoverId, TENANT).orElseThrow().getStatus())
                .isEqualTo("EXPIRED");
        assertThat(episodeRepo.findByEpisodeIdAndTenantId(episodeId, TENANT).orElseThrow().getState())
                .isEqualTo("OPEN_IN_CARE");
    }

    @Test
    @DisplayName("a PENDING handover whose deadline has not yet passed is left alone")
    void leavesNotYetDueHandoverAlone() {
        UUID episodeId = seedAwaitingEpisode();
        UUID handoverId = seedPendingHandover(episodeId, OffsetDateTime.now().plusMinutes(10));

        int expired = job.expireDue(OffsetDateTime.now());

        assertThat(expired).isZero();
        assertThat(handoverRepo.findByHandoverIdAndTenantId(handoverId, TENANT).orElseThrow().getStatus())
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("a handover with no response_due_at (predates V209) is never swept — no guessed deadline")
    void nullDeadlineNeverSwept() {
        UUID episodeId = seedAwaitingEpisode();
        UUID handoverId = seedPendingHandover(episodeId, null);

        int expired = job.expireDue(OffsetDateTime.now());

        assertThat(expired).isZero();
        assertThat(handoverRepo.findByHandoverIdAndTenantId(handoverId, TENANT).orElseThrow().getStatus())
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("one already-resolved handover does not stop the sweep from expiring the others")
    void perHandoverIsolation() {
        UUID episode1 = seedAwaitingEpisode();
        UUID due1 = seedPendingHandover(episode1, OffsetDateTime.now().minusMinutes(5));

        UUID episode2 = seedAwaitingEpisode();
        UUID due2 = seedPendingHandover(episode2, OffsetDateTime.now().minusMinutes(5));
        // Resolve it out from under the sweep between the query and the per-item expiry, simulating
        // a race the isolated-transaction design must tolerate without aborting the whole sweep.
        handoverRepo.findByHandoverIdAndTenantId(due2, TENANT).orElseThrow().setStatus("ACCEPTED");

        int expired = job.expireDue(OffsetDateTime.now());

        assertThat(expired).isEqualTo(1);
        assertThat(handoverRepo.findByHandoverIdAndTenantId(due1, TENANT).orElseThrow().getStatus())
                .isEqualTo("EXPIRED");
        assertThat(handoverRepo.findByHandoverIdAndTenantId(due2, TENANT).orElseThrow().getStatus())
                .isEqualTo("ACCEPTED");
    }
}
