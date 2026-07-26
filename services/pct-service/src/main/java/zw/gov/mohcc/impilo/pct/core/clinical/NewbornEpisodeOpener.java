package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.core.EncounterService;
import zw.gov.mohcc.impilo.pct.core.JourneyStateMachine;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;

import java.util.UUID;

/**
 * Opens the newborn's journey and encounter in a transaction of its own.
 *
 * <p>The separate transaction is the whole point. {@link NewbornEpisodeService} promises that
 * a birth record survives even if the clinical episode cannot be opened — the facts of a
 * delivery are worth more than the workflow scaffolding around them. Catching the exception
 * is not enough to keep that promise: a failed insert marks the surrounding JPA transaction
 * rollback-only, so the birth record would be discarded at commit no matter how carefully the
 * caller handled the error. Running here with {@code REQUIRES_NEW} means a failure is
 * contained in its own transaction and the caller's write genuinely survives.</p>
 *
 * <p>This was found by deploying, not by testing: a unit test with a mock that throws sees a
 * plain exception and no transaction, so it reports the promise as kept while production
 * quietly breaks it.</p>
 */
@Component
public class NewbornEpisodeOpener {

    private static final Logger log = LoggerFactory.getLogger(NewbornEpisodeOpener.class);

    private final JourneyStateMachine journeyStateMachine;
    private final EncounterService encounterService;

    public NewbornEpisodeOpener(JourneyStateMachine journeyStateMachine, EncounterService encounterService) {
        this.journeyStateMachine = journeyStateMachine;
        this.encounterService = encounterService;
    }

    /**
     * @param facilityId where the baby was born; required, because a journey without a facility
     *                   cannot be persisted and attempting it would fail the whole write
     * @return the opened episode, or null when it could not be opened
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OpenedEpisode open(UUID facilityId, String babyCpid, String deliverySourceRef) {
        if (facilityId == null) {
            log.warn("Newborn {} has a birth record but no facility could be resolved, so no journey was"
                    + " opened. The birth record stands; open the episode when the child is seen.", babyCpid);
            return null;
        }
        try {
            JourneyEntity journey = journeyStateMachine.createJourney(facilityId, babyCpid, "BIRTH", deliverySourceRef);
            EncounterEntity encounter = encounterService.startEncounter(
                    journey.getJourneyId(), "NEWBORN", "inpatient", "birth",
                    "in_person", null, "inpatient", "routine", null, null, null);
            return new OpenedEpisode(journey.getJourneyId(), encounter.getEncounterRef().toString());
        } catch (RuntimeException e) {
            log.warn("Newborn {} birth record stored, but the clinical episode could not be opened: {}",
                    babyCpid, e.getMessage());
            return null;
        }
    }

    public record OpenedEpisode(String journeyId, String encounterId) {
    }
}
