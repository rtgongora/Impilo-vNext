package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.ProviderDeviceCredentialEntity;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.ProviderDeviceCredentialRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Device-bound provider offline credentials (PJ13, D-P9).
 *
 * <p>Issued ONLY from an authenticated online session (a session ref is
 * mandatory — no cold issuance), carrying a cached provider/facility/role
 * status snapshot and a high-risk-action denylist. Offline login validates
 * against the snapshot; a high-risk action is refused offline; no new
 * professional identity is ever created here. On reconnection the credential is
 * revalidated — this service never mints identity, only caches an already-
 * proven one.</p>
 */
@Service
public class ProviderDeviceCredentialService {

    /** Default offline credential lifetime (D-P9 open question — 72h working default). */
    static final long DEFAULT_TTL_HOURS = 72;

    /** Action classes that must stay online (the doctrine's restricted set). */
    static final Set<String> DEFAULT_HIGH_RISK_DENYLIST = Set.of(
            "PRESCRIBE", "SIGN_RECORD", "CLOSE_SENSITIVE_RECORD", "DISPENSE_CONTROLLED",
            "BREAK_GLASS", "CONSENT_CHANGE");

    private static final Logger log = LoggerFactory.getLogger(ProviderDeviceCredentialService.class);

    private final ProviderDeviceCredentialRepository repository;
    private final ObjectMapper objectMapper;

    public ProviderDeviceCredentialService(ProviderDeviceCredentialRepository repository,
                                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public record IssueRequest(UUID tenantId, String providerPublicId, String personHealthId,
                               String deviceId, UUID facilityId, Map<String, Object> statusSnapshot,
                               String onlineSessionRef, Long ttlHours) {}

    public record OfflineDecision(boolean allowed, String reason) {}

    @Transactional
    public ProviderDeviceCredentialEntity issue(IssueRequest request) {
        if (request.onlineSessionRef() == null || request.onlineSessionRef().isBlank()) {
            // No cold issuance — the credential is only ever minted from a live,
            // authenticated online session (doctrine: authenticate online at least once).
            throw new IllegalArgumentException("An online session reference is required to issue an offline credential");
        }
        if (request.providerPublicId() == null || request.personHealthId() == null
                || request.deviceId() == null || request.facilityId() == null) {
            throw new IllegalArgumentException("provider, person, device and facility are all required");
        }

        // Supersede any prior active credential for this (provider, device).
        repository.findByTenantIdAndProviderPublicIdAndDeviceIdAndStatus(
                        request.tenantId(), request.providerPublicId(), request.deviceId(),
                        ProviderDeviceCredentialEntity.STATUS_ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(ProviderDeviceCredentialEntity.STATUS_REVOKED);
                    existing.setRevokedAt(Instant.now());
                    repository.save(existing);
                });

        long ttl = request.ttlHours() != null && request.ttlHours() > 0
                ? request.ttlHours() : DEFAULT_TTL_HOURS;

        ProviderDeviceCredentialEntity credential = new ProviderDeviceCredentialEntity();
        credential.setTenantId(request.tenantId());
        credential.setProviderPublicId(request.providerPublicId());
        credential.setPersonHealthId(request.personHealthId());
        credential.setDeviceId(request.deviceId());
        credential.setFacilityId(request.facilityId());
        credential.setStatusSnapshot(toJson(request.statusSnapshot()));
        credential.setHighRiskDenylist(toJson(DEFAULT_HIGH_RISK_DENYLIST));
        credential.setIssuedFromSession(request.onlineSessionRef());
        credential.setExpiresAt(Instant.now().plus(ttl, ChronoUnit.HOURS));
        credential = repository.save(credential);
        log.info("provider device credential issued for {} on device {} (ttl {}h)",
                request.providerPublicId(), request.deviceId(), ttl);
        return credential;
    }

    /**
     * Offline authorisation decision: an ACTIVE, unexpired credential permits an
     * action UNLESS it is on the high-risk denylist (those stay online). Any
     * missing/expired/revoked credential denies. Never mints identity.
     */
    @Transactional(readOnly = true)
    public OfflineDecision decideOffline(UUID tenantId, String providerPublicId, String deviceId,
                                         String action) {
        Optional<ProviderDeviceCredentialEntity> credential = repository
                .findByTenantIdAndProviderPublicIdAndDeviceIdAndStatus(
                        tenantId, providerPublicId, deviceId,
                        ProviderDeviceCredentialEntity.STATUS_ACTIVE);
        if (credential.isEmpty()) {
            return new OfflineDecision(false, "NO_OFFLINE_CREDENTIAL");
        }
        if (credential.get().getExpiresAt().isBefore(Instant.now())) {
            return new OfflineDecision(false, "OFFLINE_CREDENTIAL_EXPIRED");
        }
        String normalisedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (highRiskDenylist(credential.get()).contains(normalisedAction)) {
            return new OfflineDecision(false, "HIGH_RISK_ACTION_REQUIRES_ONLINE");
        }
        return new OfflineDecision(true, "OFFLINE_CACHED_STATUS");
    }

    private Set<String> highRiskDenylist(ProviderDeviceCredentialEntity credential) {
        try {
            List<String> list = objectMapper.readValue(credential.getHighRiskDenylist(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return Set.copyOf(list);
        } catch (Exception e) {
            // Fail-closed: an unreadable denylist restricts everything high-risk.
            return DEFAULT_HIGH_RISK_DENYLIST;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise credential payload", e);
        }
    }
}
