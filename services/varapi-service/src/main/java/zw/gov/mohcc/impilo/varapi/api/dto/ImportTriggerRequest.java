package zw.gov.mohcc.impilo.varapi.api.dto;

public record ImportTriggerRequest(
        ImportType importType,
        String payload
) {
    public enum ImportType {
        REST,
        CSV,
        KAFKA
    }
}
