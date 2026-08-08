package zw.gov.mohcc.impilo.zibo.domain;

/**
 * How a publisher's version string is ordered.
 *
 * <p>ZIBO decided version precedence by clock — {@code max(comparing(publishedAt))} in
 * {@code ValidationEngine} and {@code MedicineRegistryService}, {@code createdAt DESC} in
 * {@code ArtifactRepository.findAllVersions}. Publishing a 1.0.1 correction after 1.1.0 therefore
 * silently started serving the older content, and for external terminologies the clock and the
 * version agree only by luck.</p>
 *
 * <p><b>The publisher's version string is never rewritten.</b> LOINC's "2.82" stays "2.82"; ATC's
 * "2026.01" stays "2026.01". Coercing external versions into SemVer would destroy the identifier
 * the publisher uses and that every historical clinical record was captured against. This enum
 * declares how to <em>order</em> that string; {@code version_sort_key} carries the derived,
 * comparable form.</p>
 *
 * <p>Append-only: persisted as a string.</p>
 */
public enum VersionScheme {

    /**
     * Two- or three-part numeric: {@code 1.1.0}, and also LOINC's {@code 2.82}, whose minor part is
     * a sequential integer. Two-part matters: as raw strings {@code "2.9"} sorts above
     * {@code "2.82"}, which is backwards.
     */
    SEMVER,

    /**
     * A release date the publisher writes as a version: {@code 2026.01} (ATC), {@code 2026-01}
     * (ICD-11), {@code 2025.1} (EDLIZ). Detected by a four-digit leading year, which is the only
     * thing separating these from {@link #SEMVER} — both are "digits dot digits".
     */
    DATE,

    /** A bare incrementing integer. */
    INTEGER,

    /**
     * Anything whose ordering cannot be derived — e.g. {@code 10-2025}, which could be release 10
     * of 2025 or October 2025. Sorted by raw string, which is honest about not knowing rather than
     * guessing an order and being confidently wrong.
     */
    PUBLISHER_DEFINED
}
