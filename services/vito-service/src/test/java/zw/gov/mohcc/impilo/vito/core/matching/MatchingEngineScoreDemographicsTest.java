package zw.gov.mohcc.impilo.vito.core.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.MatchResultRepository;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Regression for the demographic-score ceiling: the raw weight table reserves
 * 0.25 for phoneHash, which demographics-only callers never supply, so a
 * PERFECT four-field match scored 0.75 — below the 0.95 auto-link threshold —
 * and demographic dedupe could never fire anywhere (caught live by the D1
 * double-register proof). scoreDemographics must renormalize over the fields
 * it can actually provide.
 */
class MatchingEngineScoreDemographicsTest {

    private MatchingEngine engine;
    private ClientEntity candidate;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine(mock(ClientRepository.class), mock(MatchResultRepository.class),
                new VitoProperties(), new ObjectMapper());
        candidate = new ClientEntity();
        candidate.setGivenName("Dedupe");
        candidate.setFamilyName("Proofcase");
        candidate.setDateOfBirth(LocalDate.parse("1992-02-02"));
        candidate.setSex("F");
    }

    @Test
    @DisplayName("a perfect four-field match scores 1.0 (>= auto-link threshold), not the 0.75 ceiling")
    void perfectMatchClearsAutoLinkThreshold() {
        double score = engine.scoreDemographics("Dedupe", "Proofcase", "1992-02-02", "F", candidate);
        assertEquals(1.0, score, 1e-9);
        assertTrue(score >= new VitoProperties().getMatching().getThresholdAutoLink());
    }

    @Test
    @DisplayName("a clear non-match stays far below the threshold after renormalization")
    void nonMatchStaysBelowThreshold() {
        double score = engine.scoreDemographics("Zanele", "Moyo", "1961-11-30", "M", candidate);
        assertTrue(score < 0.7, "renormalization must not inflate non-matches, got " + score);
    }

    @Test
    @DisplayName("date-of-birth mismatch alone drops the score below auto-link")
    void dobMismatchBlocksAutoLink() {
        double score = engine.scoreDemographics("Dedupe", "Proofcase", "1993-03-03", "F", candidate);
        assertTrue(score < new VitoProperties().getMatching().getThresholdAutoLink(),
                "dob is the heaviest field; mismatch must block auto-link, got " + score);
    }
}
