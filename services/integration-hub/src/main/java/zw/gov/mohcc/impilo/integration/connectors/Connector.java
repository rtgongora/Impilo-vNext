package zw.gov.mohcc.impilo.integration.connectors;

public interface Connector {

    ConnectorType type();

    ConnectorResult execute(ConnectorRequest request);
}
