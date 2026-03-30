package zw.gov.mohcc.impilo.tshepo.federation.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.events.DeltaPayload;
import zw.gov.mohcc.impilo.tshepo.federation.persistence.*;
import zw.gov.mohcc.impilo.tshepo.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.persistence.EventOutboxRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Core service for federation pod registration.
 *
 * Manages the pod lifecycle: registration, heartbeat, renewal,
 * revocation, and reinstatement.
 */
@Service
public class PodRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(PodRegistrationService.class);
    private static final Duration DEFAULT_REGISTRATION_VALIDITY = Duration.ofDays(30);
    private static final int DEFAULT_HEARTBEAT_INTERVAL = 300; // seconds
    private static final int DEFAULT_MAX_OFFLINE_HOURS = 48;

    private final PodRegistrationRepository podRepo;
    private final EventOutboxRepository outboxRepo;
    private final PodRevocationCacheService revocationCache;
    private final ObjectMapper objectMapper;

    public PodRegistrationService(PodRegistrationRepository podRepo,
                                   EventOutboxRepository outboxRepo,
                                   PodRevocationCacheService revocationCache,
                                   ObjectMapper objectMapper) {
        this.podRepo = podRepo;
        this.outboxRepo = outboxRepo;
        this.revocationCache = revocationCache;
        this.objectMapper = objectMapper;
    }

    /**
     * Register a new pod with the national spine.
     *
     * Validates the certificate, assigns authority scope, and persists the registration.
     * Publishes a pod.registered event to the outbox.
     */
    @Transactional
    public PodRegistrationEntity registerPod(PodRegistrationRequest request) {
        // Reject duplicate registrations
        if (podRepo.existsByPodId(request.podId())) {
            throw new IllegalStateException("Pod " + request.podId() + " is already registered");
        }

        // Validate certificate is present (actual chain validation is done by mTLS termination)
        if (request.podCertificate() == null || request.podCertificate().isBlank()) {
            throw new IllegalArgumentException("Pod certificate is required for registration");
        }

        Instant now = Instant.now();
        PodRegistrationEntity pod = new PodRegistrationEntity();
        pod.setPodId(request.podId());
        pod.setPodName(request.podName());
        pod.setPodCertificate(request.podCertificate());
        pod.setCapabilities(toJson(request.capabilities()));
        pod.setRegion(request.region());
        pod.setFacilityIds(toJson(request.facilityIds()));

        // Scope the authority grant: never exceed what was requested,
        // and apply national policy constraints
        List<String> grantedClasses = scopeDataClasses(request.requestedDataClasses());
        pod.setGrantedDataClasses(toJson(grantedClasses));
        pod.setMaxOfflineHours(Math.min(
                request.requestedMaxOfflineHours() != null ? request.requestedMaxOfflineHours() : DEFAULT_MAX_OFFLINE_HOURS,
                DEFAULT_MAX_OFFLINE_HOURS));
        pod.setMergeAuthority(false); // Pods never get merge authority
        pod.setRevocationPriority("HIGH");
        pod.setHeartbeatIntervalSeconds(DEFAULT_HEARTBEAT_INTERVAL);
        pod.setStatus(PodStatus.ACTIVE);
        pod.setIssuedAt(now);
        pod.setExpiresAt(now.plus(DEFAULT_REGISTRATION_VALIDITY));

        pod = podRepo.save(pod);

        publishOutboxEvent("POD_REGISTRATION", pod.getPodId().toString(),
                "impilo.federation.pod.registered.v1", Map.of(
                        "registration_id", pod.getRegistrationId().toString(),
                        "pod_id", pod.getPodId().toString(),
                        "pod_name", pod.getPodName(),
                        "region", pod.getRegion() != null ? pod.getRegion() : "",
                        "granted_data_classes", grantedClasses,
                        "status", pod.getStatus().name()));

        log.info("Pod registered: id={}, name={}, region={}, registrationId={}",
                pod.getPodId(), pod.getPodName(), pod.getRegion(), pod.getRegistrationId());

        return pod;
    }

    /**
     * Process a heartbeat from a registered pod.
     *
     * Updates last_heartbeat_at and validates the pod is still active.
     *
     * @return the updated registration, or empty if the pod is not found or revoked
     */
    @Transactional
    public Optional<PodRegistrationEntity> heartbeat(UUID podId) {
        return podRepo.findByPodId(podId)
                .map(pod -> {
                    if (pod.getStatus() == PodStatus.REVOKED) {
                        log.warn("Heartbeat received from revoked pod: {}", podId);
                        return null;
                    }
                    if (pod.getStatus() == PodStatus.EXPIRED) {
                        log.warn("Heartbeat received from expired pod: {}", podId);
                        return null;
                    }
                    pod.setLastHeartbeatAt(Instant.now());
                    return podRepo.save(pod);
                });
    }

    /**
     * Revoke a pod's registration.
     *
     * Marks the pod as REVOKED, publishes a HIGH_PRI revocation event to Kafka,
     * and updates the revocation cache for immediate enforcement.
     */
    @Transactional
    public PodRegistrationEntity revokePod(UUID podId, String revokedBy, String reason) {
        PodRegistrationEntity pod = podRepo.findByPodId(podId)
                .orElseThrow(() -> new IllegalArgumentException("Pod not found: " + podId));

        if (pod.getStatus() == PodStatus.REVOKED) {
            throw new IllegalStateException("Pod is already revoked: " + podId);
        }

        Map<String, Object> before = podToMap(pod);

        Instant now = Instant.now();
        pod.setStatus(PodStatus.REVOKED);
        pod.setRevokedAt(now);
        pod.setRevokedBy(revokedBy);
        pod.setRevocationReason(reason);
        pod = podRepo.save(pod);

        Map<String, Object> after = podToMap(pod);

        // Update revocation cache immediately for real-time enforcement
        revocationCache.markRevoked(podId.toString());

        // Publish HIGH_PRI revocation event (uses control channel topic)
        DeltaPayload delta = new DeltaPayload("REVOKE", before, after, List.of("status", "revoked_at", "revoked_by", "revocation_reason"));
        publishOutboxEvent("POD_REVOCATION", pod.getPodId().toString(),
                "impilo.federation.pod.revoked.v1", Map.of(
                        "delta", delta.toMap(),
                        "full", after,
                        "pod_id", pod.getPodId().toString(),
                        "pod_name", pod.getPodName(),
                        "revoked_by", revokedBy,
                        "reason", reason,
                        "revoked_at", now.toString(),
                        "priority", "HIGH"));

        log.info("Pod revoked: id={}, by={}, reason={}", podId, revokedBy, reason);

        return pod;
    }

    /**
     * Reinstate a previously revoked pod.
     */
    @Transactional
    public PodRegistrationEntity reinstatePod(UUID podId, String reinstatedBy) {
        PodRegistrationEntity pod = podRepo.findByPodId(podId)
                .orElseThrow(() -> new IllegalArgumentException("Pod not found: " + podId));

        if (pod.getStatus() != PodStatus.REVOKED && pod.getStatus() != PodStatus.SUSPENDED) {
            throw new IllegalStateException("Pod is not in a revoked/suspended state: " + podId);
        }

        Map<String, Object> before = podToMap(pod);

        Instant now = Instant.now();
        pod.setStatus(PodStatus.ACTIVE);
        pod.setRevokedAt(null);
        pod.setRevokedBy(null);
        pod.setRevocationReason(null);
        pod.setIssuedAt(now);
        pod.setExpiresAt(now.plus(DEFAULT_REGISTRATION_VALIDITY));
        pod = podRepo.save(pod);

        Map<String, Object> after = podToMap(pod);

        // Clear from revocation cache
        revocationCache.clearRevocation(podId.toString());

        DeltaPayload delta = DeltaPayload.of(before, after, List.of("status", "revoked_at", "revoked_by", "revocation_reason", "issued_at", "expires_at"));
        publishOutboxEvent("POD_REGISTRATION", pod.getPodId().toString(),
                "impilo.federation.pod.reinstated.v1", Map.of(
                        "delta", delta.toMap(),
                        "full", after,
                        "pod_id", pod.getPodId().toString(),
                        "pod_name", pod.getPodName(),
                        "reinstated_by", reinstatedBy,
                        "reinstated_at", now.toString()));

        log.info("Pod reinstated: id={}, by={}", podId, reinstatedBy);

        return pod;
    }

    /**
     * Renew a pod's registration before it expires.
     */
    @Transactional
    public PodRegistrationEntity renewRegistration(UUID podId) {
        PodRegistrationEntity pod = podRepo.findByPodId(podId)
                .orElseThrow(() -> new IllegalArgumentException("Pod not found: " + podId));

        if (pod.getStatus() != PodStatus.ACTIVE) {
            throw new IllegalStateException("Only active pods can be renewed: " + podId);
        }

        Instant now = Instant.now();
        pod.setIssuedAt(now);
        pod.setExpiresAt(now.plus(DEFAULT_REGISTRATION_VALIDITY));
        pod = podRepo.save(pod);

        log.info("Pod registration renewed: id={}, expiresAt={}", podId, pod.getExpiresAt());
        return pod;
    }

    /**
     * Look up a pod's registration.
     */
    @Transactional(readOnly = true)
    public Optional<PodRegistrationEntity> findByPodId(UUID podId) {
        return podRepo.findByPodId(podId);
    }

    /**
     * Check if a pod is actively registered (not revoked, not expired).
     */
    @Transactional(readOnly = true)
    public boolean isPodActive(UUID podId) {
        return podRepo.findByPodId(podId)
                .map(pod -> pod.getStatus() == PodStatus.ACTIVE
                        && pod.getExpiresAt().isAfter(Instant.now()))
                .orElse(false);
    }

    /**
     * Get all active (non-revoked) pod registrations.
     */
    @Transactional(readOnly = true)
    public List<PodRegistrationEntity> getActiveRegistrations() {
        return podRepo.findByStatus(PodStatus.ACTIVE);
    }

    /**
     * Scope the requested data classes against national policy.
     * Only approved data classes are granted.
     */
    private List<String> scopeDataClasses(List<String> requested) {
        Set<String> allowed = Set.of("PATIENT", "ENCOUNTER", "OBSERVATION",
                "CONDITION", "MEDICATION", "ALLERGY", "PROCEDURE", "IMMUNIZATION");
        if (requested == null || requested.isEmpty()) {
            return List.of("PATIENT", "ENCOUNTER", "OBSERVATION");
        }
        return requested.stream()
                .filter(allowed::contains)
                .toList();
    }

    private Map<String, Object> podToMap(PodRegistrationEntity p) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("registration_id", p.getRegistrationId());
        map.put("pod_id", p.getPodId());
        map.put("pod_name", p.getPodName());
        map.put("region", p.getRegion());
        map.put("status", p.getStatus() != null ? p.getStatus().name() : null);
        map.put("issued_at", p.getIssuedAt());
        map.put("expires_at", p.getExpiresAt());
        map.put("revoked_at", p.getRevokedAt());
        map.put("revoked_by", p.getRevokedBy());
        map.put("revocation_reason", p.getRevocationReason());
        return map;
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                     String eventType, Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(toJson(payload));
        event.setCreatedAt(Instant.now());
        outboxRepo.save(event);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON", e);
        }
    }
}
