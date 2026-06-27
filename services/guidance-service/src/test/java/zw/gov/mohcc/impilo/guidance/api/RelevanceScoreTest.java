package zw.gov.mohcc.impilo.guidance.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** G050 — search relevance is now a real lexical score, not a hardcoded 1.0. */
class RelevanceScoreTest {

    @Test
    void titleMatchOutranksSummaryMatchOutranksNoMatch() {
        double titleHit = GuidanceApiController.relevanceScore("malaria", "Malaria treatment", "Fever guidance");
        double summaryHit = GuidanceApiController.relevanceScore("malaria", "Fever guidance", "Malaria notes");
        double noHit = GuidanceApiController.relevanceScore("malaria", "Diabetes care", "Insulin dosing");

        assertThat(titleHit).isGreaterThan(summaryHit);
        assertThat(summaryHit).isGreaterThan(noHit);
        assertThat(noHit).isZero();
        assertThat(titleHit).isLessThanOrEqualTo(1.0);
    }

    @Test
    void blankQueryScoresZero() {
        assertThat(GuidanceApiController.relevanceScore("", "anything", "anything")).isZero();
        assertThat(GuidanceApiController.relevanceScore(null, "anything", "anything")).isZero();
    }

    @Test
    void partialMultiTermMatchScoresBetweenZeroAndOne() {
        // 1 of 2 terms hits the title → between 0 and 1
        double score = GuidanceApiController.relevanceScore("malaria xyzzy", "Malaria treatment", "notes");
        assertThat(score).isGreaterThan(0.0).isLessThan(1.0);
    }
}
