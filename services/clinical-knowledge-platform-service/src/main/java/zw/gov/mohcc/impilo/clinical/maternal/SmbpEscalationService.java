package zw.gov.mohcc.impilo.clinical.maternal;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a series of self-measured blood-pressure readings into one action for the woman.
 *
 * <p>Two steps, kept separate on purpose. {@link HomeBpSeriesReducer} does the arithmetic — mean,
 * worst reading, enough-data — and {@link MaternalDangerSignService} evaluates the escalation rules
 * over the reduced facts, so the rules stay reviewable content and the reducer stays pure. This
 * service only composes them and decides the two verdicts that are not alerts: controlled, and
 * not-enough-data.
 *
 * <p><b>Not-enough-data is never "controlled".</b> If nothing escalates and the series is
 * insufficient, the verdict is {@link Verdict#INSUFFICIENT_DATA}, not {@link Verdict#CONTROLLED} —
 * a quiet week of two readings is not evidence that her blood pressure is fine, and telling her it is
 * is how a woman stops monitoring right before it matters.
 */
@Service
public class SmbpEscalationService {

    static final String CONTENT_PATH = "clinical/rmnp-smbp-escalation.json";

    private final MaternalDangerSignService dangerSignService;

    public SmbpEscalationService(MaternalDangerSignService dangerSignService) {
        this.dangerSignService = dangerSignService;
    }

    public enum Verdict {
        /** A danger sign or a severe reading — go to a facility now. */
        URGENT,
        /** Sustained elevated average over enough readings — come in for assessment. */
        ELEVATED_REFER,
        /** Enough readings, none elevated, none severe, no danger sign — keep monitoring. */
        CONTROLLED,
        /** Too few readings to conclude. Explicitly not CONTROLLED. */
        INSUFFICIENT_DATA
    }

    public record Result(
            Verdict verdict,
            HomeBpSeriesReducer.Summary summary,
            List<MaternalDangerSignService.Alert> alerts,
            String message,
            String note,
            String contentVersion,
            String approvalStatus) {
    }

    /**
     * @param readings          the home readings, first-of-session flagged where known
     * @param dangerSignPresent whether the woman reported any pregnancy danger sign; passed
     *                          explicitly rather than inferred, and null-safe — a danger sign that
     *                          was never asked about is not the same as one that was denied
     * @param thresholds        home-BP thresholds, or null for the seed values
     */
    public Result assess(List<HomeBpSeriesReducer.Reading> readings,
                         Boolean dangerSignPresent,
                         HomeBpSeriesReducer.Thresholds thresholds) {
        HomeBpSeriesReducer.Summary summary = HomeBpSeriesReducer.reduce(readings, thresholds);

        Map<String, Object> facts = new LinkedHashMap<>(summary.toFacts());
        // Only assert a danger sign when one was actually reported. Absent means "reported none";
        // a null (never asked) leaves the fact unset, so the rule reads UNKNOWN rather than false.
        if (Boolean.TRUE.equals(dangerSignPresent)) {
            facts.put("dangerSignReported", "PRESENT");
        } else if (Boolean.FALSE.equals(dangerSignPresent)) {
            facts.put("dangerSignReported", "ABSENT");
        }

        MaternalDangerSignService.Assessment assessment =
                dangerSignService.evaluate(CONTENT_PATH, facts, true);

        Verdict verdict = decide(assessment, summary);
        return new Result(
                verdict, summary, assessment.alerts(),
                message(verdict, assessment), note(verdict, summary),
                assessment.contentVersion(), assessment.approvalStatus());
    }

    private static Verdict decide(MaternalDangerSignService.Assessment assessment,
                                  HomeBpSeriesReducer.Summary summary) {
        boolean urgent = assessment.alerts().stream()
                .anyMatch(a -> "CRITICAL".equals(a.severity()));
        if (urgent) {
            return Verdict.URGENT;
        }
        if (!assessment.alerts().isEmpty()) {
            return Verdict.ELEVATED_REFER;
        }
        // Nothing escalated. Whether that is reassurance depends entirely on whether there was
        // enough to look at.
        return summary.sufficient() ? Verdict.CONTROLLED : Verdict.INSUFFICIENT_DATA;
    }

    private static String message(Verdict verdict, MaternalDangerSignService.Assessment assessment) {
        return switch (verdict) {
            case URGENT, ELEVATED_REFER -> assessment.alerts().get(0).message();
            case CONTROLLED -> "Blood pressure is controlled over enough readings. Keep monitoring, "
                    + "and keep watching for the danger signs of pregnancy.";
            case INSUFFICIENT_DATA -> "Not enough readings yet to say whether the blood pressure is "
                    + "controlled. Keep monitoring — this is not a result.";
        };
    }

    private static String note(Verdict verdict, HomeBpSeriesReducer.Summary summary) {
        if (verdict == Verdict.INSUFFICIENT_DATA) {
            return summary.meanCount() + " reading(s) counted toward the average; more are needed "
                    + "before a controlled verdict is meaningful.";
        }
        if (verdict == Verdict.CONTROLLED) {
            return "Mean " + summary.meanSystolic() + "/" + summary.meanDiastolic()
                    + " over " + summary.meanCount() + " readings.";
        }
        return "Worst reading " + summary.maxSystolic() + "/" + summary.maxDiastolic() + ".";
    }
}
