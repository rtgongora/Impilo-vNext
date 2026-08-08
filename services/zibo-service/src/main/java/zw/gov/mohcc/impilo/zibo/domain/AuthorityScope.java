package zw.gov.mohcc.impilo.zibo.domain;

/**
 * Whose terminology a concept is.
 *
 * <p>{@link #NATIONAL} content exists once and is readable from every tenant plane. That replaces
 * duplicating it per tenant, which is what {@code V007} had to do — it inserts the WHO ATC
 * CodeSystem twice, once for REGISTRY and once for CARE, because every lookup was
 * {@code findByTenantId…} and terminology could not cross the boundary.</p>
 *
 * <p>{@link #TENANT} is a plane-local override. It may carry the same {@code (system, version,
 * code)} as a national concept and wins for that tenant, mirroring how
 * {@code ArtifactResolutionService} already prefers a local artifact over the national one.</p>
 */
public enum AuthorityScope {
    NATIONAL,
    TENANT
}
