package zw.gov.mohcc.impilo.shared.visibility;

import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.ExportPolicy;

/**
 * Policy-aware gate for materialized exports (report runs, bulk extracts, etc.).
 * When trust headers indicate aggregate-only or export is prohibited, row-level
 * report execution must not proceed server-side.
 */
public final class ExportVisibilityGuard {

    private ExportVisibilityGuard() {
    }

    /**
     * @return true when the current visibility profile forbids running a report that may
     *         return row-level or identified operational data.
     */
    public static boolean deniesReportRun(VisibilityProfile profile) {
        if (profile == null) {
            return false;
        }
        ExportPolicy export = ExportPolicy.fromString(profile.exportPolicy());
        if (export == ExportPolicy.PROHIBITED) {
            return true;
        }
        if (export == ExportPolicy.AGGREGATE_ONLY) {
            return true;
        }
        return AggregateVisibilityGuard.blocksRowLevelDetail(profile);
    }

    /**
     * When true, report payloads should be post-processed (PII masking / column redaction)
     * while still allowing the run to complete (unlike {@link #deniesReportRun}).
     */
    public static boolean requiresRedactedReportOutput(VisibilityProfile profile) {
        if (profile == null) {
            return false;
        }
        ExportPolicy export = ExportPolicy.fromString(profile.exportPolicy());
        if (export == ExportPolicy.REDACTED) {
            return true;
        }
        return profile.suppressFields() != null && !profile.suppressFields().isEmpty();
    }
}
