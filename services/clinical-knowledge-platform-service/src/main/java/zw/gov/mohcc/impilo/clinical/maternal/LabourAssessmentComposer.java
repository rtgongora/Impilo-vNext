package zw.gov.mohcc.impilo.clinical.maternal;

import java.util.List;

/**
 * Combines the two layers that read a labour — the Labour Care Guide progress verdict and the LCG
 * parameter danger-sign layer — into one top-line action.
 *
 * <p>The rule it enforces is an agreement rule: a progress verdict may never contradict the danger-
 * sign layer. A woman whose labour is progressing slowly AND whose fetal heart is abnormal must be
 * shown the fetal-heart emergency as the top line, not "slow progress, offer support" — the gentle,
 * correct progress message becomes a lethal distraction the moment a danger sign is also present.
 * One labour, one top-line action, and the more urgent layer wins it.
 */
public final class LabourAssessmentComposer {

    private LabourAssessmentComposer() {
    }

    /**
     * @param topLineAction    the single action to lead with
     * @param source           which layer produced it — DANGER_SIGN or PROGRESS
     * @param monitoringStandard always the Labour Care Guide; never the demoted classic partograph
     */
    public record TopLine(String topLineAction, String source, String monitoringStandard) {
    }

    public static final String SOURCE_DANGER_SIGN = "DANGER_SIGN";
    public static final String SOURCE_PROGRESS = "PROGRESS";

    /**
     * @param progress   the LCG progress verdict
     * @param dangerAlerts the LCG parameter danger-sign alerts, most severe first (as
     *                     {@link MaternalDangerSignService} orders them)
     */
    public static TopLine topLine(LabourCareGuideProgressEngine.Assessment progress,
                                  List<MaternalDangerSignService.Alert> dangerAlerts) {
        // A CRITICAL danger sign always wins the top line, whatever progress says. This is the
        // agreement rule: progress never contradicts or buries a danger sign.
        if (dangerAlerts != null) {
            for (MaternalDangerSignService.Alert alert : dangerAlerts) {
                if ("CRITICAL".equals(alert.severity())) {
                    return new TopLine(alert.requiredAction(), SOURCE_DANGER_SIGN,
                            LabourCareGuideProgressEngine.MONITORING_STANDARD);
                }
            }
            // Then any other danger sign, before a routine progress line.
            if (!dangerAlerts.isEmpty()) {
                return new TopLine(dangerAlerts.get(0).requiredAction(), SOURCE_DANGER_SIGN,
                        LabourCareGuideProgressEngine.MONITORING_STANDARD);
            }
        }
        String action = progress == null
                ? "Assess the labour: no progress verdict is available."
                : progress.action();
        return new TopLine(action, SOURCE_PROGRESS, LabourCareGuideProgressEngine.MONITORING_STANDARD);
    }
}
