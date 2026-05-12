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
        String province
) {}
