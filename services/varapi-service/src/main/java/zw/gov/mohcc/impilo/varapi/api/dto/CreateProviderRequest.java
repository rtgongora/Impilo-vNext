package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

public record CreateProviderRequest(
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
