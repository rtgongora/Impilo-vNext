package zw.gov.mohcc.impilo.varapi.api.dto;

public record ProviderContactDto(
        Long id,
        String contactType,
        String value,
        boolean verified,
        boolean primaryContact
) {}
