package zw.gov.mohcc.impilo.pct.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import zw.gov.mohcc.impilo.pct.domain.EmergencyAlertType;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;

/**
 * The six alert triggers, exercised without a database or a scheduler. This is the
 * "patients cannot disappear" logic, so each trigger is tested for both the firing case and the
 * quiet case — a trigger that always fires is as useless as one that never does.
 */
class EmergencyAlertRulesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-28T12:00:00Z");
    private static final EmergencyAlertRules.Thresholds T = EmergencyAlertRules.Thresholds.defaults();

    /** An open episode that warrants nothing: arrived recently, observed and located just now. */
    private static EmergencyEpisodeEntity quietEpisode() {
        EmergencyEpisodeEntity e = new EmergencyEpisodeEntity();
        e.setEpisodeId(UUID.randomUUID());
        e.setTenantId(UUID.randomUUID());
        e.setFacilityId(UUID.randomUUID());
        e.setState("OPEN_IN_CARE");
        e.setArrivedAt(NOW.minusMinutes(5));
        e.setLastObservationAt(NOW.minusMinutes(5));
        e.setLocationConfirmedAt(NOW.minusMinutes(5));
        return e;
    }

    private static List<EmergencyAlertType> typesOf(List<EmergencyAlertRules.Candidate> cs) {
        return cs.stream().map(EmergencyAlertRules.Candidate::type).toList();
    }

    private static List<EmergencyAlertRules.Candidate> evaluate(EmergencyEpisodeEntity e) {
        return EmergencyAlertRules.evaluate(e, NOW, T, false);
    }

    @Test
    @DisplayName("A well-tended episode raises nothing")
    void quietEpisodeRaisesNothing() {
        assertThat(evaluate(quietEpisode())).isEmpty();
    }

    @Test
    @DisplayName("A closed or merged episode never alerts, however stale its clocks")
    void closedEpisodeNeverAlerts() {
        for (String state : List.of("CLOSED_DISCHARGED", "CLOSED_DIED", "MERGED", "PRE_ARRIVAL")) {
            EmergencyEpisodeEntity e = quietEpisode();
            e.setState(state);
            e.setArrivedAt(NOW.minusDays(3));
            e.setLastObservationAt(NOW.minusDays(3));
            e.setLocationConfirmedAt(NOW.minusDays(3));
            assertThat(evaluate(e)).as("state %s must not alert", state).isEmpty();
        }
    }

    @Test
    @DisplayName("TRIAGE_OVERDUE fires when the target passed with no first contact")
    void triageOverdue() {
        EmergencyEpisodeEntity e = quietEpisode();
        e.setTargetFirstContactAt(NOW.minusMinutes(1));
        assertThat(typesOf(evaluate(e))).contains(EmergencyAlertType.TRIAGE_OVERDUE);
    }

    @Test
    @DisplayName("TRIAGE_OVERDUE stays quiet once first contact is recorded")
    void triageOverdueSilentAfterContact() {
        EmergencyEpisodeEntity e = quietEpisode();
        e.setTargetFirstContactAt(NOW.minusMinutes(30));
        e.setFirstContactAt(NOW.minusMinutes(25));
        assertThat(typesOf(evaluate(e))).doesNotContain(EmergencyAlertType.TRIAGE_OVERDUE);
    }

    @Test
    @DisplayName("No triage target means no TRIAGE_OVERDUE — the gap belongs to triage, not to this alert")
    void noTargetNoTriageAlert() {
        EmergencyEpisodeEntity e = quietEpisode();
        e.setTargetFirstContactAt(null);
        assertThat(typesOf(evaluate(e))).doesNotContain(EmergencyAlertType.TRIAGE_OVERDUE);
    }

    @Test
    @DisplayName("REASSESSMENT_DUE fires only once the interval has actually elapsed")
    void reassessmentDue() {
        EmergencyEpisodeEntity due = quietEpisode();
        due.setReassessmentDueAt(NOW.minusSeconds(1));
        assertThat(typesOf(evaluate(due))).contains(EmergencyAlertType.REASSESSMENT_DUE);

        EmergencyEpisodeEntity notYet = quietEpisode();
        notYet.setReassessmentDueAt(NOW.plusMinutes(10));
        assertThat(typesOf(evaluate(notYet))).doesNotContain(EmergencyAlertType.REASSESSMENT_DUE);
    }

    @Test
    @DisplayName("OBSERVATION_OVERDUE fires on a stale observation")
    void observationOverdue() {
        EmergencyEpisodeEntity e = quietEpisode();
        e.setLastObservationAt(NOW.minusMinutes(90));
        assertThat(typesOf(evaluate(e))).contains(EmergencyAlertType.OBSERVATION_OVERDUE);
    }

    @Test
    @DisplayName("An episode with NO observation ever recorded still alerts — the emptiest chart is not a quiet one")
    void neverObservedAlerts() {
        EmergencyEpisodeEntity e = quietEpisode();
        e.setLastObservationAt(null);
        e.setArrivedAt(NOW.minusMinutes(90));
        e.setLocationConfirmedAt(NOW.minusMinutes(5)); // isolate the observation trigger
        List<EmergencyAlertRules.Candidate> cs = evaluate(e);
        assertThat(typesOf(cs)).contains(EmergencyAlertType.OBSERVATION_OVERDUE);
        assertThat(cs.stream().filter(c -> c.type() == EmergencyAlertType.OBSERVATION_OVERDUE)
                .findFirst().orElseThrow().reason()).contains("has ever been recorded");
    }

    @Test
    @DisplayName("LOCATION_UNKNOWN fires on STALENESS of the confirmation, not on a null location string")
    void locationStaleFires() {
        EmergencyEpisodeEntity e = quietEpisode();
        e.setCurrentLocation("Resus bay 2");   // location IS known...
        e.setLocationConfirmedAt(NOW.minusMinutes(90)); // ...but nobody has confirmed it in 90 min
        assertThat(typesOf(evaluate(e))).contains(EmergencyAlertType.LOCATION_UNKNOWN);
    }

    @Test
    @DisplayName("A never-confirmed location alerts after the grace period — nobody has EVER known where they are")
    void locationNeverConfirmedAlertsAfterGrace() {
        EmergencyEpisodeEntity e = quietEpisode();
        e.setLocationConfirmedAt(null);
        e.setArrivedAt(NOW.minusMinutes(45));
        e.setLastObservationAt(NOW.minusMinutes(5)); // isolate the location trigger
        assertThat(typesOf(evaluate(e))).contains(EmergencyAlertType.LOCATION_UNKNOWN);
    }

    @Test
    @DisplayName("A never-confirmed location does NOT alert inside the grace period")
    void locationNeverConfirmedQuietInsideGrace() {
        EmergencyEpisodeEntity e = quietEpisode();
        e.setLocationConfirmedAt(null);
        e.setArrivedAt(NOW.minusMinutes(5));
        assertThat(typesOf(evaluate(e))).doesNotContain(EmergencyAlertType.LOCATION_UNKNOWN);
    }

    @Test
    @DisplayName("ACCEPTANCE_OVERDUE fires only in OPEN_AWAITING_ACCEPTANCE, and says responsibility has not transferred")
    void acceptanceOverdue() {
        EmergencyEpisodeEntity e = quietEpisode();
        e.setState("OPEN_AWAITING_ACCEPTANCE");
        e.setAwaitingAcceptanceSince(NOW.minusMinutes(45));
        List<EmergencyAlertRules.Candidate> cs = evaluate(e);
        assertThat(typesOf(cs)).contains(EmergencyAlertType.ACCEPTANCE_OVERDUE);
        assertThat(cs.stream().filter(c -> c.type() == EmergencyAlertType.ACCEPTANCE_OVERDUE)
                .findFirst().orElseThrow().reason()).contains("NOT transferred");

        EmergencyEpisodeEntity inCare = quietEpisode();
        inCare.setAwaitingAcceptanceSince(NOW.minusMinutes(45));
        assertThat(typesOf(evaluate(inCare))).doesNotContain(EmergencyAlertType.ACCEPTANCE_OVERDUE);
    }

    @Test
    @DisplayName("CORRELATION_DIVERGENCE is raised only when the reconciler reports a disagreement")
    void correlationDivergence() {
        EmergencyEpisodeEntity e = quietEpisode();
        assertThat(typesOf(EmergencyAlertRules.evaluate(e, NOW, T, true)))
                .contains(EmergencyAlertType.CORRELATION_DIVERGENCE);
        assertThat(typesOf(EmergencyAlertRules.evaluate(e, NOW, T, false)))
                .doesNotContain(EmergencyAlertType.CORRELATION_DIVERGENCE);
    }

    @Test
    @DisplayName("Every candidate carries the clock reading that produced it, so a responder sees the evidence")
    void everyCandidateCarriesItsEvidence() {
        EmergencyEpisodeEntity e = quietEpisode();
        e.setTargetFirstContactAt(NOW.minusMinutes(20));
        e.setReassessmentDueAt(NOW.minusMinutes(5));
        e.setLastObservationAt(NOW.minusMinutes(90));
        e.setLocationConfirmedAt(NOW.minusMinutes(90));
        List<EmergencyAlertRules.Candidate> cs = evaluate(e);
        assertThat(cs).hasSize(4);
        assertThat(cs).allSatisfy(c -> {
            assertThat(c.reason()).isNotBlank();
            assertThat(c.detectedValueAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("Alert types carry a severity in the schema's vocabulary and a positive response window")
    void typeMetadataMatchesSchema() {
        for (EmergencyAlertType t : EmergencyAlertType.values()) {
            assertThat(t.severity()).isIn("CRITICAL", "HIGH", "MEDIUM", "LOW");
            assertThat(t.responseWindowMinutes()).isPositive();
        }
    }
}
