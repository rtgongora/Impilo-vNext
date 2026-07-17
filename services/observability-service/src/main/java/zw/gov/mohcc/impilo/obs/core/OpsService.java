package zw.gov.mohcc.impilo.obs.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.obs.persistence.entity.ClientEventEntity;
import zw.gov.mohcc.impilo.obs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.obs.persistence.entity.ServiceHeartbeatEntity;
import zw.gov.mohcc.impilo.obs.persistence.repository.ClientEventRepository;
import zw.gov.mohcc.impilo.obs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.obs.persistence.repository.ServiceHeartbeatRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OpsService {

    private static final Logger log = LoggerFactory.getLogger(OpsService.class);
    static final int STALE_THRESHOLD_MINUTES = 5;

    /** Server-side cap on a single client-event ingest batch; extras are ignored. */
    static final int MAX_CLIENT_EVENTS_BATCH = 50;

    private final ServiceHeartbeatRepository heartbeatRepository;
    private final EventOutboxRepository outboxRepository;
    private final ClientEventRepository clientEventRepository;

    public OpsService(ServiceHeartbeatRepository heartbeatRepository,
                      EventOutboxRepository outboxRepository,
                      ClientEventRepository clientEventRepository) {
        this.heartbeatRepository = heartbeatRepository;
        this.outboxRepository = outboxRepository;
        this.clientEventRepository = clientEventRepository;
    }

    @Transactional
    public ServiceHeartbeatEntity recordHeartbeat(UUID tenantId, String serviceName, String instanceId,
                                                   String status, String versionTag, String metadata) {
        Optional<ServiceHeartbeatEntity> existing =
                heartbeatRepository.findByServiceNameAndInstanceId(serviceName, instanceId);

        ServiceHeartbeatEntity heartbeat;
        if (existing.isPresent()) {
            heartbeat = existing.get();
            heartbeat.setStatus(status != null ? status : "UP");
            heartbeat.setLastHeartbeat(OffsetDateTime.now());
            if (versionTag != null) heartbeat.setVersionTag(versionTag);
            if (metadata != null) heartbeat.setMetadata(metadata);
        } else {
            heartbeat = new ServiceHeartbeatEntity();
            heartbeat.setServiceName(serviceName);
            heartbeat.setInstanceId(instanceId);
            heartbeat.setTenantId(tenantId);
            heartbeat.setStatus(status != null ? status : "UP");
            heartbeat.setVersionTag(versionTag);
            heartbeat.setLastHeartbeat(OffsetDateTime.now());
            heartbeat.setMetadata(metadata != null ? metadata : "{}");
        }

        heartbeatRepository.save(heartbeat);
        log.info("Heartbeat recorded [service={}, instance={}, status={}]", serviceName, instanceId, heartbeat.getStatus());
        return heartbeat;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getHealthSummary() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<ServiceHeartbeatEntity> all = heartbeatRepository.findAll();
        List<ServiceHeartbeatEntity> stale = heartbeatRepository.findStaleHeartbeats(threshold);

        Set<String> staleServiceNames = stale.stream()
                .map(ServiceHeartbeatEntity::getServiceName)
                .collect(Collectors.toSet());

        long totalServices = all.stream().map(ServiceHeartbeatEntity::getServiceName).distinct().count();
        long healthyServices = totalServices - staleServiceNames.size();

        List<Map<String, Object>> serviceDetails = all.stream()
                .collect(Collectors.groupingBy(ServiceHeartbeatEntity::getServiceName))
                .entrySet().stream()
                .map(entry -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("service_name", entry.getKey());
                    detail.put("instances", entry.getValue().size());
                    boolean isHealthy = entry.getValue().stream()
                            .anyMatch(h -> h.getLastHeartbeat().isAfter(threshold));
                    detail.put("status", isHealthy ? "UP" : "STALE");
                    entry.getValue().stream()
                            .max(Comparator.comparing(ServiceHeartbeatEntity::getLastHeartbeat))
                            .ifPresent(latest -> detail.put("last_heartbeat", latest.getLastHeartbeat().toString()));
                    return detail;
                })
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_services", totalServices);
        summary.put("healthy", healthyServices);
        summary.put("stale", staleServiceNames.size());
        summary.put("stale_threshold_minutes", STALE_THRESHOLD_MINUTES);
        summary.put("services", serviceDetails);
        summary.put("generated_at", OffsetDateTime.now().toString());
        return summary;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMetricsLag() {
        List<EventOutboxEntity> unpublished = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        long outboxDepth = unpublished.size();
        OffsetDateTime oldestUnpublished = unpublished.isEmpty() ? null : unpublished.get(0).getCreatedAt();

        long lagSeconds = 0;
        if (oldestUnpublished != null) {
            lagSeconds = java.time.Duration.between(oldestUnpublished, OffsetDateTime.now()).getSeconds();
        }

        Map<String, Object> lag = new LinkedHashMap<>();
        lag.put("outbox_depth", outboxDepth);
        lag.put("outbox_lag_seconds", lagSeconds);
        lag.put("oldest_unpublished_at", oldestUnpublished != null ? oldestUnpublished.toString() : null);
        lag.put("measured_at", OffsetDateTime.now().toString());
        return lag;
    }

    /**
     * Persist a batch of PII-free client events. The batch is capped server-side
     * at {@link #MAX_CLIENT_EVENTS_BATCH}; over-long allow-listed fields are
     * truncated defensively. Returns the number of events accepted (persisted).
     */
    @Transactional
    public int recordClientEvents(UUID tenantId, List<ClientEventCommand> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        List<ClientEventCommand> capped = events.size() > MAX_CLIENT_EVENTS_BATCH
                ? events.subList(0, MAX_CLIENT_EVENTS_BATCH)
                : events;

        int accepted = 0;
        for (ClientEventCommand ev : capped) {
            if (ev == null) {
                continue;
            }
            ClientEventEntity entity = new ClientEventEntity();
            entity.setTenantId(tenantId);
            entity.setRoute(truncate(ev.route(), 256));
            entity.setCode(truncate(ev.code(), 64));
            entity.setCorrelationId(truncate(ev.correlationId(), 64));
            entity.setRequestId(truncate(ev.requestId(), 64));
            entity.setHttpStatus(ev.status());
            entity.setOccurredAt(parseOccurredAt(ev.at()));
            entity.setSource("CLIENT");
            clientEventRepository.save(entity);
            accepted++;
        }
        log.info("Client events ingested [tenant={}, accepted={}, submitted={}]",
                tenantId, accepted, events.size());
        return accepted;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static OffsetDateTime parseOccurredAt(String at) {
        if (at == null || at.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(at.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
