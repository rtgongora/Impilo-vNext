package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the server-authoritative tenant ({@link TenantContextFilter}).
 *
 * <p>Bound from {@code impilo.tenancy.*}. The mode ladder is the estate's existing idiom for
 * shipping a control that is not yet safe to enforce (see {@code AuthzProperties.workContextMode}
 * and {@code confidentialityMode}): observe first, quantify, then flip.</p>
 */
@ConfigurationProperties(prefix = "impilo.tenancy")
public class TenantAuthorityProperties {

    /**
     * OFF     — never resolve, never log, never override. Byte-for-byte today's behaviour.
     * SHADOW  — resolve the authoritative tenant and AUDIT every divergence from the header the
     *           caller presented, but forward the caller's value unchanged. Default: the
     *           divergence rate is unmeasured, and a boundary that starts refusing traffic before
     *           anyone knows the rate is an outage, not a control.
     * ENFORCE — the request's {@code X-Tenant-ID} reads the authoritative value; the caller's
     *           header is refused.
     */
    private String mode = "SHADOW";

    /**
     * The claim carrying the tenant on a validated token.
     *
     * <p><b>No token on this estate carries it today</b> — verified 2026-07-27: the only claims any
     * service reads are {@code health_id}, {@code realm_access} and {@code capabilities}, and the
     * Keycloak realm has no tenant mapper. Until an identity-plane change mints it, resolution
     * falls through to {@link #authenticatedDefault} and the filter's authority is
     * "authenticated ⇒ the platform's tenant", not "this token's tenant". That is a real narrowing
     * (it removes the caller's ability to choose) but it is NOT per-tenant isolation, and the
     * distinction must not be blurred in a report to anyone deciding to flip ENFORCE.</p>
     */
    private String claim = "tenant_id";

    /**
     * Tenant asserted for an authenticated caller whose token carries no {@link #claim}.
     *
     * <p>{@link PublicTenants#CARE_PLANE} because that is what an authenticated human in the shell
     * is doing: writing and reading care content. Registry-plane reads are cross-plane reads and
     * are declared at the BFF client, which still wins — see {@link TenantContextFilter}.</p>
     */
    private String authenticatedDefault = PublicTenants.CARE_PLANE;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getClaim() { return claim; }
    public void setClaim(String claim) { this.claim = claim; }

    public String getAuthenticatedDefault() { return authenticatedDefault; }
    public void setAuthenticatedDefault(String authenticatedDefault) {
        this.authenticatedDefault = authenticatedDefault;
    }

    public boolean isOff() { return "OFF".equalsIgnoreCase(mode); }

    public boolean isEnforcing() { return "ENFORCE".equalsIgnoreCase(mode); }
}
