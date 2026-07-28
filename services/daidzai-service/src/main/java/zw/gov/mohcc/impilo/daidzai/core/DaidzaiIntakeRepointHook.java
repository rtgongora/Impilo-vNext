package zw.gov.mohcc.impilo.daidzai.core;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.AssistanceRequestRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmergencyRequestRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.MciCasualtyRepository;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointCommand;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointHook;

import java.util.UUID;

/**
 * Repoints DAIDZAI's public-intake subject anchors — {@code dai_emergency_request},
 * {@code dai_assistance_request}, {@code dai_mci_casualty} — when VITO merges a provisional
 * identity into a confirmed one. Separate from {@link DaidzaiTraumaRepointHook}, which covers the
 * trauma spine ({@code dai_trauma_episode}/{@code dai_emergency_incident}): these three are the
 * intake-side records a caller or a triage sieve tag creates before any trauma episode may exist,
 * and W12's coverage audit found all three carrying a live {@code subject_cpid} with no hook wired —
 * the exact silent-drift failure mode this pack's doctrine warns about (a table with a cpid ships
 * its repoint in the same wave, never a later one). Idempotent: a replayed merge matches no rows
 * the second time.
 */
@Component
public class DaidzaiIntakeRepointHook implements IdentityRepointHook {

    private final EmergencyRequestRepository emergencyRequestRepository;
    private final AssistanceRequestRepository assistanceRequestRepository;
    private final MciCasualtyRepository mciCasualtyRepository;

    public DaidzaiIntakeRepointHook(EmergencyRequestRepository emergencyRequestRepository,
                                    AssistanceRequestRepository assistanceRequestRepository,
                                    MciCasualtyRepository mciCasualtyRepository) {
        this.emergencyRequestRepository = emergencyRequestRepository;
        this.assistanceRequestRepository = assistanceRequestRepository;
        this.mciCasualtyRepository = mciCasualtyRepository;
    }

    @Override
    @Transactional
    public int repoint(IdentityRepointCommand command) {
        UUID tenantId = UUID.fromString(command.tenantId());
        int n = emergencyRequestRepository.repointSubjectCpid(
                tenantId, command.oldSubjectCpid(), command.newSubjectCpid());
        n += assistanceRequestRepository.repointSubjectCpid(
                tenantId, command.oldSubjectCpid(), command.newSubjectCpid());
        n += mciCasualtyRepository.repointSubjectCpid(
                tenantId, command.oldSubjectCpid(), command.newSubjectCpid());
        return n;
    }

    @Override
    public String participant() {
        return "daidzai-service";
    }
}
