package zw.gov.mohcc.impilo.butanofhir.core;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.butanofhir.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.butanofhir.persistence.entity.FhirResourceEntity;
import zw.gov.mohcc.impilo.butanofhir.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.butanofhir.persistence.repository.FhirResourceRepository;

@Service
public class FhirResourceService {

    private final FhirResourceRepository resourceRepository;
    private final EventOutboxRepository outboxRepository;

    public FhirResourceService(FhirResourceRepository resourceRepository,
                               EventOutboxRepository outboxRepository) {
        this.resourceRepository = resourceRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional(readOnly = true)
    public List<FhirResourceEntity> listByType(UUID tenantId, String resourceType) {
        return resourceRepository.findByTenantIdAndResourceType(tenantId, resourceType);
    }

    @Transactional(readOnly = true)
    public FhirResourceEntity findById(Long id, UUID tenantId) {
        return resourceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("FHIR resource not found: " + id));
    }

    @Transactional
    public FhirResourceEntity createOrUpdate(UUID tenantId, String resourceType, String resourceId,
                                              String fhirVersion, String payload,
                                              String correlationId) {
        FhirResourceEntity entity = resourceRepository
                .findByTenantIdAndResourceTypeAndResourceId(tenantId, resourceType, resourceId)
                .orElseGet(FhirResourceEntity::new);

        boolean isNew = entity.getId() == null;

        entity.setTenantId(tenantId);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setFhirVersion(fhirVersion != null ? fhirVersion : "R4");
        entity.setPayload(payload);
        entity.setStatus("ACTIVE");
        entity = resourceRepository.save(entity);

        String eventType = isNew ? "FHIR_RESOURCE_CREATED" : "FHIR_RESOURCE_UPDATED";
        publishEvent("FhirResource", entity.getId().toString(), eventType,
                buildPayload(entity), tenantId.toString(), correlationId);

        return entity;
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
        event.setProducer("butano-fhir");
        event.setSchemaVersion(1);
        event.setPublished(false);
        outboxRepository.save(event);
    }

    private String buildPayload(FhirResourceEntity r) {
        return "{\"id\":" + r.getId()
                + ",\"tenantId\":\"" + r.getTenantId() + "\""
                + ",\"resourceType\":\"" + r.getResourceType() + "\""
                + ",\"resourceId\":\"" + r.getResourceId() + "\""
                + ",\"fhirVersion\":\"" + r.getFhirVersion() + "\""
                + ",\"status\":\"" + r.getStatus() + "\""
                + "}";
    }
}
