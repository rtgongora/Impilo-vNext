package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProviderResponse(
        String providerPublicId,
        // Numeric registry key. The certificate/lifecycle/compliance engines are keyed on this
        // Long PK, while the experience layer only holds the public id — surfacing it here lets
        // the BFF resolve public-id → numeric with a single provider lookup (W0 bridge).
        Long providerId,
        UUID providerRef,
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
        Long employmentOrgId,
        String profilePhotoRef,
        String status,
        Integer version,
        List<ProviderIdentifierDto> identifiers,
        List<ProviderSpecialtyDto> specialties,
        List<ProviderContactDto> contacts,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {}
