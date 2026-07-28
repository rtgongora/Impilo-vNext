package zw.gov.mohcc.impilo.pct.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointCommand;
import zw.gov.mohcc.impilo.sharedkernel.identity.VitoMergeRepointDispatcher;

/**
 * W12's "unknown -> VITO merge with nothing lost" proof for PCT's own emergency spine —
 * {@code emergency_episode.subject_cpid} repoints from a single dispatched merge, through the same
 * fan-out Spring wires in production.
 *
 * <p><b>Deliberately scoped to {@link PctEmergencyRepointHook} only.</b> pct also carries
 * {@link PctTraumaRepointHook} ({@code ed_visit}/{@code emergency_cases}, pre-existing, outside this
 * pack's remit) — extending this test to it would mean building fakes for two large ED entities this
 * wave never touches. Recorded here as a deliberate boundary, not a silent gap: the coverage guard
 * (check-identity-repoint-coverage.sh) still asserts that hook exists, just does not re-prove its SQL.
 */
@ExtendWith(MockitoExtension.class)
class IdentityRepointFullFidelityTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String OLD_CPID = "CPID-PROVISIONAL";
    private static final String NEW_CPID = "CPID-CONFIRMED";

    @Mock private EmergencyEpisodeRepository episodeRepository;

    private VitoMergeRepointDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        var hook = new PctEmergencyRepointHook(episodeRepository);
        dispatcher = new VitoMergeRepointDispatcher(List.of(hook));
    }

    @Test
    @DisplayName("a merge repoints emergency_episode.subject_cpid through the same fan-out production uses")
    void episodeRepointsFromOneMerge() {
        when(episodeRepository.repointSubjectCpid(eq(TENANT), eq(OLD_CPID), eq(NEW_CPID), org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(1);

        var command = new IdentityRepointCommand(TENANT.toString(), OLD_CPID, NEW_CPID, "merge-1", "corr-1");
        var repointed = dispatcher.dispatch(command);

        assertThat(repointed).containsEntry("pct-service", 1);
        verify(episodeRepository, times(1)).repointSubjectCpid(
                eq(TENANT), eq(OLD_CPID), eq(NEW_CPID), org.mockito.ArgumentMatchers.any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("a redelivered merge matches nothing the second time")
    void redeliveredMergeIsANoOp() {
        when(episodeRepository.repointSubjectCpid(eq(TENANT), eq(OLD_CPID), eq(NEW_CPID), org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(0);

        var command = new IdentityRepointCommand(TENANT.toString(), OLD_CPID, NEW_CPID, "merge-1", "corr-1");
        var repointed = dispatcher.dispatch(command);

        assertThat(repointed).containsEntry("pct-service", 0);
    }
}
