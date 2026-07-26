package zw.gov.mohcc.impilo.clinical.immunisation;

import java.util.List;

/**
 * The national immunisation schedule as governed content.
 *
 * <p>The schedule is data rather than code because the EPI programme owns it. Antigens are added,
 * timings shift, and a vaccine is withdrawn or introduced by programme decision — none of which
 * should require a deployment, and all of which must be reviewable as a diff by the people
 * accountable for it.</p>
 *
 * @param codeSystem the system the series identifiers belong to. Declared locally today because
 *                   zibo-service holds no vaccine terminology yet; when it does, this points at
 *                   the governed Zibo value set and these become the local mapping.
 */
public record EpiSchedule(
        String scheduleId,
        String name,
        String version,
        String approvalStatus,
        String adaptationAuthority,
        String codeSystem,
        String provenance,
        List<Antigen> antigens) {

    /**
     * @param appliesToSex null when the antigen is scheduled for every child; otherwise the sex the
     *                     national schedule targets
     * @param safetyNote   why a window closes, where closing it is a safety decision rather than a
     *                     programmatic one — surfaced verbatim when a dose ages out
     */
    public record Antigen(
            String series,
            String name,
            String protectsAgainst,
            String route,
            String appliesToSex,
            String safetyNote,
            List<Dose> doses) {
    }

    /**
     * One dose in a series.
     *
     * <p>The three age fields answer different questions and conflating them is how forecasts go
     * wrong. {@code minimumAgeDays} is validity — earlier does not immunise.
     * {@code recommendedAgeDays} is the programme's target. {@code overdueAfterAgeDays} is when a
     * missed dose becomes a defaulter-tracing concern. {@code latestUsefulAgeDays} is different
     * again: the point after which the dose is not given at all.</p>
     *
     * @param minimumIntervalDays days that must have passed since the previous valid dose in the
     *                            series; a separate gate from age, and the one most often missed
     */
    public record Dose(
            Integer doseNumber,
            Integer recommendedAgeDays,
            Integer minimumAgeDays,
            Integer minimumIntervalDays,
            Integer overdueAfterAgeDays,
            Integer latestUsefulAgeDays,
            String note) {
    }
}
