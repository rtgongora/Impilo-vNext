package zw.gov.mohcc.impilo.varapi.api.dto;

public record UpdateProviderRequest(
        String title,
        String givenName,
        String familyName,
        String email,
        String phone,
        String profession,
        String cadre,
        Long primaryCouncilId,
        Long employmentOrgId
) {}
