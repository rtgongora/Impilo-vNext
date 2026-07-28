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
 * The examination vocabularies in Java and in the database must be the same vocabulary.
 *
 * <p>Two copies drift silently. The Java set produces a useful 400; the CHECK constraint is the
 * actual guarantee. This test parses the migration so the two cannot part company — the same
 * mutation-proven approach {@link ProblemVocabularyTest} and {@link ProgrammeVocabularyTest} use.</p>
 */
class ExaminationVocabularyTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V113__examination_framework.sql");

    @Test
    void regionsMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ExaminationVocabulary.REGIONS).containsExactlyInAnyOrderElementsOf(
                checkValues("pct_examination_findings_region_check"));
    }

    @Test
    void thereAreExactlyTheTwentyTwoRegionsTheBriefNames() {
        // §7 lists twenty-two. A framework that quietly ships nineteen looks complete to everyone who
        // did not count, and the three that went missing are invisible rather than absent.
        assertThat(ExaminationVocabulary.REGIONS).hasSize(22);
    }

    @Test
    void statesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ExaminationVocabulary.STATES).containsExactlyInAnyOrderElementsOf(
                checkValues("pct_examination_findings_state_check"));
    }

    @Test
    void thereAreExactlySixStatesAndNotExaminedIsOneOfThem() {
        // The whole framework turns on this. A two-state examination form forces every unperformed
        // examination into "normal", because that is the harmless-looking one.
        assertThat(ExaminationVocabulary.STATES).hasSize(6);
        assertThat(ExaminationVocabulary.STATES).contains("NOT_EXAMINED", "UNABLE_TO_EXAMINE",
                "DEFERRED", "UNCERTAIN");
    }

    @Test
    void graphicsMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ExaminationVocabulary.GRAPHICS).containsExactlyInAnyOrderElementsOf(
                checkValues("pct_examination_findings_graphic_check"));
    }

    @Test
    void thereAreExactlyTheElevenGraphicsTheBriefNames() {
        assertThat(ExaminationVocabulary.GRAPHICS).hasSize(11);
    }

    @Test
    void lateralityMatchesTheDatabaseCheckConstraint() throws IOException {
        assertThat(ExaminationVocabulary.LATERALITY).containsExactlyInAnyOrderElementsOf(
                checkValues("pct_examination_findings_laterality_check"));
    }

    @Test
    void notExaminedIsNeverCountedAsExamined() {
        // The single assertion this framework exists for. If these ever merge, "the chest was clear"
        // and "nobody listened to the chest" become the same record.
        assertThat(ExaminationVocabulary.wasExamined("NORMAL")).isTrue();
        assertThat(ExaminationVocabulary.wasExamined("ABNORMAL")).isTrue();
        // The examiner looked and is unsure — that is an examination, and a useful one.
        assertThat(ExaminationVocabulary.wasExamined("UNCERTAIN")).isTrue();

        assertThat(ExaminationVocabulary.wasExamined("NOT_EXAMINED")).isFalse();
        assertThat(ExaminationVocabulary.wasExamined("UNABLE_TO_EXAMINE")).isFalse();
        assertThat(ExaminationVocabulary.wasExamined("DEFERRED")).isFalse();
    }

    @Test
    void everyStateThatIsNotPlainlyNormalOrPlainlyUnexaminedMustSaySomething() {
        // ABNORMAL/UNCERTAIN must say what was found; UNABLE_TO_EXAMINE/DEFERRED must say why, so the
        // next examiner knows what has to change before the examination can happen.
        assertThat(ExaminationVocabulary.requiresDetail("ABNORMAL")).isTrue();
        assertThat(ExaminationVocabulary.requiresDetail("UNCERTAIN")).isTrue();
        assertThat(ExaminationVocabulary.requiresDetail("UNABLE_TO_EXAMINE")).isTrue();
        assertThat(ExaminationVocabulary.requiresDetail("DEFERRED")).isTrue();

        assertThat(ExaminationVocabulary.requiresDetail("NORMAL")).isFalse();
        assertThat(ExaminationVocabulary.requiresDetail("NOT_EXAMINED")).isFalse();
    }

    @Test
    void theExaminedAndDetailFreeStatesAreDifferentPartitions() {
        // They overlap on NORMAL and are otherwise unrelated: UNCERTAIN was examined but needs
        // detail, NOT_EXAMINED was not examined and needs none. Collapsing the two ideas into one
        // flag would force one of those two cases to be recorded wrongly.
        assertThat(ExaminationVocabulary.EXAMINED_STATES)
                .isNotEqualTo(ExaminationVocabulary.STATES_NEEDING_NO_DETAIL);
        assertThat(ExaminationVocabulary.EXAMINED_STATES).contains("UNCERTAIN");
        assertThat(ExaminationVocabulary.STATES_NEEDING_NO_DETAIL).doesNotContain("UNCERTAIN");
    }

    private static Set<String> checkValues(String constraintName) throws IOException {
        String sql = Files.readString(MIGRATION);
        int start = sql.indexOf("CONSTRAINT " + constraintName);
        assertThat(start)
                .withFailMessage("constraint %s not found in %s", constraintName, MIGRATION)
                .isGreaterThan(-1);

        int inStart = sql.indexOf("IN (", start);
        assertThat(inStart).isGreaterThan(-1);

        // Scan to the paren that actually closes the IN list rather than to the first "))".
        // The inherited helper looked for "))", which silently over-reads any constraint whose
        // parentheses close on separate lines — it would have swallowed the next constraint's values
        // and passed while doing it, since the assertion is only that the parsed set is non-empty.
        int depth = 0;
        int inEnd = -1;
        for (int i = inStart + "IN (".length() - 1; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    inEnd = i;
                    break;
                }
            }
        }
        assertThat(inEnd)
                .withFailMessage("unbalanced IN list for %s", constraintName)
                .isGreaterThan(inStart);

        Set<String> values = new LinkedHashSet<>();
        Matcher m = Pattern.compile("'([A-Z_]+)'").matcher(sql.substring(inStart, inEnd));
        while (m.find()) {
            values.add(m.group(1));
        }
        assertThat(values).withFailMessage("no values parsed for %s", constraintName).isNotEmpty();
        return values;
    }
}
