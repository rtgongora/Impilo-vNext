package zw.gov.mohcc.impilo.varapi.api.dto;

public record PortalMeResponse(
        String providerPublicId,
        String title,
        String givenName,
        String familyName,
        String email,
        String phone,
        String profession,
        String cadre,
        String practiceNumber,
        String profilePhotoRef,
        String status
) {}
