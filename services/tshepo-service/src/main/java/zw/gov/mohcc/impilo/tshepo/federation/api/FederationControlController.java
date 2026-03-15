package zw.gov.mohcc.impilo.tshepo.federation.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.federation.core.*;
import zw.gov.mohcc.impilo.tshepo.federation.persistence.PodRegistrationEntity;

import java.util.*;

/**
 * Federation Control API — manages pod registration, heartbeat, revocation.
 *
 * All endpoints are internal-only (behind Envoy ext_authz).
 * Only the national spine admin can register, revoke, or reinstate pods.
 */
@RestController
@RequestMapping("/internal/v1/federation")
public class FederationControlController {

    private final PodRegistrationService registrationService;
    private final PodRevocationCacheService revocationCache;

    public FederationControlController(PodRegistrationService registrationService,
                                        PodRevocationCacheService revocationCache) {
        this.registrationService = registrationService;
        this.revocationCache = revocationCache;
    }

    /**
     * Step 1: Pod registration request.
     * Pod submits its identity, certificate, and requested capabilities.
     * Spine responds with authority scope and heartbeat interval.
     */
    @PostMapping("/pods/register")
    public ResponseEntity<Map<String, Object>> registerPod(@RequestBody PodRegistrationRequest request) {
        PodRegistrationEntity pod = registrationService.registerPod(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toRegistrationAck(pod));
    }

    /**
     * Step 4: Heartbeat — pod signals it is alive and connected.
     */
    @PostMapping("/pods/{podId}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(@PathVariable UUID podId) {
        return registrationService.heartbeat(podId)
                .map(pod -> ResponseEntity.ok(Map.<String, Object>of(
                        "pod_id", pod.getPodId().toString(),
                        "status", pod.getStatus().name(),
                        "last_heartbeat_at", pod.getLastHeartbeatAt().toString())))
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "POD_NOT_ACTIVE",
                                "message", "Pod is not in an active state")));
    }

    /**
     * Revoke a pod's registration. Admin-only.
     */
    @PostMapping("/pods/{podId}/revoke")
    public ResponseEntity<Map<String, Object>> revokePod(
            @PathVariable UUID podId,
            @RequestBody Map<String, String> body) {
        String revokedBy = body.getOrDefault("revoked_by", "system");
        String reason = body.getOrDefault("reason", "ADMIN_REVOCATION");

        PodRegistrationEntity pod = registrationService.revokePod(podId, revokedBy, reason);
        return ResponseEntity.ok(Map.of(
                "pod_id", pod.getPodId().toString(),
                "status", pod.getStatus().name(),
                "revoked_at", pod.getRevokedAt().toString(),
                "revoked_by", revokedBy,
                "reason", reason));
    }

    /**
     * Reinstate a previously revoked pod. Admin-only.
     */
    @PostMapping("/pods/{podId}/reinstate")
    public ResponseEntity<Map<String, Object>> reinstatePod(
            @PathVariable UUID podId,
            @RequestBody Map<String, String> body) {
        String reinstatedBy = body.getOrDefault("reinstated_by", "system");

        PodRegistrationEntity pod = registrationService.reinstatePod(podId, reinstatedBy);
        return ResponseEntity.ok(Map.of(
                "pod_id", pod.getPodId().toString(),
                "status", pod.getStatus().name(),
                "expires_at", pod.getExpiresAt().toString()));
    }

    /**
     * Renew registration before expiry.
     */
    @PostMapping("/pods/{podId}/renew")
    public ResponseEntity<Map<String, Object>> renewRegistration(@PathVariable UUID podId) {
        PodRegistrationEntity pod = registrationService.renewRegistration(podId);
        return ResponseEntity.ok(Map.of(
                "pod_id", pod.getPodId().toString(),
                "status", pod.getStatus().name(),
                "expires_at", pod.getExpiresAt().toString()));
    }

    /**
     * Query pod registration status.
     */
    @GetMapping("/pods/{podId}")
    public ResponseEntity<Map<String, Object>> getPodStatus(@PathVariable UUID podId) {
        return registrationService.findByPodId(podId)
                .map(pod -> ResponseEntity.ok(toRegistrationAck(pod)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Check if a pod is currently revoked (fast cache lookup).
     */
    @GetMapping("/pods/{podId}/revocation-status")
    public ResponseEntity<Map<String, Object>> getRevocationStatus(@PathVariable UUID podId) {
        boolean revoked = revocationCache.isRevoked(podId.toString());
        return ResponseEntity.ok(Map.of(
                "pod_id", podId.toString(),
                "revoked", revoked));
    }

    /**
     * List all active pod registrations.
     */
    @GetMapping("/pods")
    public ResponseEntity<List<Map<String, Object>>> listActivePods() {
        List<Map<String, Object>> pods = registrationService.getActiveRegistrations()
                .stream()
                .map(this::toRegistrationAck)
                .toList();
        return ResponseEntity.ok(pods);
    }

    private Map<String, Object> toRegistrationAck(PodRegistrationEntity pod) {
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("registration_id", pod.getRegistrationId().toString());
        ack.put("pod_id", pod.getPodId().toString());
        ack.put("pod_name", pod.getPodName());
        ack.put("audience", "impilo-federation");
        ack.put("authority_scope", Map.of(
                "data_classes", pod.getGrantedDataClasses(),
                "max_offline_hours", pod.getMaxOfflineHours(),
                "merge_authority", pod.isMergeAuthority(),
                "revocation_priority", pod.getRevocationPriority()));
        ack.put("heartbeat_interval_seconds", pod.getHeartbeatIntervalSeconds());
        ack.put("status", pod.getStatus().name());
        ack.put("issued_at", pod.getIssuedAt().toString());
        ack.put("expires_at", pod.getExpiresAt().toString());
        if (pod.getLastHeartbeatAt() != null) {
            ack.put("last_heartbeat_at", pod.getLastHeartbeatAt().toString());
        }
        return ack;
    }
}
