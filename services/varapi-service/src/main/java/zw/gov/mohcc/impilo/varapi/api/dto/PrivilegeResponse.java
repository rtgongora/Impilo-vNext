package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PrivilegeResponse(
        Long id,
        String providerPublicId,
        Long facilityId,
        UUID workspaceId,
        String scope,
        String privilegeType,
        String status,
        String grantedBy,
        Instant grantedAt,
        LocalDate validFrom,
        LocalDate validTo,
        String supervisingProviderPublicId,
        String notes
) {}
