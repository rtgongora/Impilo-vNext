package zw.gov.mohcc.impilo.governance.orgkey;

import java.util.UUID;

import org.springframework.stereotype.Component;

import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorProperties;
import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorProperties.ReadPreference;

/**
 * Dormant, flag-gated resolver that decides whether an organisation read should
 * use the legacy {@code wgv_organisation} id or the org-registry id.
 *
 * <p>Behaviour is governed by {@code impilo.org-mirror.read-preference}:
 * <ul>
 *   <li>{@code WGV} (default) — always resolves to the legacy wgv id. A strict
 *       no-op vs today; the org-registry key is ignored even if present.</li>
 *   <li>{@code ORG_REGISTRY} — resolves to the org-registry id ONLY when a
 *       dual-key mapping exists ({@code orgRegistryOrgId != null}); otherwise it
 *       falls back to the legacy wgv id.</li>
 * </ul>
 *
 * <p>Nothing wires live reads to this resolver in phase-2a — it is opt-in
 * scaffolding for phase-2c. With the default flag it can never change a read
 * path.
 */
@Component
public class OrgReadPreferenceResolver {

    private final OrgMirrorProperties properties;

    public OrgReadPreferenceResolver(OrgMirrorProperties properties) {
        this.properties = properties;
    }

    /** Which store a resolved read should target. */
    public enum OrgReadSource {
        WGV,
        ORG_REGISTRY
    }

    /**
     * @param source            the store to read from
     * @param organisationId    the id to read by within {@code source}
     * @param wgvOrganisationId the legacy wgv id (always carried for audit/fallback)
     */
    public record OrgReadResolution(OrgReadSource source, UUID organisationId, UUID wgvOrganisationId) {
    }

    /**
     * Resolve the read target for an organisation reference.
     *
     * @param wgvOrganisationId the authoritative legacy wgv organisation id (required)
     * @param orgRegistryOrgId  the dual-key mapping if backfilled, else {@code null}
     */
    public OrgReadResolution resolve(UUID wgvOrganisationId, UUID orgRegistryOrgId) {
        if (properties.getReadPreference() == ReadPreference.ORG_REGISTRY && orgRegistryOrgId != null) {
            return new OrgReadResolution(OrgReadSource.ORG_REGISTRY, orgRegistryOrgId, wgvOrganisationId);
        }
        return new OrgReadResolution(OrgReadSource.WGV, wgvOrganisationId, wgvOrganisationId);
    }
}
