package zw.gov.mohcc.impilo.indawo.api.policy;

import zw.gov.mohcc.impilo.indawo.api.dto.SiteResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

public final class SiteRepresentation {

    private SiteRepresentation() {
    }

    public static SiteResponse apply(SiteResponse r, VisibilityProfile p) {
        if (p == null || r == null) {
            return r;
        }
        boolean stripPii = p.piiAccess() != null
                && (p.piiAccess().equalsIgnoreCase("NONE") || p.piiAccess().equalsIgnoreCase("MASKED"));
        if (!stripPii) {
            return r;
        }
        return new SiteResponse(
                r.siteId(),
                r.tenantId(),
                r.name(),
                r.type(),
                null,
                r.geo(),
                r.status(),
                r.version(),
                r.createdAt(),
                r.updatedAt()
        );
    }
}
