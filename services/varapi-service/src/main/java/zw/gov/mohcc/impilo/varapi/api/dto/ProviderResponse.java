package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProviderResponse(
        String providerPublicId,
        UUID providerRef,
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
