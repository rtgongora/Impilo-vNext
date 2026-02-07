package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

public record ProviderSpecialtyDto(
        Long id,
        String specialtyCode,
        String specialtyName,
        String subspecialtyCode,
        String subspecialtyName,
        boolean ziboValidated,
        boolean primarySpecialty,
        LocalDate effectiveDate,
        LocalDate endDate
) {}
