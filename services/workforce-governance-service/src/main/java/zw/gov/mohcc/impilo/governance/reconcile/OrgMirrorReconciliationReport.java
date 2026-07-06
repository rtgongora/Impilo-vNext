package zw.gov.mohcc.impilo.governance.reconcile;

import java.util.List;

/**
 * Criterion-1 evidence for the org-registry cutover soak: a diff of the
 * org-registry WGV mirror against local ACTIVE {@code wgv_organisation} rows.
 *
 * <p>Read this to decide whether the mirror is complete and drift-free. The
 * cutover (phase-2c) may proceed only when {@link #completenessPct()} is
 * {@code 100.0} AND {@link #driftRows()} is empty, sustained across the agreed
 * soak window. An empty mirror honestly reports {@code 0.0}% with every active
 * source-ref listed as missing.
 *
 * @param wgvActiveTotal    count of local ACTIVE {@code wgv_organisation} rows
 * @param mirroredCount     how many of those have a matching mirror row (by source_ref)
 * @param missingSourceRefs wgv organisation ids with no mirror row yet
 * @param driftRows         mirrored rows whose code / legalName / status differ
 * @param completenessPct   {@code mirroredCount / wgvActiveTotal * 100} (100.0 when there are no active rows)
 */
public record OrgMirrorReconciliationReport(
        long wgvActiveTotal,
        long mirroredCount,
        List<String> missingSourceRefs,
        List<DriftRow> driftRows,
        double completenessPct) {

    /**
     * A mirrored row that disagrees with its authoritative wgv counterpart.
     *
     * @param sourceRef   the wgv organisation id (= mirror source_ref)
     * @param driftFields which of {@code code}, {@code legalName}, {@code status} differ
     */
    public record DriftRow(String sourceRef, List<String> driftFields) {
    }
}
