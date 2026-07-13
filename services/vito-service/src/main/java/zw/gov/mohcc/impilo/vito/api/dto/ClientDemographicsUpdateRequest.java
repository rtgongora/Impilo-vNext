package zw.gov.mohcc.impilo.vito.api.dto;

import java.time.LocalDate;

public record ClientDemographicsUpdateRequest(
        String givenName,
        String middleName,
        String familyName,
        LocalDate dateOfBirth,
        String sex,
        String phone,
        String addressLine1,
        String city,
        String district,
        String province,
        // Extended demographics — update parity with the create path (Wave 3A).
        // Appended (not reordered) to preserve positional/JSON contract compatibility.
        String email,
        String passportReference,
        String medicalAidNumber,
        String preferredLanguage,
        String maritalStatus,
        String emergencyContactName,
        String emergencyContactPhone
) {}
