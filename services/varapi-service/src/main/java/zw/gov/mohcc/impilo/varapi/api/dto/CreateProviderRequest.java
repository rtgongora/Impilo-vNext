package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateProviderRequest(
        /** Canonical Impilo / Health ID for this person; required when {@code varapi.identity.require-impilo-health-id-on-create} is true. */
        UUID impiloHealthId,
        String title,
        String givenName,
        String familyName,
        LocalDate dateOfBirth,
        String gender,
        String nationality,
        String nationalId,
        String email,
        String phone,
        String practiceNumber,
        String profession,
        String cadre,
        Long primaryCouncilId,
        Long employmentOrgId
) {}
