package zw.gov.mohcc.impilo.integration.connectors;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ConnectorRegistry {

    private final Map<ConnectorType, Connector> connectors;

    public ConnectorRegistry(List<Connector> connectorBeans) {
        this.connectors = new EnumMap<>(ConnectorType.class);
        for (Connector connector : connectorBeans) {
            connectors.put(connector.type(), connector);
        }
    }

    public Connector resolve(ConnectorType type) {
        Connector connector = connectors.get(type);
        if (connector == null) {
            throw new IllegalArgumentException("No connector registered for type: " + type);
        }
        return connector;
    }
}
