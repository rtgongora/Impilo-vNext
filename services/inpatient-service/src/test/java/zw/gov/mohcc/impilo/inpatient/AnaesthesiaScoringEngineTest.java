package zw.gov.mohcc.impilo.inpatient;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.inpatient.core.AnaesthesiaScoringEngine;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnaesthesiaScoringEngineTest {

    @Test
    void stop_bang_calculates_risk_band() {
        var result = AnaesthesiaScoringEngine.calculate("STOP_BANG", "PREOP", Map.of(
                "snoring", true, "tired", true, "observedApnea", true,
                "pressure", true, "bmiGreaterThan35", true));
        assertEquals(5, result.totalScore());
        assertEquals("HIGH", result.riskBand());
    }

    @Test
    void apfel_interprets_ponv_risk() {
        var result = AnaesthesiaScoringEngine.calculate("APFEL", "PREOP", Map.of(
                "female", true, "nonSmoker", true, "historyPonv", true, "postopOpioids", true));
        assertEquals(4, result.totalScore());
        assertEquals("HIGH", result.riskBand());
    }

    @Test
    void suggests_rcri_for_cardiac_procedure() {
        List<AnaesthesiaScoringEngine.ScoreSuggestion> suggestions =
                AnaesthesiaScoringEngine.suggestScores("Coronary artery bypass", "CABG", "III");
        assertTrue(suggestions.stream().anyMatch(s -> "RCRI".equals(s.scoreType()) && s.recommended()));
        assertTrue(suggestions.stream().anyMatch(s -> "ASA".equals(s.scoreType())));
    }

    @Test
    void mallampati_iv_is_high_risk() {
        var result = AnaesthesiaScoringEngine.calculate("MALLAMPATI", "PREOP", Map.of("mallampatiClass", "IV"));
        assertEquals("HIGH", result.riskBand());
        assertTrue(result.interpretation().contains("difficult intubation"));
    }

    @Test
    void intraop_vitals_flags_abnormal() {
        var result = AnaesthesiaScoringEngine.calculate("INTRAOP_VITALS", "INTRAOP", Map.of(
                "heartRate", 120, "systolicBp", 85, "spo2", 92));
        assertTrue(result.totalScore() >= 1);
    }
}
