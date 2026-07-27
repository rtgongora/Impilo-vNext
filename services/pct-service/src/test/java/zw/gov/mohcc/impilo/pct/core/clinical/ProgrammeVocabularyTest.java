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
 * Same drift guard as {@link MedicalEpisodeVocabularyTest}, for the HIV/TB programme vocabularies.
 * The migrations are the source of truth; adding a value on one side only fails the build rather
 * than a patient's record.
 */
class ProgrammeVocabularyTest {

    private static final Path ENROLMENT_MIGRATION =
            Path.of("src/main/resources/db/migration/V108__programme_enrolment.sql");
    private static final Path REGIMEN_MIGRATION =
            Path.of("src/main/resources/db/migration/V109__treatment_regimen.sql");

    @Test
    void programmesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ProgrammeVocabulary.PROGRAMMES).containsExactlyInAnyOrderElementsOf(
                checkValues(ENROLMENT_MIGRATION, "pct_programme_enrolments_programme_check"));
    }

    @Test
    void enrolmentStatusesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ProgrammeVocabulary.ENROLMENT_STATUSES).containsExactlyInAnyOrderElementsOf(
                checkValues(ENROLMENT_MIGRATION, "pct_programme_enrolments_status_check"));
    }

    @Test
    void exitReasonsMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ProgrammeVocabulary.EXIT_REASONS).containsExactlyInAnyOrderElementsOf(
                checkValues(ENROLMENT_MIGRATION, "pct_programme_enrolments_exit_reason_check"));
    }

    @Test
    void regimenStagesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ProgrammeVocabulary.REGIMEN_STAGES).containsExactlyInAnyOrderElementsOf(
                checkValues(REGIMEN_MIGRATION, "pct_treatment_regimens_stage_check"));
    }

    @Test
    void changeReasonsMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ProgrammeVocabulary.CHANGE_REASONS).containsExactlyInAnyOrderElementsOf(
                checkValues(REGIMEN_MIGRATION, "pct_treatment_regimens_change_reason_check"));
    }

    @Test
    void openAndClosedEnrolmentStatusesPartitionTheVocabulary() {
        assertThat(ProgrammeVocabulary.ENROLMENT_STATUSES)
                .containsAll(ProgrammeVocabulary.OPEN_ENROLMENT_STATUSES)
                .containsAll(ProgrammeVocabulary.CLOSED_ENROLMENT_STATUSES);
        assertThat(ProgrammeVocabulary.OPEN_ENROLMENT_STATUSES)
                .doesNotContainAnyElementsOf(ProgrammeVocabulary.CLOSED_ENROLMENT_STATUSES);
        // Every status is either open or closed; a third category would escape both the "still in
        // the programme" and "must have an exit reason" halves of the model.
        assertThat(ProgrammeVocabulary.OPEN_ENROLMENT_STATUSES.size()
                   + ProgrammeVocabulary.CLOSED_ENROLMENT_STATUSES.size())
                .isEqualTo(ProgrammeVocabulary.ENROLMENT_STATUSES.size());
    }

    @Test
    void exitReasonsDistinguishOutcomesThatStatusCannot() {
        // All leave status EXITED and mean entirely different things — collapsing them would report
        // a patient lost to follow-up identically to one who was cured.
        assertThat(ProgrammeVocabulary.EXIT_REASONS)
                .contains("CURED", "TREATMENT_COMPLETED", "TRANSFERRED_OUT", "LOST_TO_FOLLOW_UP", "DIED");
    }

    @Test
    void artAndTbStagesPartitionTheRegimenStages() {
        assertThat(ProgrammeVocabulary.REGIMEN_STAGES)
                .containsAll(ProgrammeVocabulary.ART_STAGES)
                .containsAll(ProgrammeVocabulary.TB_STAGES);
        assertThat(ProgrammeVocabulary.ART_STAGES)
                .doesNotContainAnyElementsOf(ProgrammeVocabulary.TB_STAGES);
        assertThat(ProgrammeVocabulary.ART_STAGES.size() + ProgrammeVocabulary.TB_STAGES.size())
                .isEqualTo(ProgrammeVocabulary.REGIMEN_STAGES.size());
    }

    @Test
    void stagesAreScopedToTheirProgramme() {
        // An ART line on a TB enrolment is a category error the shared stage column cannot catch.
        assertThatThrownBy(() ->
                ProgrammeVocabulary.requireStageForProgramme(ProgrammeVocabulary.TB_TREATMENT, "FIRST_LINE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to programme TB_TREATMENT");
        assertThatThrownBy(() ->
                ProgrammeVocabulary.requireStageForProgramme(ProgrammeVocabulary.HIV_CARE, "INTENSIVE_PHASE"))
                .isInstanceOf(IllegalArgumentException.class);
        // The valid pairings do not throw.
        ProgrammeVocabulary.requireStageForProgramme(ProgrammeVocabulary.HIV_CARE, "FIRST_LINE");
        ProgrammeVocabulary.requireStageForProgramme(ProgrammeVocabulary.TB_TREATMENT, "CONTINUATION_PHASE");
    }

    @Test
    void onlyHivIsConfidential() {
        // TB is a notifiable disease, confidential from nobody; conflating the two would be wrong.
        assertThat(ProgrammeVocabulary.isConfidential(ProgrammeVocabulary.HIV_CARE)).isTrue();
        assertThat(ProgrammeVocabulary.isConfidential(ProgrammeVocabulary.TB_TREATMENT)).isFalse();
    }

    private static Set<String> checkValues(Path migration, String constraintName) throws IOException {
        String sql = Files.readString(migration);
        int start = sql.indexOf("CONSTRAINT " + constraintName);
        assertThat(start)
                .withFailMessage("constraint %s not found in %s", constraintName, migration)
                .isGreaterThan(-1);

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
