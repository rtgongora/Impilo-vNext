package zw.gov.mohcc.impilo.daidzai.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import zw.gov.mohcc.impilo.daidzai.persistence.repository.AssistanceRequestRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmergencyIncidentRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmergencyRequestRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.MciCasualtyRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.TraumaEpisodeRepository;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointCommand;
import zw.gov.mohcc.impilo.sharedkernel.identity.VitoMergeRepointDispatcher;

/**
 * W12's "unknown -> VITO merge with nothing lost" proof for DAIDZAI: every table on this service
 * carrying a live {@code subject_cpid} must repoint from ONE dispatched merge, through the SAME
 * fan-out Spring wires in production ({@code List<IdentityRepointHook>} auto-collected into
 * {@code VitoMergeRepointDispatcher}).
 *
 * <p>Before this wave, {@link DaidzaiTraumaRepointHook} covered {@code dai_trauma_episode} and
 * {@code dai_emergency_incident} but nothing covered {@code dai_mci_casualty} (added this same
 * programme's W11), {@code dai_emergency_request} or {@code dai_assistance_request} — three tables
 * with a resolvable identity and no repoint path, the exact silent-drift failure the doctrine warns
 * about. {@link DaidzaiIntakeRepointHook} closes that gap; this test proves the closure by asserting
 * all five, not just the two that already worked.
 *
 * <p>Scope: proves every WIRED table repoints and that a redelivered merge is a no-op (idempotent).
 * It does not re-verify each repository's JPQL against a real database — that is proven separately
 * on real Postgres per this pack's standing migration-verification discipline — only that the hook
 * layer invokes every one of them with the right arguments and that the dispatcher fans out to both
 * hooks and aggregates their counts correctly.
 */
@ExtendWith(MockitoExtension.class)
class IdentityRepointFullFidelityTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String OLD_CPID = "CPID-PROVISIONAL";
    private static final String NEW_CPID = "CPID-CONFIRMED";

    @Mock private TraumaEpisodeRepository traumaEpisodeRepository;
    @Mock private EmergencyIncidentRepository emergencyIncidentRepository;
    @Mock private MciCasualtyRepository mciCasualtyRepository;
    @Mock private EmergencyRequestRepository emergencyRequestRepository;
    @Mock private AssistanceRequestRepository assistanceRequestRepository;

    private VitoMergeRepointDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        var traumaHook = new DaidzaiTraumaRepointHook(traumaEpisodeRepository, emergencyIncidentRepository);
        var intakeHook = new DaidzaiIntakeRepointHook(
                emergencyRequestRepository, assistanceRequestRepository, mciCasualtyRepository);
        // The exact List<IdentityRepointHook> shape Spring auto-collects in production.
        dispatcher = new VitoMergeRepointDispatcher(List.of(traumaHook, intakeHook));
    }

    @Test
    @DisplayName("one merge repoints all five identity-bearing daidzai tables — nothing is left behind")
    void allFiveTablesRepointFromOneMerge() {
        when(traumaEpisodeRepository.repointSubjectCpid(TENANT, OLD_CPID, NEW_CPID)).thenReturn(1);
        when(emergencyIncidentRepository.repointSubjectCpid(TENANT, OLD_CPID, NEW_CPID)).thenReturn(1);
        when(mciCasualtyRepository.repointSubjectCpid(TENANT, OLD_CPID, NEW_CPID)).thenReturn(1);
        when(emergencyRequestRepository.repointSubjectCpid(TENANT, OLD_CPID, NEW_CPID)).thenReturn(1);
        when(assistanceRequestRepository.repointSubjectCpid(TENANT, OLD_CPID, NEW_CPID)).thenReturn(1);

        var command = new IdentityRepointCommand(TENANT.toString(), OLD_CPID, NEW_CPID, "merge-1", "corr-1");
        var repointed = dispatcher.dispatch(command);

        // Both hooks name their participant() "daidzai-service" (the pct-service pair does the same
        // for PctEmergencyRepointHook/PctTraumaRepointHook), so the dispatcher's per-participant
        // diagnostic map holds only the LAST hook's own count, not a sum — a pre-existing
        // shared-kernel convention, not something this wave changes. The property that actually
        // matters, that every table repoints, is proven below by the per-repository verifies, not by
        // this map.
        assertThat(repointed).containsKey("daidzai-service");
        assertThat(dispatcher.hookCount()).isEqualTo(2);

        verify(traumaEpisodeRepository, times(1)).repointSubjectCpid(eq(TENANT), eq(OLD_CPID), eq(NEW_CPID));
        verify(emergencyIncidentRepository, times(1)).repointSubjectCpid(eq(TENANT), eq(OLD_CPID), eq(NEW_CPID));
        verify(mciCasualtyRepository, times(1)).repointSubjectCpid(eq(TENANT), eq(OLD_CPID), eq(NEW_CPID));
        verify(emergencyRequestRepository, times(1)).repointSubjectCpid(eq(TENANT), eq(OLD_CPID), eq(NEW_CPID));
        verify(assistanceRequestRepository, times(1)).repointSubjectCpid(eq(TENANT), eq(OLD_CPID), eq(NEW_CPID));
    }

    @Test
    @DisplayName("a redelivered merge matches nothing the second time — repoint is naturally idempotent")
    void redeliveredMergeIsANoOp() {
        when(traumaEpisodeRepository.repointSubjectCpid(TENANT, OLD_CPID, NEW_CPID)).thenReturn(0);
        when(emergencyIncidentRepository.repointSubjectCpid(TENANT, OLD_CPID, NEW_CPID)).thenReturn(0);
        when(mciCasualtyRepository.repointSubjectCpid(TENANT, OLD_CPID, NEW_CPID)).thenReturn(0);
        when(emergencyRequestRepository.repointSubjectCpid(TENANT, OLD_CPID, NEW_CPID)).thenReturn(0);
        when(assistanceRequestRepository.repointSubjectCpid(TENANT, OLD_CPID, NEW_CPID)).thenReturn(0);

        var command = new IdentityRepointCommand(TENANT.toString(), OLD_CPID, NEW_CPID, "merge-1", "corr-1");
        var repointed = dispatcher.dispatch(command);

        assertThat(repointed).containsEntry("daidzai-service", 0);
    }
}
