package zw.gov.mohcc.impilo.obs.core;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.obs.persistence.entity.DashboardEntity;
import zw.gov.mohcc.impilo.obs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.obs.persistence.repository.DashboardRepository;
import zw.gov.mohcc.impilo.obs.persistence.repository.EventOutboxRepository;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final EventOutboxRepository outboxRepository;

    public DashboardService(DashboardRepository dashboardRepository,
                            EventOutboxRepository outboxRepository) {
        this.dashboardRepository = dashboardRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public DashboardEntity createDashboard(UUID tenantId, String actorId, UUID correlationId,
                                           String name, String description,
                                           DashboardType dashboardType, String config) {
        DashboardEntity entity = new DashboardEntity();
        entity.setTenantId(tenantId);
        entity.setName(name);
        entity.setDescription(description);
        entity.setDashboardType(dashboardType != null ? dashboardType : DashboardType.GRAFANA);
        entity.setConfig(config != null ? config : "{}");
        entity.setCreatedBy(actorId);
        entity = dashboardRepository.save(entity);

        publishEvent("DASHBOARD", entity.getId().toString(), "DASHBOARD_CREATED",
                buildDashboardPayload(entity), tenantId.toString(), correlationId);

        return entity;
    }

    @Transactional(readOnly = true)
    public List<DashboardEntity> listDashboards(UUID tenantId) {
        return dashboardRepository.findByTenantId(tenantId);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, String payload,
                              String tenantId, UUID correlationId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(tenantId);
        event.setCorrelationId(correlationId != null ? correlationId.toString() : null);
        outboxRepository.save(event);
    }

    private String buildDashboardPayload(DashboardEntity d) {
        return "{\"id\":" + d.getId()
                + ",\"tenantId\":\"" + d.getTenantId() + "\""
                + ",\"name\":\"" + d.getName() + "\""
                + ",\"dashboardType\":\"" + d.getDashboardType() + "\""
                + ",\"status\":\"" + d.getStatus() + "\""
                + "}";
    }
}
