package zw.gov.mohcc.impilo.schemaregistry.api.dto;

public record RegisterSchemaRequest(
        String subject,
        String schema,
        String description
) {}
