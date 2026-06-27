package zw.gov.mohcc.impilo.ia.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, server-side risk evaluation. The caller reports observed risk <b>signals</b>
 * (factors); the engine computes the score, level and recommendation. The verdict is NEVER
 * client-supplied — this is what closes the previous pass-through where a caller could declare
 * itself LOW/ALLOW.
 *
 * <p>Each known factor carries a weight; the score is the capped sum, the level is derived from
 * thresholds, and the recommendation from the level. Unknown signals add a small conservative
 * weight rather than being ignored (an unrecognised signal is mild evidence of risk, not none).</p>
 */
public final class RiskEngine {

    private static final int UNKNOWN_FACTOR_WEIGHT = 5;
    private static final int MAX_SCORE = 100;

    /** Catalogue of recognised risk signals and their weights. */
    private static final Map<String, Integer> FACTOR_WEIGHTS = Map.ofEntries(
            Map.entry("NEW_DEVICE", 20),
            Map.entry("UNRECOGNIZED_LOCATION", 25),
            Map.entry("IMPOSSIBLE_TRAVEL", 40),
            Map.entry("MULTIPLE_FAILED_AUTH", 30),
            Map.entry("UNVERIFIED_IDENTITY", 25),
            Map.entry("ELEVATED_PRIVILEGE_REQUEST", 20),
            Map.entry("OFF_HOURS_ACCESS", 10),
            Map.entry("SHARED_DEVICE", 15),
            Map.entry("TEMP_CREDENTIAL", 15),
            Map.entry("SENSITIVE_DATA_ACCESS", 20),
            Map.entry("KNOWN_COMPROMISED_CREDENTIAL", 60)
    );

    private RiskEngine() {
    }

    public record Result(BigDecimal score, RiskLevel level, Recommendation recommendation) {}

    public static Result evaluate(List<String> factors) {
        int total = 0;
        if (factors != null) {
            for (String f : factors) {
                if (f == null || f.isBlank()) {
                    continue;
                }
                total += FACTOR_WEIGHTS.getOrDefault(f.trim().toUpperCase(), UNKNOWN_FACTOR_WEIGHT);
            }
        }
        int score = Math.min(MAX_SCORE, total);
        RiskLevel level = levelFor(score);
        return new Result(BigDecimal.valueOf(score), level, recommendationFor(level));
    }

    private static RiskLevel levelFor(int score) {
        if (score >= 75) {
            return RiskLevel.CRITICAL;
        }
        if (score >= 50) {
            return RiskLevel.HIGH;
        }
        if (score >= 25) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private static Recommendation recommendationFor(RiskLevel level) {
        return switch (level) {
            case LOW -> Recommendation.ALLOW;
            case MEDIUM -> Recommendation.STEP_UP;
            case HIGH -> Recommendation.REVIEW;
            case CRITICAL -> Recommendation.DENY;
        };
    }
}
