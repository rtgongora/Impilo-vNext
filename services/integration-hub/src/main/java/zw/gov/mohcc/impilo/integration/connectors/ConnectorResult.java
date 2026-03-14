package zw.gov.mohcc.impilo.integration.connectors;

public record ConnectorResult(
        boolean success,
        int statusCode,
        String responseBody,
        String errorMessage
) {

    public static ConnectorResult success(int statusCode, String responseBody) {
        return new ConnectorResult(true, statusCode, responseBody, null);
    }

    public static ConnectorResult failure(int statusCode, String errorMessage) {
        return new ConnectorResult(false, statusCode, null, errorMessage);
    }

    public static ConnectorResult error(String errorMessage) {
        return new ConnectorResult(false, 0, null, errorMessage);
    }
}
