package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The antenatal contact schedule.
 *
 * <p>The property the tests are built around is the band-hole one: there must be no gestational age
 * at which the forecast is undefined. A schedule that returns nothing between two contacts is the
 * same failure as an early-warning score with a heart rate that matches no band — the patient falls
 * through the gap, and the gap is invisible because nothing errored.
 */
class ContactScheduleEngineTest {

    private final ContactScheduleService service =
            new ContactScheduleService(new ObjectMapper());

    private ContactScheduleEngine.ScheduleForecast at(int week, Integer... completed) {
        return service.forecast(week * 7, Set.of(completed));
    }

    // ---------------------------------------------------------------- the band-hole property

    @Test
    @DisplayName("every gestation from 0 to 300 days yields a defined forecast — no hole")
    void everyGestationHasADefinedForecast() {
        for (int day = 0; day <= 300; day++) {
            var forecast = service.forecast(day, Set.of());
            assertThat(forecast)
                    .as("day %d produced no forecast", day)
                    .isNotNull();
            assertThat(forecast.gestationKnown()).isTrue();
            assertThat(forecast.contacts())
                    .as("day %d produced an empty contact list", day)
                    .hasSize(8);
            assertThat(forecast.note()).as("day %d has no note", day).isNotBlank();
            // Either there is a next contact to act on, or she is post-term. Never neither, never
            // both — that is the hole.
            boolean hasNext = forecast.nextContact() != null;
            assertThat(hasNext ^ forecast.postTerm())
                    .as("day %d: exactly one of {a next contact, post-term} must hold "
                            + "(next=%s postTerm=%s)", day, hasNext, forecast.postTerm())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("every contact has exactly one status at every gestation — none is skipped or doubled")
    void everyContactAlwaysHasOneStatus() {
        for (int day = 0; day <= 300; day++) {
            var forecast = service.forecast(day, Set.of());
            for (int n = 1; n <= 8; n++) {
                final int contactNumber = n;
                assertThat(forecast.contacts())
                        .as("day %d: contact %d missing from the forecast", day, contactNumber)
                        .anyMatch(c -> c.contactNumber() == contactNumber);
            }
        }
    }

    // ---------------------------------------------------------------- placement

    @Test
    @DisplayName("at 12 weeks the first contact is due")
    void firstContactDueAtBooking() {
        var forecast = at(12);
        assertThat(forecast.nextContact()).isNotNull();
        assertThat(forecast.nextContact().contactNumber()).isEqualTo(1);
        assertThat(forecast.nextContact().status())
                .isEqualTo(ContactScheduleEngine.ContactStatus.DUE);
    }

    @Test
    @DisplayName("a woman booking at 22 weeks has overdue early contacts, not a clean slate")
    void lateBookerHasOverdueContacts() {
        var forecast = at(22);

        // Contacts 1 and 2 were due weeks ago and never happened. The schedule must show that,
        // not present her as simply due for contact 2.
        assertThat(forecast.overdueContacts()).contains(1);
        assertThat(forecast.note()).contains("overdue");
    }

    @Test
    @DisplayName("a completed contact is not also reported as due")
    void completedContactsAreNotStillDue() {
        var forecast = at(20, 1);

        var contact1 = forecast.contacts().stream()
                .filter(c -> c.contactNumber() == 1).findFirst().orElseThrow();
        assertThat(contact1.status()).isEqualTo(ContactScheduleEngine.ContactStatus.COMPLETED);
        assertThat(forecast.overdueContacts()).doesNotContain(1);
    }

    @Test
    @DisplayName("the next contact is the earliest actionable one, skipping completed ones")
    void nextContactSkipsCompleted() {
        var forecast = at(20, 1, 2);
        // 1 done, 2 done at week 20; the next actionable is 3 (upcoming at week 24).
        assertThat(forecast.nextContact()).isNotNull();
        assertThat(forecast.nextContact().contactNumber()).isEqualTo(3);
    }

    // ---------------------------------------------------------------- post-term

    @Test
    @DisplayName("at 41 weeks she is post-term, not due for a ninth contact")
    void postTermIsAnExplicitState() {
        var forecast = at(41);

        assertThat(forecast.postTerm()).isTrue();
        assertThat(forecast.nextContact())
                .as("post-term must not present a routine next contact").isNull();
        assertThat(forecast.postTermMessage()).containsIgnoringCase("post-term");
        assertThat(forecast.note()).contains("birth");
    }

    @Test
    @DisplayName("at 43 weeks she is still post-term, not wrapped around to something undefined")
    void wellPastTermStaysPostTerm() {
        var forecast = at(43);
        assertThat(forecast.postTerm()).isTrue();
        assertThat(forecast.contacts()).hasSize(8);
    }

    // ---------------------------------------------------------------- unknown gestation

    @Test
    @DisplayName("with no gestation the schedule is not placed, and says so rather than guessing week 1")
    void unknownGestationIsHonest() {
        var forecast = service.forecast(null, Set.of());

        assertThat(forecast.gestationKnown()).isFalse();
        assertThat(forecast.nextContact()).isNull();
        assertThat(forecast.note()).contains("Date the pregnancy first");
        // The pathway is still shown — every contact present, none fabricated as due.
        assertThat(forecast.contacts()).hasSize(8);
        assertThat(forecast.overdueContacts()).isEmpty();
    }

    @Test
    @DisplayName("with no gestation a completed contact still shows as completed")
    void unknownGestationStillHonoursCompletedContacts() {
        var forecast = service.forecast(null, Set.of(1));
        var contact1 = forecast.contacts().stream()
                .filter(c -> c.contactNumber() == 1).findFirst().orElseThrow();
        assertThat(contact1.status()).isEqualTo(ContactScheduleEngine.ContactStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- content integrity

    @Test
    @DisplayName("the schedule loads with eight contacts in ascending target order")
    void scheduleLoadsInOrder() {
        var schedule = service.schedule();
        assertThat(schedule).isNotNull();
        assertThat(schedule.contacts()).hasSize(8);

        int previous = -1;
        for (var c : schedule.contacts()) {
            assertThat(c.targetWeek())
                    .as("contact %d target week is not ascending", c.contactNumber())
                    .isGreaterThan(previous);
            previous = c.targetWeek();
            assertThat(c.keyActions())
                    .as("contact %d has no key actions", c.contactNumber()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("each contact's window is well-formed: opens <= target <= overdue")
    void windowsAreWellFormed() {
        for (var c : service.schedule().contacts()) {
            assertThat(c.opensWeek())
                    .as("contact %d opens after its target", c.contactNumber())
                    .isLessThanOrEqualTo(c.targetWeek());
            assertThat(c.overdueAfterWeek())
                    .as("contact %d is overdue before its target", c.contactNumber())
                    .isGreaterThanOrEqualTo(c.targetWeek());
        }
    }

    @Test
    @DisplayName("consecutive contact windows do not leave a gestational gap between them")
    void windowsDoNotLeaveAGap() {
        var contacts = service.schedule().contacts();
        for (int i = 1; i < contacts.size(); i++) {
            var previous = contacts.get(i - 1);
            var current = contacts.get(i);
            // The next window must open no later than the previous one becomes overdue, or a woman
            // between the two matches neither as DUE and only the overdue logic catches her.
            assertThat(current.opensWeek())
                    .as("gap between contact %d (overdue after %d) and contact %d (opens %d)",
                            previous.contactNumber(), previous.overdueAfterWeek(),
                            current.contactNumber(), current.opensWeek())
                    .isLessThanOrEqualTo(previous.overdueAfterWeek() + 1);
        }
    }

    @Test
    @DisplayName("a missing schedule reports unavailable, never an empty clean forecast")
    void missingScheduleIsNeverSilence() {
        var empty = service.read("clinical/does-not-exist.json");
        assertThat(empty).isNull();

        var forecast = ContactScheduleEngine.forecast(empty, 140, Set.of());
        assertThat(forecast.contacts()).isEmpty();
        assertThat(forecast.note()).contains("no");
    }
}
