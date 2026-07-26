package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The problem list's vocabularies are written down twice — as Java sets that produce a useful 400,
 * and as database CHECK constraints that are the actual guarantee. Two copies of one vocabulary
 * drift, and the way they drift is silent: a value the service accepts is rejected by Postgres as a
 * constraint violation at write time, which surfaces to a clinician as a 500 with no explanation.
 *
 * <p>So the migration is the source of truth and this test reads it. Adding a value to one side and
 * not the other fails the build rather than a patient's record.</p>
 */
class ProblemVocabularyTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V100__problem_model_certainty_and_status.sql");

    @Test
    void categoriesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ProblemVocabulary.CATEGORIES)
                .containsExactlyInAnyOrderElementsOf(checkValues("pct_problems_category_check"));
    }

    @Test
    void certaintiesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ProblemVocabulary.CERTAINTIES)
                .containsExactlyInAnyOrderElementsOf(checkValues("pct_problems_certainty_check"));
    }

    @Test
    void clinicalStatusesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ProblemVocabulary.CLINICAL_STATUSES)
                .containsExactlyInAnyOrderElementsOf(checkValues("pct_problems_clinical_status_check"));
    }

    /** The derived sets must be drawn from the vocabulary, not invented alongside it. */
    @Test
    void derivedStatusSetsAreSubsetsOfTheVocabulary() {
        assertThat(ProblemVocabulary.CLINICAL_STATUSES).containsAll(ProblemVocabulary.OPEN_STATUSES);
        assertThat(ProblemVocabulary.CLINICAL_STATUSES).containsAll(ProblemVocabulary.RECURRENT_STATUSES);
    }

    /**
     * A condition in remission is still the patient's and still needs monitoring. Dropping it from
     * the open set would quietly remove controlled epilepsy, treated lymphoma and stable lupus from
     * every problem list that shows "current problems".
     */
    @Test
    void remissionCountsAsAnOpenProblem() {
        assertThat(ProblemVocabulary.OPEN_STATUSES).contains("REMISSION");
        assertThat(ProblemVocabulary.OPEN_STATUSES).doesNotContain("RESOLVED", "INACTIVE");
    }

    @Test
    void refusesAnUnrecognisedValueAndNamesWhatIsAllowed() {
        assertThatThrownBy(() -> ProblemVocabulary.require(
                "diagnostic_certainty", "PROBABLY", ProblemVocabulary.CERTAINTIES, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROBABLY")
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void treatsAbsentAsAbsentRatherThanInventingAValue() {
        assertThat(ProblemVocabulary.require("severity", null, ProblemVocabulary.CERTAINTIES, false)).isNull();
        assertThat(ProblemVocabulary.require("severity", "  ", ProblemVocabulary.CERTAINTIES, false)).isNull();
    }

    @Test
    void normalisesCase() {
        assertThat(ProblemVocabulary.require(
                "clinical_status", "recurrence", ProblemVocabulary.CLINICAL_STATUSES, true))
                .isEqualTo("RECURRENCE");
    }

    /** Reads the quoted values out of one named CHECK constraint in the migration. */
    private static Set<String> checkValues(String constraintName) throws IOException {
        String sql = Files.readString(MIGRATION);
        int start = sql.indexOf("CONSTRAINT " + constraintName);
        assertThat(start)
                .withFailMessage("constraint %s not found in %s", constraintName, MIGRATION)
                .isGreaterThan(-1);

        // Take the IN (...) list belonging to this constraint only.
        int inStart = sql.indexOf("IN (", start);
        int inEnd = sql.indexOf("))", inStart);
        assertThat(inStart).isGreaterThan(-1);
        assertThat(inEnd).isGreaterThan(inStart);

        Set<String> values = new LinkedHashSet<>();
        Matcher m = Pattern.compile("'([A-Z_]+)'").matcher(sql.substring(inStart, inEnd));
        while (m.find()) {
            values.add(m.group(1));
        }
        assertThat(values).withFailMessage("no values parsed for %s", constraintName).isNotEmpty();
        return values;
    }
}
