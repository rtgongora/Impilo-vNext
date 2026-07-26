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

/**
 * Same drift guard as {@link ProblemVocabularyTest}, for the episode vocabularies. The migration is
 * the source of truth; adding a value on one side only fails the build rather than a patient's
 * record.
 */
class MedicalEpisodeVocabularyTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V101__medical_episode.sql");

    @Test
    void typesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(MedicalEpisodeVocabulary.TYPES)
                .containsExactlyInAnyOrderElementsOf(checkValues("pct_medical_episodes_type_check"));
    }

    @Test
    void statusesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(MedicalEpisodeVocabulary.STATUSES)
                .containsExactlyInAnyOrderElementsOf(checkValues("pct_medical_episodes_status_check"));
    }

    @Test
    void endReasonsMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(MedicalEpisodeVocabulary.END_REASONS)
                .containsExactlyInAnyOrderElementsOf(checkValues("pct_medical_episodes_end_reason_check"));
    }

    @Test
    void problemRolesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(MedicalEpisodeVocabulary.PROBLEM_ROLES)
                .containsExactlyInAnyOrderElementsOf(checkValues("pct_episode_problems_role_check"));
    }

    @Test
    void openAndClosedStatusesPartitionTheVocabulary() {
        assertThat(MedicalEpisodeVocabulary.STATUSES)
                .containsAll(MedicalEpisodeVocabulary.OPEN_STATUSES)
                .containsAll(MedicalEpisodeVocabulary.CLOSED_STATUSES);
        assertThat(MedicalEpisodeVocabulary.OPEN_STATUSES)
                .doesNotContainAnyElementsOf(MedicalEpisodeVocabulary.CLOSED_STATUSES);
        // Every status is either open or closed — a third category would silently escape both the
        // "still running" and "must have an end date" halves of the model.
        assertThat(MedicalEpisodeVocabulary.OPEN_STATUSES.size()
                   + MedicalEpisodeVocabulary.CLOSED_STATUSES.size())
                .isEqualTo(MedicalEpisodeVocabulary.STATUSES.size());
    }

    /**
     * The reason these are separate from status. All four of these leave the status FINISHED and
     * mean entirely different things; a single "finished" would make an outcome indicator report a
     * patient who was lost to follow-up identically to one who completed treatment.
     */
    @Test
    void endReasonsDistinguishOutcomesThatStatusCannot() {
        assertThat(MedicalEpisodeVocabulary.END_REASONS)
                .contains("COMPLETED", "TRANSFERRED", "LOST_TO_FOLLOW_UP", "DIED");
    }

    private static Set<String> checkValues(String constraintName) throws IOException {
        String sql = Files.readString(MIGRATION);
        int start = sql.indexOf("CONSTRAINT " + constraintName);
        assertThat(start)
                .withFailMessage("constraint %s not found in %s", constraintName, MIGRATION)
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
