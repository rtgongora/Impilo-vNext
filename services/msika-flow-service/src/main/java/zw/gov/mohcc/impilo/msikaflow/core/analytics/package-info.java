/**
 * OF-B29 (Wave OF-D) — the anomaly-detection + marketplace-fairness half of
 * the fraud/anomaly/fairness epic (Vol II §13.3/§13.7/§21).
 *
 * <h2>Architecture decision (this lane, binding)</h2>
 * Monitors run as <b>scheduled sweeps inside msika-flow</b> over the service's
 * own tables plus consumed-event projections ({@code mf_reservations} rides
 * {@code inventory.reservation.status_changed}). No separate analytics
 * service exists or is created: msika-flow owns the marketplace transaction
 * truth these monitors read, sweeps stay deterministic and unit-testable, and
 * a second service would duplicate read models while adding zero new truth.
 * The documented future seam is <b>event consumption</b>: every finding emits
 * {@code msika.flow.anomaly.detected.v1} (coded, PII-free), which
 * analytics-pipeline-service / Rito / ROM worklists can subscribe to without
 * ever reading these tables.
 *
 * <h2>Accountability model</h2>
 * Findings persist in {@code mf_anomaly_findings} with a coded type, subject
 * refs, and an OPEN → REVIEWED → DISMISSED lifecycle bound to a reviewer and
 * a mandatory reason — worklist items, never fire-and-forget logs (§13.7,
 * §21.5 "a named operational duty, not an unowned dashboard"). Automated
 * findings never auto-alter clinical or marketplace records.
 *
 * <h2>Honest deferrals (future lanes)</h2>
 * <ul>
 *   <li>Statistical/model-based scoring (velocity baselines, bid-clustering
 *       collusion screens, steering graphs) — this lane ships deterministic
 *       rule + threshold monitors only.</li>
 *   <li>Cross-service correlation feeds (pharmacy dispense episodes, Nhume
 *       custody chains, DURA ledger divergence) — marketplace-side views only
 *       here; each SoR sweeps its own side.</li>
 *   <li>Rito/ROM escalation wiring — findings emit the versioned event; the
 *       worklist routing is the consumer's lane.</li>
 *   <li>Commitment-time ranking-snapshot capture — snapshots are captured
 *       best-effort at sweep cadence; absence at audit time is itself flagged
 *       ({@code RANKING_SNAPSHOT_MISSING}) rather than silently skipped.</li>
 * </ul>
 */
package zw.gov.mohcc.impilo.msikaflow.core.analytics;
