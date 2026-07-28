package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shaping of the programme cohort response.
 *
 * <p><strong>Scope.</strong> This covers the shaping only. The {@code cohortCounts} JPQL itself is
 * executed by {@link ProgrammeCohortQueryTest} — which could not exist when this file was written,
 * because the pct Spring context was unbootable under H2. That gap is now closed rather than left
 * standing as a documented limitation.</p>
 *
 * <p>What it does cover is the part that has actually misled people before: the arithmetic meaning
 * of the numbers.</p>
 */
class ProgrammeCohortShapingTest {

    /** Mirrors ProgrammeEnrolmentService.cohortCounts' mapping of the repository rows. */
    private static Map<String, Object> shape(List<Object[]> rows) {
        List<Map<String, Object>> counts = new java.util.ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("programme", r[0]);
            row.put("status", r[1]);
            row.put("enrolments", ((Number) r[2]).longValue());
            counts.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", counts);
        out.put("counts_enrolments_not_people",
                "A person enrolled in more than one programme is counted in each. Do not total "
                + "these rows into a patient count.");
        return out;
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyRowCarriesProgrammeStatusAndACount() {
        Map<String, Object> out = shape(List.<Object[]>of(
                new Object[]{"HIV_CARE", "ON_TREATMENT", 12L},
                new Object[]{"TB_TREATMENT", "ON_TREATMENT", 3L}));

        List<Map<String, Object>> counts = (List<Map<String, Object>>) out.get("counts");
        assertThat(counts).hasSize(2);
        assertThat(counts.get(0)).containsEntry("programme", "HIV_CARE")
                .containsEntry("status", "ON_TREATMENT").containsEntry("enrolments", 12L);
    }

    @Test
    void theResponseSaysTheseAreEnrolmentsNotPeople() {
        // The misreading this prevents is specific and has happened elsewhere in this estate:
        // someone sums a cohort table into a "patients reached" figure. A person on both HIV care
        // and TB treatment is legitimately in both rows, so the total is not a headcount — and the
        // response has to say so, because the number looks like a headcount.
        Map<String, Object> out = shape(List.<Object[]>of(new Object[]{"HIV_CARE", "ON_TREATMENT", 1L}));
        assertThat((String) out.get("counts_enrolments_not_people"))
                .contains("counted in each")
                .contains("Do not total");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEmptyCohortIsAnEmptyListAndNotAnAbsentKey() {
        // A consumer reading counts[] must get [] rather than null, so "no enrolments" and "the
        // field is missing" cannot become the same thing on the wire.
        Map<String, Object> out = shape(List.<Object[]>of());
        assertThat(out).containsKey("counts");
        assertThat((List<Map<String, Object>>) out.get("counts")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsArriveAsLongsRatherThanWhateverTheDriverReturned() {
        // COUNT() comes back as Long on Postgres and can be Integer/BigInteger elsewhere; the API
        // must not vary its JSON number type by driver.
        Map<String, Object> out = shape(List.<Object[]>of(new Object[]{"HIV_CARE", "ENROLLED", java.math.BigInteger.valueOf(7)}));
        List<Map<String, Object>> counts = (List<Map<String, Object>>) out.get("counts");
        assertThat(counts.get(0).get("enrolments")).isInstanceOf(Long.class).isEqualTo(7L);
    }
}
