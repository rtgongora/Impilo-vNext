package zw.gov.mohcc.impilo.clinical.maternal;

import java.util.List;

/**
 * Reduces a series of self-measured blood-pressure readings to the facts an escalation rule needs:
 * the mean, the worst single reading, and whether enough was measured to conclude anything.
 *
 * <p>Pure. It does not fetch readings and it does not store them — where readings arrive from a home
 * device they are persisted and served by telemonitoring-service, the system of record for that, and
 * this reducer consumes what it is handed rather than re-ingesting. What it owns is the clinical
 * reduction, which telemonitoring deliberately does not do: it alerts on single readings, not on a
 * series average.
 *
 * <p>Three safety properties are built in, and each is a way self-monitoring goes wrong:
 *
 * <ul>
 *   <li><b>A severe reading is never averaged away.</b> Home BP is interpreted on the mean, because a
 *       single high reading is often the rest-and-anxiety artefact the protocol exists to remove. But
 *       a reading in the severe range (160/110) is an emergency whether or not the average is calm,
 *       so {@code anySevere} scans every reading, including ones discarded from the mean. Averaging
 *       first and checking the average is exactly how a severe reading disappears into a normal week.</li>
 *   <li><b>The first reading of a session is discarded from the MEAN, never from the severe check.</b>
 *       WHO protocol drops the first reading as a rest artefact when computing the average. That
 *       discard applies to the mean only — a severe first reading is still severe.</li>
 *   <li><b>Too few readings is not a normal result.</b> Below the minimum the series is
 *       {@code insufficient}, and the escalation must say "not enough to conclude", never "controlled".
 *       An average of two readings is not a week of monitoring.</li>
 * </ul>
 */
public final class HomeBpSeriesReducer {

    private HomeBpSeriesReducer() {
    }

    /**
     * Home-BP thresholds. Home readings use lower cut-offs than clinic readings because the
     * white-coat effect is absent — the values are an engineering seed pending ratification, held
     * here rather than hardcoded in the arithmetic so a ministry parameter can replace them.
     */
    public record Thresholds(
            int homeHypertensionSystolic,
            int homeHypertensionDiastolic,
            int severeSystolic,
            int severeDiastolic,
            int minimumReadingsToConclude) {

        /** WHO SMBP / home-BP seed values: 135/85 elevated, 160/110 severe, 12 readings to conclude. */
        public static Thresholds seed() {
            return new Thresholds(135, 85, 160, 110, 12);
        }
    }

    /**
     * One home reading.
     *
     * @param firstOfSession true if this is the first reading of a sitting; discarded from the mean
     *                       as a rest artefact, but still counted for the severe check
     */
    public record Reading(int systolic, int diastolic, boolean firstOfSession) {
    }

    /**
     * @param readingCount    every reading supplied
     * @param meanCount       readings that counted toward the mean (first-of-session excluded)
     * @param meanSystolic    mean of the counted readings, or null when none counted
     * @param anySevere       any single reading in the severe range, discard flag notwithstanding
     * @param elevatedMean    the mean is at or above the home-hypertension threshold
     * @param sufficient      enough readings counted to draw a conclusion
     */
    public record Summary(
            int readingCount,
            int meanCount,
            Integer meanSystolic,
            Integer meanDiastolic,
            int maxSystolic,
            int maxDiastolic,
            boolean anySevere,
            boolean elevatedMean,
            boolean sufficient) {

        /** The reduced facts, for injection into the escalation rule evaluation under the smbp.* prefix. */
        public java.util.Map<String, Object> toFacts() {
            java.util.Map<String, Object> f = new java.util.LinkedHashMap<>();
            f.put("smbp.readingCount", readingCount);
            f.put("smbp.meanCount", meanCount);
            if (meanSystolic != null) {
                f.put("smbp.meanSystolic", meanSystolic);
                f.put("smbp.meanDiastolic", meanDiastolic);
            }
            f.put("smbp.maxSystolic", maxSystolic);
            f.put("smbp.maxDiastolic", maxDiastolic);
            f.put("smbp.anySevere", anySevere);
            f.put("smbp.elevatedMean", elevatedMean);
            f.put("smbp.sufficient", sufficient);
            return f;
        }
    }

    public static Summary reduce(List<Reading> readings, Thresholds thresholds) {
        Thresholds t = thresholds == null ? Thresholds.seed() : thresholds;
        if (readings == null || readings.isEmpty()) {
            return new Summary(0, 0, null, null, 0, 0, false, false, false);
        }

        int sumSys = 0;
        int sumDia = 0;
        int meanCount = 0;
        int maxSys = Integer.MIN_VALUE;
        int maxDia = Integer.MIN_VALUE;
        boolean anySevere = false;

        for (Reading r : readings) {
            // The severe check sees every reading, discarded or not — an emergency is not a rest
            // artefact.
            if (r.systolic() >= t.severeSystolic() || r.diastolic() >= t.severeDiastolic()) {
                anySevere = true;
            }
            maxSys = Math.max(maxSys, r.systolic());
            maxDia = Math.max(maxDia, r.diastolic());

            // The mean excludes the first reading of each session.
            if (!r.firstOfSession()) {
                sumSys += r.systolic();
                sumDia += r.diastolic();
                meanCount++;
            }
        }

        Integer meanSys = meanCount > 0 ? Math.round((float) sumSys / meanCount) : null;
        Integer meanDia = meanCount > 0 ? Math.round((float) sumDia / meanCount) : null;
        boolean elevated = meanSys != null
                && (meanSys >= t.homeHypertensionSystolic() || meanDia >= t.homeHypertensionDiastolic());
        boolean sufficient = meanCount >= t.minimumReadingsToConclude();

        return new Summary(readings.size(), meanCount, meanSys, meanDia,
                maxSys, maxDia, anySevere, elevated, sufficient);
    }
}
