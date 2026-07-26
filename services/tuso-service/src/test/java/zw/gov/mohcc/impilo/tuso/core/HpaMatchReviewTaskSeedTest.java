package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAR W7 guard tests — the disputed matches must stay disputed until a person says otherwise.
 *
 * <p>These 591 rows exist because HPA's own matcher refused to commit to them. The whole value of
 * seeding them as tasks is that the withheld locality and address are released by a registrar's
 * judgement, not by code deciding the matcher was too cautious.</p>
 */
class HpaMatchReviewTaskSeedTest {

    /** Only the four statuses the matcher withheld. A corroborated match is not a review task. */
    @Test
    void onlyDisputedMatchesBecomeTasks() {
        assertThat(HpaMatchReviewTaskSeedService.DISPUTED_MATCH_STATUSES)
                .containsExactlyInAnyOrder(
                        "REVIEW_EXACT_REG_IDENTITY_CONFLICT",
                        "REVIEW_EXACT_REG_POSSIBLE_RENAME",
                        "REVIEW_EXACT_NAME_PROVINCE_REG_CHANGED",
                        "AMBIGUOUS_REGISTRATION_KEY");
        assertThat(HpaMatchReviewTaskSeedService.DISPUTED_MATCH_STATUSES)
                .as("the 2,294 certain matches are already enriched and need no review")
                .doesNotContain("AUTO_EXACT_REG_CORROBORATED")
                .as("3,442 rows have no legacy record at all — there is nothing to adjudicate")
                .doesNotContain("CURRENT_ONLY_NO_SQL_MATCH");
    }

    /**
     * An identity conflict risks confusing two real facilities with each other; a suspected rename
     * risks a stale name. They must not carry the same priority.
     */
    @Test
    void identityConflictOutranksASuspectedRename() {
        assertThat(HpaMatchReviewTaskSeedService.priorityFor("REVIEW_EXACT_REG_IDENTITY_CONFLICT"))
                .isEqualTo("HIGH");
        assertThat(HpaMatchReviewTaskSeedService.priorityFor("REVIEW_EXACT_REG_POSSIBLE_RENAME"))
                .isEqualTo("MEDIUM");
    }

    /** The task text must state the consequence, so a reviewer knows what their decision releases. */
    @Test
    void theTaskExplainsWhatIsBeingWithheldAndWhy() {
        var conflict = new HpaMatchReviewTaskSeedService.DisputedMatch(
                "REVIEW_EXACT_REG_IDENTITY_CONFLICT", "EXACT_REG", "Low",
                "4711", "Parirenyatwa Dental Unit", "HP-100",
                "Parirenyatwa Dental", "L-100", "AVENUES", "12 Baines Avenue");

        assertThat(HpaMatchReviewTaskSeedService.whyRequired(conflict))
                .contains("wrong facility")
                .contains("withheld");
        assertThat(HpaMatchReviewTaskSeedService.acceptedEvidence(conflict))
                .contains("Parirenyatwa Dental Unit")
                .contains("L-100")
                .contains("releases the legacy locality");
    }

    /** The required-information text keys the uniqueness constraint, so it must name the record. */
    @Test
    void theTaskKeyNamesTheDisputedLegacyRecord() {
        var rename = new HpaMatchReviewTaskSeedService.DisputedMatch(
                "REVIEW_EXACT_REG_POSSIBLE_RENAME", null, null,
                "99", "New Name Clinic", "HP-2", "Old Name Clinic", "L-2", "MUTARE", null);
        String key = HpaMatchReviewTaskSeedService.requiredInformation(rename);
        assertThat(key).contains("L-2").contains("REVIEW_EXACT_REG_POSSIBLE_RENAME");
    }

    // ── the CSV reader, because the bundle's quoting will break a naive split ──

    @Test
    void quotedFieldsWithCommasSurviveParsing() {
        List<String> parsed = HpaMatchReviewTaskSeedService.parseCsvLine(
                "a,\"12 Baines Avenue, Suite 8, Harare\",c");
        assertThat(parsed).containsExactly("a", "12 Baines Avenue, Suite 8, Harare", "c");
    }

    @Test
    void doubledQuotesAreUnescaped() {
        assertThat(HpaMatchReviewTaskSeedService.parseCsvLine("\"He said \"\"yes\"\"\",b"))
                .containsExactly("He said \"yes\"", "b");
    }

    /**
     * The importer stores the deterministic identifier with its source prefix; the crosswalk carries
     * a bare id. Comparing them raw matched zero of 591 rows on live data.
     */
    @Test
    void institutionIdsAreMatchedWithTheirSourcePrefix() {
        assertThat(HpaMatchReviewTaskSeedService.identifierValue("3892")).isEqualTo("HPA-3892");
        assertThat(HpaMatchReviewTaskSeedService.identifierValue(" 1778 ")).isEqualTo("HPA-1778");
        // Idempotent if the source ever starts sending the prefixed form.
        assertThat(HpaMatchReviewTaskSeedService.identifierValue("HPA-2589")).isEqualTo("HPA-2589");
        assertThat(HpaMatchReviewTaskSeedService.identifierValue("  ")).isNull();
        assertThat(HpaMatchReviewTaskSeedService.identifierValue(null)).isNull();
    }

    /**
     * A CSV record is not a line. The crosswalk's free-text columns contain newlines inside quoted
     * fields; reading line-by-line turned a 6,327-record file into 7,852 garbage rows.
     */
    @Test
    void recordsSpanningNewlinesAreReadAsOneRecord() throws Exception {
        String csv = "a,\"note\nwith newline\",c\nd,e,f\n";
        var reader = new java.io.BufferedReader(new java.io.StringReader(csv));
        String first = HpaMatchReviewTaskSeedService.readRecord(reader);
        String second = HpaMatchReviewTaskSeedService.readRecord(reader);

        assertThat(HpaMatchReviewTaskSeedService.parseCsvLine(first))
                .containsExactly("a", "note\nwith newline", "c");
        assertThat(HpaMatchReviewTaskSeedService.parseCsvLine(second)).containsExactly("d", "e", "f");
        assertThat(HpaMatchReviewTaskSeedService.readRecord(reader)).isNull();
    }

    @Test
    void quoteBalanceDetectsAnUnterminatedField() {
        assertThat(HpaMatchReviewTaskSeedService.unbalancedQuotes("a,\"open")).isTrue();
        assertThat(HpaMatchReviewTaskSeedService.unbalancedQuotes("a,\"closed\",c")).isFalse();
        assertThat(HpaMatchReviewTaskSeedService.unbalancedQuotes("plain,row")).isFalse();
    }

    /** The bundle's CSVs carry a BOM; unstripped it corrupts the first column name silently. */
    @Test
    void theByteOrderMarkIsStripped() {
        assertThat(HpaMatchReviewTaskSeedService.stripBom("﻿MatchStatus")).isEqualTo("MatchStatus");
        assertThat(HpaMatchReviewTaskSeedService.stripBom("MatchStatus")).isEqualTo("MatchStatus");
    }
}
