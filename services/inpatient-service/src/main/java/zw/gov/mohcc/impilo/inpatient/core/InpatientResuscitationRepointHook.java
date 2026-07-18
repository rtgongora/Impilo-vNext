package zw.gov.mohcc.impilo.inpatient.core;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EmergencyActivationRepository;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointCommand;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointHook;

import java.util.UUID;

/**
 * Repoints the resuscitation patient anchor when VITO merges an unknown trauma patient's provisional
 * identity into a confirmed one. resuscitation_record has no patient column — the anchor lives on
 * {@code emergency_activation.subject_cpid} (resuscitation_record + resuscitation_event follow through
 * the activation_id FK). Idempotent. Auto-collected into inpatient's {@code List<IdentityRepointHook>}
 * fan-out ALONGSIDE the theatre session's InpatientProcedureEpisodeRepointHook (procedure_episode).
 */
@Component
public class InpatientResuscitationRepointHook implements IdentityRepointHook {

    private final EmergencyActivationRepository emergencyActivationRepository;

    public InpatientResuscitationRepointHook(EmergencyActivationRepository emergencyActivationRepository) {
        this.emergencyActivationRepository = emergencyActivationRepository;
    }

    @Override
    @Transactional
    public int repoint(IdentityRepointCommand command) {
        return emergencyActivationRepository.repointSubjectCpid(
                UUID.fromString(command.tenantId()), command.oldSubjectCpid(), command.newSubjectCpid());
    }

    @Override
    public String participant() {
        return "inpatient.resuscitation";
    }
}
