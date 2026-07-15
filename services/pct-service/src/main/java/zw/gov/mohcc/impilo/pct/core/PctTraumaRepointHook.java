package zw.gov.mohcc.impilo.pct.core;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.repository.EdVisitRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyCaseRepository;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointCommand;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointHook;

import java.util.UUID;

/**
 * Repoints PCT's ED patient anchors when VITO merges an unknown trauma patient's provisional identity
 * into a confirmed one: {@code ed_visit.patient_cpid} and {@code emergency_cases.known_health_id}.
 * Idempotent. Auto-collected into PCT's {@code List<IdentityRepointHook>} fan-out.
 */
@Component
public class PctTraumaRepointHook implements IdentityRepointHook {

    private final EdVisitRepository edVisitRepository;
    private final EmergencyCaseRepository emergencyCaseRepository;

    public PctTraumaRepointHook(EdVisitRepository edVisitRepository,
                                EmergencyCaseRepository emergencyCaseRepository) {
        this.edVisitRepository = edVisitRepository;
        this.emergencyCaseRepository = emergencyCaseRepository;
    }

    @Override
    @Transactional
    public int repoint(IdentityRepointCommand command) {
        UUID tenantId = UUID.fromString(command.tenantId());
        int n = edVisitRepository.repointPatientCpid(tenantId, command.mergedHealthId(), command.survivorHealthId());
        n += emergencyCaseRepository.repointKnownHealthId(tenantId, command.mergedHealthId(), command.survivorHealthId());
        return n;
    }

    @Override
    public String participant() {
        return "pct-service";
    }
}
