package zw.gov.mohcc.impilo.indawo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.indawo.api.dto.UpsertSiteRequest;
import zw.gov.mohcc.impilo.indawo.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.indawo.domain.SiteEntity;
import zw.gov.mohcc.impilo.indawo.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.indawo.repository.SiteRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SiteService {

    private static final Logger log = LoggerFactory.getLogger(SiteService.class);
    private static final String AGGREGATE_TYPE = "Site";
    private static final String PRODUCER = "indawo-service";
    private static final String SUBJECT_TYPE = "Site";

    private final SiteRepository siteRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SiteService(SiteRepository siteRepository,
                       OutboxEventRepository outboxRepository,
                       ObjectMapper objectMapper) {
        this.siteRepository = siteRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UpsertResult upsertSite(UUID siteId, UUID tenantId, String podId,
                                    String correlationId, String idempotencyKey,
                                    UpsertSiteRequest request) {
        Optional<SiteEntity> existing = siteRepository.findById(siteId);

        if (existing.isPresent()) {
            return updateSite(existing.get(), tenantId, podId, correlationId, idempotencyKey, request);
        } else {
            return createSite(siteId, tenantId, podId, correlationId, idempotencyKey, request);
        }
    }

    private UpsertResult createSite(UUID siteId, UUID tenantId, String podId,
                                     String correlationId, String idempotencyKey,
                                     UpsertSiteRequest request) {
        SiteEntity site = new SiteEntity(siteId, tenantId, request.name(), request.type());
        site.setAddressJson(toJson(request.address()));
        site.setGeoJson(toJson(request.geo()));
        if (request.status() != null && !request.status().isBlank()) {
            site.setStatus(request.status());
        }

        siteRepository.save(site);

        Map<String, Object> afterState = buildSiteState(site);
        Map<String, Object> payload = buildDeltaPayload("CREATE", null, afterState,
                List.of("name", "type", "address_json", "geo_json", "status"));

        appendOutboxEvent(
                "impilo.indawo.site.created.v1",
                siteId.toString(), tenantId, podId, correlationId, idempotencyKey,
                payload, siteId.toString());

        log.info("Created site [siteId={}, name={}]", siteId, request.name());
        return new UpsertResult(site, true);
    }

    private UpsertResult updateSite(SiteEntity site, UUID tenantId, String podId,
                                     String correlationId, String idempotencyKey,
                                     UpsertSiteRequest request) {
        Map<String, Object> beforeState = buildSiteState(site);
        List<String> changedFields = new java.util.ArrayList<>();

        if (!site.getName().equals(request.name())) {
            site.setName(request.name());
            changedFields.add("name");
        }
        if (!site.getType().equals(request.type())) {
            site.setType(request.type());
            changedFields.add("type");
        }

        String newAddressJson = toJson(request.address());
        if (!java.util.Objects.equals(site.getAddressJson(), newAddressJson)) {
            site.setAddressJson(newAddressJson);
            changedFields.add("address_json");
        }

        String newGeoJson = toJson(request.geo());
        if (!java.util.Objects.equals(site.getGeoJson(), newGeoJson)) {
            site.setGeoJson(newGeoJson);
            changedFields.add("geo_json");
        }

        if (request.status() != null && !request.status().isBlank()
                && !site.getStatus().equals(request.status())) {
            site.setStatus(request.status());
            changedFields.add("status");
        }

        site.setVersion(site.getVersion() + 1);
        site.setUpdatedAt(OffsetDateTime.now());
        siteRepository.save(site);

        Map<String, Object> afterState = buildSiteState(site);
        Map<String, Object> payload = buildDeltaPayload("UPDATE", beforeState, afterState, changedFields);

        appendOutboxEvent(
                "impilo.indawo.site.updated.v1",
                site.getSiteId().toString(), tenantId, podId, correlationId, idempotencyKey,
                payload, site.getSiteId().toString());

        log.info("Updated site [siteId={}, changedFields={}]", site.getSiteId(), changedFields);
        return new UpsertResult(site, false);
    }

    @Transactional(readOnly = true)
    public Optional<SiteEntity> getSite(UUID siteId) {
        return siteRepository.findById(siteId);
    }

    @Transactional(readOnly = true)
    public Page<SiteEntity> listSites(UUID tenantId, int page, int size) {
        return siteRepository.findByTenantId(tenantId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<SiteEntity> getSnapshot(OffsetDateTime asOf, int page, int size) {
        return siteRepository.findSnapshotAsOf(asOf, PageRequest.of(page, size));
    }

    private void appendOutboxEvent(String eventType, String aggregateId,
                                    UUID tenantId, String podId, String correlationId,
                                    String idempotencyKey, Map<String, Object> payload,
                                    String partitionKey) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(AGGREGATE_TYPE);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType(SUBJECT_TYPE);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private Map<String, Object> buildSiteState(SiteEntity site) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("site_id", site.getSiteId().toString());
        state.put("tenant_id", site.getTenantId().toString());
        state.put("name", site.getName());
        state.put("type", site.getType());
        state.put("address_json", site.getAddressJson());
        state.put("geo_json", site.getGeoJson());
        state.put("status", site.getStatus());
        state.put("version", site.getVersion());
        return state;
    }

    private Map<String, Object> buildDeltaPayload(String op, Map<String, Object> before,
                                                    Map<String, Object> after,
                                                    List<String> changedFields) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("op", op);
        delta.put("before", before);
        delta.put("after", after);
        delta.put("changed_fields", changedFields);
        return delta;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    public record UpsertResult(SiteEntity site, boolean created) {}
}
