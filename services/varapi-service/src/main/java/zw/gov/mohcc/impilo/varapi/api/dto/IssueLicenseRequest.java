package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

public record IssueLicenseRequest(
        Long councilId,
        String licenseType,
        String licenseNumber,
        LocalDate validFrom,
        LocalDate validTo,
        String conditions
) {}
