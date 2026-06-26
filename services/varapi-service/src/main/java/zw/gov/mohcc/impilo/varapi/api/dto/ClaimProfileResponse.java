package zw.gov.mohcc.impilo.varapi.api.dto;

import java.util.UUID;

/** Result of claiming a preloaded provider profile. */
public record ClaimProfileResponse(
        String providerPublicId,
        UUID impiloHealthId,
        String lifecycleStatus,   // CLAIMED after a successful claim
        String givenName,
        String familyName,
        String profession
) {}
