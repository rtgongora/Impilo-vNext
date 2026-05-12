package zw.gov.mohcc.impilo.vito.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ExternalClientRegistrationRequest(
        @NotBlank String givenName,
        String middleName,
        @NotBlank String familyName,
        @NotNull LocalDate dateOfBirth,
        @NotBlank String sex,
        String nationalId,
        String phone,
        String addressLine1,
        String city,
        String district,
        String province,
        @NotBlank String sourceSystem,
        String sourceSystemPatientId,
        String facilityId,
        UUID preAllocatedHealthId,
        String mrsPhid
) {}
