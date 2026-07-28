package zw.gov.mohcc.impilo.pct.core;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointCommand;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointHook;

import java.util.UUID;

/**
 * Repoints {@code emergency_episode.subject_cpid} when VITO merges a provisional identity into a
 * confirmed one.
 *
 * <p><b>Why this ships in the same wave as the table, not a later one.</b> An emergency episode is
 * routinely opened on a patient nobody has identified — that is the care-first requirement, not an
 * edge case. If the table exists before the hook does, then the first real merge silently leaves
 * that patient's episode, and everything correlated to it, attached to an identity that no longer
 * refers to anyone. Nothing fails; the record simply stops being findable under the name the patient
 * now has. So the rule for this pack is that any table carrying a cpid gets its repoint in the same
 * commit range as the migration.
 *
 * <p>What makes this safe is that the repoint updates an <b>attribute</b>. Every emergency clinical
 * row anchors on {@code episode_id}, and the correlations that matter — OROS orders on
 * {@code encounter_ref}, MADI on the episode id, procedures on
 * {@code procedure_episode.emergency_episode_id}, prehospital on
 * {@code dai_ems_epcr.trauma_episode_id} — do not mention the cpid at all. A merge therefore cannot
 * detach an order, a result, a medicine, a procedure, a blood product or a note, because none of them
 * were ever parented by the identity.
 *
 * <p>Idempotent: a replayed merge matches no rows the second time and returns zero.
 */
@Component
public class PctEmergencyRepointHook implements IdentityRepointHook {

    private final EmergencyEpisodeRepository episodeRepository;

    public PctEmergencyRepointHook(EmergencyEpisodeRepository episodeRepository) {
        this.episodeRepository = episodeRepository;
    }

    @Override
    @Transactional
    public int repoint(IdentityRepointCommand command) {
        UUID tenantId = UUID.fromString(command.tenantId());
        return episodeRepository.repointSubjectCpid(
                tenantId, command.oldSubjectCpid(), command.newSubjectCpid(), java.time.OffsetDateTime.now());
    }

    @Override
    public String participant() {
        return "pct-service";
    }
}
