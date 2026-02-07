package zw.gov.mohcc.impilo.varapi.api.dto;

public record ContactDto(
        String contactType,
        String value,
        boolean verified,
        boolean primaryContact
) {}
