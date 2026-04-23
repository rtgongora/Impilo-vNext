package zw.gov.mohcc.impilo.varapi.api.dto.collaboration;

import java.util.UUID;

public record CreateExternalProviderProfileRequest(
        UUID tenantId,
        String vitoAccessGrantRef,
        Long councilId,
        String councilRegistrationNumber,
        String givenName,
        String familyName,
        String professionOrCadre,
        String email,
        String phone,
        String organisationHint) {
}
