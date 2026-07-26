package zw.gov.mohcc.impilo.clinical.growth;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Governed growth-interpretation content.
 *
 * <p>The thresholds and the actions are clinical policy and change independently of the arithmetic
 * that produces z-scores, which is why they live here as data rather than in the engine. A change
 * to when a clinic should escalate faltering is a content diff a clinician can read.</p>
 *
 * @param signals ordered by priority; data-quality signals carry the lowest numbers because a
 *                measurement in question must be resolved before its trend means anything
 */
public record GrowthIntelligenceContent(
        String packId,
        String version,
        String approvalStatus,
        String adaptationAuthority,
        String provenance,
        List<SignalDefinition> signals,
        List<TestCase> testCases) {

    public boolean blocksInterpretation(String code) {
        return signals.stream()
                .filter(s -> s.code().equals(code))
                .anyMatch(SignalDefinition::blocksInterpretation);
    }

    /**
     * One thing worth saying about a growth trajectory.
     *
     * @param zDropAtLeast              a fall of this magnitude or more in the named measure
     * @param zRiseAtLeast              a rise of this magnitude or more
     * @param zChangeMagnitudeAtLeast   a change in either direction
     * @param staticWeightMinDays       no weight gained across at least this many days
     * @param absoluteWeightLoss        weight lower than at the previous contact
     * @param statureModeChanged        the child switched between being measured lying and standing
     * @param family                    signals in the same family are a severity ladder, not
     *                                  independent findings: only the most severe applicable one is
     *                                  reported, because a clinician needs one message about the
     *                                  weight trend at the right severity, not four at once
     * @param blocksInterpretation      when true, this signal suppresses clinical signals entirely
     *                                  rather than sitting alongside them
     */
    public record SignalDefinition(
            String code,
            String name,
            String severity,
            int priority,
            String measure,
            String family,
            BigDecimal zDropAtLeast,
            BigDecimal zRiseAtLeast,
            BigDecimal zChangeMagnitudeAtLeast,
            Integer staticWeightMinDays,
            boolean absoluteWeightLoss,
            boolean statureModeChanged,
            Integer maxAgeDays,
            String action,
            boolean referralRequired,
            boolean blocksInterpretation) {
    }

    /** A trajectory shipped with the content and executed against the engine as a build gate. */
    public record TestCase(
            String name,
            List<Map<String, Object>> points,
            List<String> expectSignals,
            boolean expectReferral,
            boolean expectBlocked,
            boolean expectInsufficient) {
    }
}
