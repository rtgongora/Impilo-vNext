package zw.gov.mohcc.impilo.varapi.api.dto;

public record SpecialtyDto(
        String specialtyCode,
        String specialtyName,
        String subspecialtyCode,
        String subspecialtyName,
        boolean primarySpecialty
) {}
