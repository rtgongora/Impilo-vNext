package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityTokenRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityTokenResponse;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityVerifyResponse;
import zw.gov.mohcc.impilo.tshepo.offline.client.AuthzServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.client.KeysServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.config.OfflineProperties;
import zw.gov.mohcc.impilo.tshepo.offline.exception.CapabilityTokenNotFoundException;
import zw.gov.mohcc.impilo.tshepo.offline.exception.OfflineServiceException;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.CapabilityTokenEntity;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.CapabilityTokenRepository;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.EventOutboxRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for issuing, revoking, and verifying offline capability tokens.
 *
 * <p>Capability tokens are JWS-signed (Ed25519 via keys-service), facility-scoped,
 * and have a short TTL. They define exactly what operations an actor may perform
 * while their device is offline.</p>
 */
@Service
public class CapabilityTokenService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityTokenService.class);
    /** Purpose-scoped key the keys-service signs capability tokens with (fail-closed). */
    private static final String SIGNING_PURPOSE = "OFFLINE_CAPABILITY";

    private final CapabilityTokenRepository tokenRepo;
    private final EventOutboxRepository outboxRepo;
    private final AuthzServiceClient authzClient;
    private final KeysServiceClient keysClient;
    private final OfflineProperties offlineProperties;
    private final ObjectMapper objectMapper;
    private final CapabilityTokenJwsVerifier jwsVerifier;

    public CapabilityTokenService(CapabilityTokenRepository tokenRepo,
                                   EventOutboxRepository outboxRepo,
                                   AuthzServiceClient authzClient,
                                   KeysServiceClient keysClient,
                                   OfflineProperties offlineProperties,
                                   ObjectMapper objectMapper,
                                   CapabilityTokenJwsVerifier jwsVerifier) {
        this.tokenRepo = tokenRepo;
        this.outboxRepo = outboxRepo;
        this.authzClient = authzClient;
        this.keysClient = keysClient;
        this.offlineProperties = offlineProperties;
        this.objectMapper = objectMapper;
        this.jwsVerifier = jwsVerifier;
    }

    /**
     * Issue a new offline capability token after validating the actor has facility access.
     */
    @Transactional
    public CapabilityTokenResponse issueToken(CapabilityTokenRequest request) {
        log.info("Issuing capability token: tenant={}, actor={}, facility={}",
                request.tenantId(), request.actorId(), request.facilityId());

        // Step 1: Validate actor has access to the requested facility
        boolean hasAccess = authzClient.checkFacilityAccess(
                request.tenantId(), request.actorId(), request.facilityId());
        if (!hasAccess) {
            throw new OfflineServiceException("FACILITY_ACCESS_DENIED",
                    "Actor " + request.actorId() + " does not have access to facility " + request.facilityId());
        }

        // Step 2: Determine capabilities — intersect requested with allowed
        List<String> capabilities = resolveCapabilities(request.requestedCapabilities());

        // Step 3: Determine TTL
        int ttlHours = resolveTtlHours(request.ttlHours());

        // Step 4: Build the token entity
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttlHours, ChronoUnit.HOURS);

        String capabilitiesJson = toJson(capabilities);

        CapabilityTokenEntity entity = new CapabilityTokenEntity();
        entity.setTenantId(request.tenantId());
        entity.setActorId(request.actorId());
        entity.setFacilityId(request.facilityId());
        entity.setDeviceFingerprint(request.deviceFingerprint());
        entity.setCapabilities(capabilitiesJson);
        entity.setIssuedAt(now);
        entity.setExpiresAt(expiresAt);
        entity.setStatus("ACTIVE");

        entity = tokenRepo.save(entity);

        // Step 5: Build JWT claims and sign with keys-service
        Map<String, Object> claims = buildTokenClaims(entity, capabilities);
        String claimsJson = toJson(claims);
        String signedToken = keysClient.signPayload(entity.getTenantId(), claimsJson, SIGNING_PURPOSE);

        // Step 6: Write outbox event
        writeOutboxEvent("CapabilityToken", entity.getId().toString(),
                "CAPABILITY_TOKEN_ISSUED", buildEventPayload(entity, "ISSUED"));

        log.info("Issued capability token id={}, expires={}", entity.getId(), expiresAt);

        return new CapabilityTokenResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getActorId(),
                entity.getFacilityId(),
                entity.getDeviceFingerprint(),
                capabilities,
                signedToken,
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getStatus()
        );
    }

    /**
     * Retrieve a capability token by ID (tenant-isolated).
     */
    @Transactional(readOnly = true)
    public CapabilityTokenResponse getToken(UUID tokenId, UUID tenantId) {
        CapabilityTokenEntity entity = tokenRepo.findByIdAndTenantId(tokenId, tenantId)
                .orElseThrow(() -> new CapabilityTokenNotFoundException(tokenId));

        List<String> capabilities = parseCapabilities(entity.getCapabilities());

        return new CapabilityTokenResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getActorId(),
                entity.getFacilityId(),
                entity.getDeviceFingerprint(),
                capabilities,
                null, // signed token not returned on GET
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getStatus()
        );
    }

    /**
     * Revoke an active capability token.
     */
    @Transactional
    public void revokeToken(UUID tokenId, UUID tenantId) {
        log.info("Revoking capability token: id={}, tenant={}", tokenId, tenantId);

        CapabilityTokenEntity entity = tokenRepo.findByIdAndTenantId(tokenId, tenantId)
                .orElseThrow(() -> new CapabilityTokenNotFoundException(tokenId));

        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new OfflineServiceException("TOKEN_NOT_ACTIVE",
                    "Token " + tokenId + " is not active (status=" + entity.getStatus() + ")");
        }

        entity.setStatus("REVOKED");
        entity.setRevokedAt(Instant.now());
        tokenRepo.save(entity);

        writeOutboxEvent("CapabilityToken", entity.getId().toString(),
                "CAPABILITY_TOKEN_REVOKED", buildEventPayload(entity, "REVOKED"));

        log.info("Revoked capability token id={}", tokenId);
    }

    /**
     * Verify a signed capability token: check JWS signature, expiry, and revocation status.
     */
    @Transactional
    public CapabilityVerifyResponse verifyToken(CapabilityVerifyRequest request) {
        log.debug("Verifying capability token");

        // Step 1: Cryptographic JWS verification (local, against JWKS) BEFORE any claim
        // is trusted — fail closed on malformed / unsupported-alg / unknown-kid / bad
        // signature / expired token. This replaces the previous DB-existence-only check.
        CapabilityTokenJwsVerifier.Result jws = jwsVerifier.verify(request.signedToken());
        if (!jws.valid()) {
            writeOutboxEvent("CapabilityToken", "unknown", "CAPABILITY_TOKEN_VERIFY_REJECTED",
                    "{\"reason\":\"" + jws.reason() + "\"}");
            return CapabilityVerifyResponse.invalid("Token signature/format invalid: " + jws.reason());
        }

        // Step 2: Read the cryptographically-verified token id from the signed claims.
        String tokenIdStr;
        try {
            tokenIdStr = jws.claims().getJWTID();
        } catch (Exception e) {
            tokenIdStr = null;
        }
        if (tokenIdStr == null || tokenIdStr.isBlank()) {
            return CapabilityVerifyResponse.invalid("Missing token ID in verified claims");
        }
        UUID tokenId;
        try {
            tokenId = UUID.fromString(tokenIdStr);
        } catch (IllegalArgumentException e) {
            return CapabilityVerifyResponse.invalid("Invalid token ID");
        }

        // Step 3: Revocation / status / expiry checks against the registry.
        Optional<CapabilityTokenEntity> entityOpt = tokenRepo.findByIdAndTenantId(tokenId, request.tenantId());
        if (entityOpt.isEmpty()) {
            writeOutboxEvent("CapabilityToken", tokenId.toString(), "CAPABILITY_TOKEN_VERIFY_REJECTED",
                    "{\"reason\":\"NOT_FOUND\"}");
            return CapabilityVerifyResponse.invalid("Token not found");
        }
        CapabilityTokenEntity entity = entityOpt.get();
        if ("REVOKED".equals(entity.getStatus())) {
            writeOutboxEvent("CapabilityToken", tokenId.toString(), "CAPABILITY_TOKEN_VERIFY_REJECTED",
                    "{\"reason\":\"REVOKED\"}");
            return CapabilityVerifyResponse.invalid("Token has been revoked");
        }
        if (Instant.now().isAfter(entity.getExpiresAt())) {
            writeOutboxEvent("CapabilityToken", tokenId.toString(), "CAPABILITY_TOKEN_VERIFY_REJECTED",
                    "{\"reason\":\"EXPIRED\"}");
            return CapabilityVerifyResponse.invalid("Token has expired");
        }
        if (!"ACTIVE".equals(entity.getStatus())) {
            writeOutboxEvent("CapabilityToken", tokenId.toString(), "CAPABILITY_TOKEN_VERIFY_REJECTED",
                    "{\"reason\":\"NOT_ACTIVE\"}");
            return CapabilityVerifyResponse.invalid("Token is not active (status=" + entity.getStatus() + ")");
        }

        List<String> capabilities = parseCapabilities(entity.getCapabilities());
        writeOutboxEvent("CapabilityToken", entity.getId().toString(), "CAPABILITY_TOKEN_VERIFY_ACCEPTED",
                "{\"actorId\":\"" + entity.getActorId() + "\"}");
        return CapabilityVerifyResponse.valid(
                entity.getId(),
                entity.getActorId(),
                entity.getFacilityId(),
                capabilities,
                entity.getExpiresAt()
        );
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private List<String> resolveCapabilities(List<String> requested) {
        List<String> allowed = offlineProperties.allowedOfflineActions();
        if (requested == null || requested.isEmpty()) {
            return List.copyOf(allowed);
        }
        // Intersect requested with allowed
        List<String> resolved = new ArrayList<>();
        for (String cap : requested) {
            if (allowed.contains(cap)) {
                resolved.add(cap);
            }
        }
        if (resolved.isEmpty()) {
            throw new OfflineServiceException("NO_VALID_CAPABILITIES",
                    "None of the requested capabilities are allowed for offline use");
        }
        return List.copyOf(resolved);
    }

    private int resolveTtlHours(Integer requestedTtl) {
        if (requestedTtl == null || requestedTtl <= 0) {
            return offlineProperties.defaultCapabilityTtlHours();
        }
        return Math.min(requestedTtl, offlineProperties.maxCapabilityTtlHours());
    }

    private Map<String, Object> buildTokenClaims(CapabilityTokenEntity entity, List<String> capabilities) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("jti", entity.getId().toString());
        claims.put("iss", offlineProperties.tokenIssuer());
        claims.put("sub", entity.getActorId());
        claims.put("aud", offlineProperties.tokenAudience());
        claims.put("iat", entity.getIssuedAt().getEpochSecond());
        claims.put("exp", entity.getExpiresAt().getEpochSecond());
        claims.put("tenant_id", entity.getTenantId().toString());
        claims.put("facility_id", entity.getFacilityId().toString());
        claims.put("device_fingerprint", entity.getDeviceFingerprint());
        claims.put("capabilities", capabilities);
        claims.put("max_offline_encounters", offlineProperties.maxOfflineEncounters());
        return claims;
    }

    private List<String> parseCapabilities(String capabilitiesJson) {
        try {
            return objectMapper.readValue(capabilitiesJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse capabilities JSON: {}", capabilitiesJson, e);
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OfflineServiceException("SERIALIZATION_ERROR", "Failed to serialize to JSON", e);
        }
    }

    private void writeOutboxEvent(String aggregateType, String aggregateId,
                                   String eventType, String payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setCreatedAt(Instant.now());
        outboxRepo.save(outbox);
    }

    private String buildEventPayload(CapabilityTokenEntity entity, String action) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("tokenId", entity.getId().toString());
        event.put("tenantId", entity.getTenantId().toString());
        event.put("actorId", entity.getActorId());
        event.put("facilityId", entity.getFacilityId().toString());
        event.put("action", action);
        event.put("timestamp", Instant.now().toString());
        return toJson(event);
    }
}
