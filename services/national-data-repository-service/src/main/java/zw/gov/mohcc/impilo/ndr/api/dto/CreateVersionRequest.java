package zw.gov.mohcc.impilo.ndr.api.dto;

public record CreateVersionRequest(
        String schemaJson,
        long rowCount
) {}
