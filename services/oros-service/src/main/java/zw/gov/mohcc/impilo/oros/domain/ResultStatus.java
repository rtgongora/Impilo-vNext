package zw.gov.mohcc.impilo.oros.domain;

/**
 * Reporting lifecycle status of a diagnostic result/report (spec §10).
 *
 * <p>A final report is never overwritten: an amendment or addendum produces a <em>new</em>
 * versioned result row that supersedes the prior one, preserving the full history chain.</p>
 */
public enum ResultStatus {
    PRELIMINARY,
    FINAL,
    AMENDED,
    ADDENDUM,
    CORRECTED
}
