package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record GrantPrivilegeRequest(
        Long facilityId,
        UUID workspaceId,
        String scope,
        String privilegeType,
        LocalDate validFrom,
        LocalDate validTo,
        Long supervisingProviderId,
        String notes
) {}
