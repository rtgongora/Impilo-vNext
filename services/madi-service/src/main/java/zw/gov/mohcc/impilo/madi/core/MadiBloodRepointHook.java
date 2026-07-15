package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodOrderRepository;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointCommand;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointHook;

import java.util.UUID;

/**
 * Repoints MADI's blood-order patient anchor ({@code blood_orders.patient_cpid}) when VITO merges an
 * unknown trauma patient's provisional identity into a confirmed one. Idempotent. Auto-collected into
 * MADI's {@code List<IdentityRepointHook>} fan-out.
 */
@Component
public class MadiBloodRepointHook implements IdentityRepointHook {

    private final BloodOrderRepository bloodOrderRepository;

    public MadiBloodRepointHook(BloodOrderRepository bloodOrderRepository) {
        this.bloodOrderRepository = bloodOrderRepository;
    }

    @Override
    @Transactional
    public int repoint(IdentityRepointCommand command) {
        return bloodOrderRepository.repointPatientCpid(
                UUID.fromString(command.tenantId()), command.mergedHealthId(), command.survivorHealthId());
    }

    @Override
    public String participant() {
        return "madi-service";
    }
}
