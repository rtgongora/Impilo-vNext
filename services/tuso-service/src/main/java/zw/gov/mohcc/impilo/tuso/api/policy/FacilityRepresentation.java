package zw.gov.mohcc.impilo.tuso.api.policy;

import zw.gov.mohcc.impilo.tuso.api.dto.FacilityResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

public final class FacilityRepresentation {

    private FacilityRepresentation() {
    }

    public static FacilityResponse apply(FacilityResponse r, VisibilityProfile p) {
        if (p == null || r == null) {
            return r;
        }
        boolean stripPii = p.piiAccess() != null
                && (p.piiAccess().equalsIgnoreCase("NONE") || p.piiAccess().equalsIgnoreCase("MASKED"));
        if (!stripPii) {
            return r;
        }
        FacilityResponse.GeoDetail geo = r.geo();
        FacilityResponse.GeoDetail geoMasked = geo == null ? null
                : new FacilityResponse.GeoDetail(
                null,
                null,
                null,
                geo.province(),
                geo.district(),
                geo.ward(),
                null,
                geo.country(),
                geo.altitudeM(),
                geo.catchmentArea());
        return new FacilityResponse(
                r.id(),
                r.facilityUuid(),
                r.name(),
                r.facilityCode(),
                r.facilityType(),
                r.province(),
                r.district(),
                r.latitude(),
                r.longitude(),
                r.status(),
                r.operationalStatus(),
                r.ownership(),
                r.level(),
                r.facilityCategory(),
                r.operatingModel(),
                r.parentId(),
                r.parentName(),
                r.description(),
                r.openedDate(),
                r.closedDate(),
                r.closeReason(),
                r.mergedIntoId(),
                r.version(),
                r.identifiers(),
                java.util.List.of(),
                geoMasked,
                r.capabilities(),
                r.readiness(),
                r.createdAt(),
                r.updatedAt(),
                r.createdBy(),
                r.updatedBy()
        );
    }
}
