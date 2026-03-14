package zw.gov.mohcc.impilo.obs.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.obs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.obs.persistence.entity.ServiceHeartbeatEntity;
import zw.gov.mohcc.impilo.obs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.obs.persistence.repository.ServiceHeartbeatRepository;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OpsService {

    private static final Logger log = LoggerFactory.getLogger(OpsService.class);
    private static final int STALE_THRESHOLD_MINUTES = 5;

    private final ServiceHeartbeatRepository heartbeatRepository;
    private final EventOutboxRepository outboxRepository;

    public OpsService(ServiceHeartbeatRepository heartbeatRepository,
                      EventOutboxRepository outboxRepository) {
        this.heartbeatRepository = heartbeatRepository;
        this.outboxRepository = outboxRepository;
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
}
