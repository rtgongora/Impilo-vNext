package zw.gov.mohcc.impilo.fhirgateway.core;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirRouteEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirRouteRepository;

@Service
public class GatewayRouteService {

    private final FhirRouteRepository routeRepository;
    private final EventOutboxRepository outboxRepository;

    public GatewayRouteService(FhirRouteRepository routeRepository,
                               EventOutboxRepository outboxRepository) {
        this.routeRepository = routeRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional(readOnly = true)
    public List<FhirRouteEntity> listRoutes(UUID tenantId) {
        return routeRepository.findByTenantId(tenantId);
    }

    @Transactional
    public FhirRouteEntity createRoute(UUID tenantId, String correlationId,
                                       String sourceSystem, String resourceType,
                                       String targetEndpoint, Boolean enabled) {
        FhirRouteEntity route = new FhirRouteEntity();
        route.setTenantId(tenantId);
        route.setSourceSystem(sourceSystem);
        route.setResourceType(resourceType);
        route.setTargetEndpoint(targetEndpoint);
        route.setEnabled(enabled != null ? enabled : true);
        route = routeRepository.save(route);

        publishEvent("FHIR_ROUTE", route.getId().toString(), "ROUTE_CREATED",
                buildRoutePayload(route), tenantId.toString(), correlationId);

        return route;
    }

    @Transactional(readOnly = true)
    public List<FhirRouteEntity> findActiveRoutes(UUID tenantId, String resourceType) {
        return routeRepository.findByTenantIdAndResourceTypeAndEnabledTrue(tenantId, resourceType);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, String payload,
                              String tenantId, String correlationId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(tenantId);
        event.setCorrelationId(correlationId);
        event.setSubjectType("FhirRoute");
        event.setSubjectId(aggregateId);
        event.setPartitionKey(tenantId);
        outboxRepository.save(event);
    }

    private String buildRoutePayload(FhirRouteEntity r) {
        return "{\"id\":" + r.getId()
                + ",\"tenantId\":\"" + r.getTenantId() + "\""
                + ",\"sourceSystem\":\"" + r.getSourceSystem() + "\""
                + ",\"resourceType\":\"" + r.getResourceType() + "\""
                + ",\"targetEndpoint\":\"" + r.getTargetEndpoint() + "\""
                + ",\"enabled\":" + r.isEnabled()
                + "}";
    }
}
