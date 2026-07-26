package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReproductiveIntentionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReproductiveIntentionRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What a person wants for their own fertility.
 *
 * <p>Recording a new intention supersedes the previous one rather than overwriting it. The history
 * is clinical information: a woman who wanted a pregnancy last year and does not now, or who has
 * moved from undecided to certain, is telling you something about the conversation to have next.
 *
 * <p><b>Nothing here infers an intention.</b> Not from age, not from parity, not from whether she
 * is using contraception, and not from her being pregnant. The assumptions this pack was written
 * against — that every married woman wants a pregnancy, that every postpartum woman wants
 * contraception, that a pregnancy is wanted because it exists — are exactly the ones that produce
 * coercive counselling. If nobody asked, the answer is that nobody asked.
 */
@Service
public class ReproductiveIntentionService {

    private static final Logger log = LoggerFactory.getLogger(ReproductiveIntentionService.class);

    private final ReproductiveIntentionRepository intentions;

    public ReproductiveIntentionService(ReproductiveIntentionRepository intentions) {
        this.intentions = intentions;
    }

    @Transactional(readOnly = true)
    public Optional<ReproductiveIntentionEntity> current(UUID tenantId, String subjectCpid) {
        if (tenantId == null || subjectCpid == null || subjectCpid.isBlank()) {
            return Optional.empty();
        }
        return intentions.findCurrent(tenantId, subjectCpid);
    }

    @Transactional(readOnly = true)
    public List<ReproductiveIntentionEntity> history(UUID tenantId, String subjectCpid) {
        return intentions.history(tenantId, subjectCpid);
    }

    /**
     * Record an intention, superseding any previous one.
     *
     * <p>Idempotent under offline replay: the same packet arriving twice returns the record it
     * already created rather than superseding a person's stated intention with an identical copy of
     * itself, which would leave the history reading as though she had changed her mind.
     */
    @Transactional
    public ReproductiveIntentionEntity record(RecordIntentionCommand command) {
        if (command.clientOfflineId() != null) {
            Optional<ReproductiveIntentionEntity> replayed = intentions
                    .findByTenantIdAndClientOfflineId(command.tenantId(), command.clientOfflineId());
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }

        ReproductiveIntentionEntity intention = new ReproductiveIntentionEntity();
        intention.setIntentionId(UUID.randomUUID());
        intention.setTenantId(command.tenantId());
        intention.setSubjectCpid(command.subjectCpid());
        intention.setJourneyId(command.journeyId());
        intention.setEncounterId(command.encounterId());
        intention.setFacilityId(command.facilityId());
        intention.setRecordedAt(OffsetDateTime.now());
        intention.setRecordedBy(command.recordedBy());
        intention.setIntention(command.intention());
        intention.setTimeframeMonths(command.timeframeMonths());
        intention.setDesiredFamilySize(command.desiredFamilySize());
        intention.setPartnerIntention(orNotAssessed(command.partnerIntention()));
        intention.setFertilityConcern(orNotAssessed(command.fertilityConcern()));
        intention.setContraceptionDesired(orNotAssessed(command.contraceptionDesired()));
        intention.setUnmetNeed(orNotAssessed(command.unmetNeed()));
        intention.setNotes(command.notes());
        intention.setStatus(ReproductiveIntentionEntity.STATUS_CURRENT);
        intention.setClientOfflineId(command.clientOfflineId());
        intention.setCreatedAt(OffsetDateTime.now());

        // Supersede before inserting: the partial unique index permits only one CURRENT per person,
        // and a history that loses the previous answer loses the fact that she changed her mind.
        Optional<ReproductiveIntentionEntity> previous =
                intentions.findCurrent(command.tenantId(), command.subjectCpid());
        previous.ifPresent(p -> {
            p.setStatus(ReproductiveIntentionEntity.STATUS_SUPERSEDED);
            p.setSupersededBy(intention.getIntentionId());
            intentions.save(p);
        });

        ReproductiveIntentionEntity saved = intentions.save(intention);
        log.info("reproductive.intention.recorded subject={} intention={} supersededPrevious={}",
                command.subjectCpid(), command.intention(), previous.isPresent());
        return saved;
    }

    /**
     * Whether this person has stated a wish to become pregnant.
     *
     * <p>Returns null when nobody has asked. Never false — an unasked question is not a stated
     * preference, and routing someone into contraceptive counselling on the strength of silence is
     * how a service becomes coercive.
     */
    @Transactional(readOnly = true)
    public Boolean wantsPregnancy(UUID tenantId, String subjectCpid) {
        return current(tenantId, subjectCpid)
                .map(i -> switch (i.getIntention()) {
                    case ReproductiveIntentionEntity.WANTS_PREGNANCY_NOW -> Boolean.TRUE;
                    case ReproductiveIntentionEntity.WANTS_NO_MORE_CHILDREN -> Boolean.FALSE;
                    // LATER, UNDECIDED, DECLINED_TO_STATE and NOT_ASSESSED are all genuinely
                    // "not a yes and not a no", and flattening them into either would misreport her.
                    default -> null;
                })
                .orElse(null);
    }

    private static String orNotAssessed(String value) {
        return value == null || value.isBlank() ? ReproductiveIntentionEntity.NOT_ASSESSED : value;
    }

    public record RecordIntentionCommand(
            UUID tenantId,
            String subjectCpid,
            UUID facilityId,
            String journeyId,
            String encounterId,
            String intention,
            Integer timeframeMonths,
            Integer desiredFamilySize,
            String partnerIntention,
            String fertilityConcern,
            String contraceptionDesired,
            String unmetNeed,
            String notes,
            String clientOfflineId,
            String recordedBy) {
    }
}
