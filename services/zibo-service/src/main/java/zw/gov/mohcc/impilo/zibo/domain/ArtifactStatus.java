package zw.gov.mohcc.impilo.zibo.domain;

/**
 * Lifecycle status of a terminology artifact.
 *
 * <p>Follows the FHIR publication lifecycle: an artifact begins as
 * {@link #DRAFT}, transitions to {@link #ACTIVE} during review, becomes
 * {@link #PUBLISHED} when officially released, and may later be
 * {@link #DEPRECATED} (still usable but discouraged) or {@link #RETIRED}
 * (no longer valid for new use).</p>
 */
public enum ArtifactStatus {
    DRAFT,
    ACTIVE,
    PUBLISHED,
    DEPRECATED,
    RETIRED
}
