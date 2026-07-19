package zw.gov.mohcc.impilo.datagovernance.deid.domain;

/**
 * De-identification pipeline zones (design: docs/data-platform/de-identification-pipeline.md).
 *
 * <p>Rows move one-way through the zones; each zone is cumulative over the
 * previous one:</p>
 * <ul>
 *   <li>{@link #RAW} — CPID-keyed source rows. Source-only; never a
 *       materialisation target and never leaves this service.</li>
 *   <li>{@link #TOKENISED} — CPID replaced by the per-dataset HMAC subject
 *       token; direct identifiers stripped (allowlist, fail-closed).</li>
 *   <li>{@link #GENERALISED} — quasi-identifiers reduced (DOB→age band,
 *       geo→district, dates→month/shift, rare categoricals→OTHER).</li>
 *   <li>{@link #RELEASED} — k-anonymity verified/suppressed and the data-use
 *       policy gate passed.</li>
 * </ul>
 */
public enum DeidZone {

    RAW,
    TOKENISED,
    GENERALISED,
    RELEASED;

    /** True when this zone includes the transforms of {@code other} (cumulative). */
    public boolean atLeast(DeidZone other) {
        return ordinal() >= other.ordinal();
    }

    /** True for zones a dataset may target for materialisation (everything but RAW). */
    public boolean isMaterialisationTarget() {
        return this != RAW;
    }
}
