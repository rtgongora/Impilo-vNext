package zw.gov.mohcc.impilo.daidzai.core;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmergencyIncidentRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.TraumaEpisodeRepository;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointCommand;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointHook;

import java.util.UUID;

/**
 * Repoints DAIDZAI's trauma subject anchor when VITO merges an unknown patient's provisional
 * identity into a confirmed one — the canonical trauma_episode + the incident both carry
 * {@code subject_health_id}. Idempotent (a redelivered merge finds no rows on the tombstoned id).
 * Auto-collected into DAIDZAI's {@code List<IdentityRepointHook>} fan-out.
 */
@Component
public class DaidzaiTraumaRepointHook implements IdentityRepointHook {

    private final TraumaEpisodeRepository episodeRepository;
    private final EmergencyIncidentRepository incidentRepository;

    public DaidzaiTraumaRepointHook(TraumaEpisodeRepository episodeRepository,
                                    EmergencyIncidentRepository incidentRepository) {
        this.episodeRepository = episodeRepository;
        this.incidentRepository = incidentRepository;
    }

    @Override
    @Transactional
    public int repoint(IdentityRepointCommand command) {
        UUID tenantId = UUID.fromString(command.tenantId());
        int n = episodeRepository.repointSubjectHealthId(tenantId, command.mergedHealthId(), command.survivorHealthId());
        n += incidentRepository.repointSubjectHealthId(tenantId, command.mergedHealthId(), command.survivorHealthId());
        return n;
    }

    @Override
    public String participant() {
        return "daidzai-service";
    }
}
