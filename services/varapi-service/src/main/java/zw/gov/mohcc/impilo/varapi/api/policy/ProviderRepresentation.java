package zw.gov.mohcc.impilo.varapi.api.policy;

import zw.gov.mohcc.impilo.varapi.api.dto.ProviderResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

/**
 * Applies Tshepo visibility obligations to provider payloads (PEP).
 */
public final class ProviderRepresentation {

    private ProviderRepresentation() {
    }

    public static ProviderResponse apply(ProviderResponse r, VisibilityProfile p) {
        if (p == null || r == null) {
            return r;
        }
        boolean stripPii = p.piiAccess() != null
                && (p.piiAccess().equalsIgnoreCase("NONE") || p.piiAccess().equalsIgnoreCase("MASKED"));
        boolean deidentified = p.visibilityTier() != null
                && p.visibilityTier().toUpperCase().contains("DEIDENTIFIED");
        if (!stripPii && !deidentified) {
            return r;
        }
        return new ProviderResponse(
                r.providerPublicId(),
                r.providerRef(),
                deidentified ? null : r.title(),
                deidentified ? null : r.givenName(),
                deidentified ? null : r.familyName(),
                deidentified ? null : r.dateOfBirth(),
                r.gender(),
                deidentified ? null : r.nationality(),
                stripPii || deidentified ? null : r.nationalId(),
                stripPii || deidentified ? null : r.email(),
                stripPii || deidentified ? null : r.phone(),
                r.practiceNumber(),
                r.profession(),
                r.cadre(),
                r.primaryCouncilId(),
                r.employmentOrgId(),
                deidentified ? null : r.profilePhotoRef(),
                r.status(),
                r.version(),
                r.identifiers(),
                r.specialties(),
                stripPii || deidentified ? java.util.List.of() : r.contacts(),
                r.createdAt(),
                r.updatedAt(),
                r.createdBy(),
                r.updatedBy()
        );
    }
}
