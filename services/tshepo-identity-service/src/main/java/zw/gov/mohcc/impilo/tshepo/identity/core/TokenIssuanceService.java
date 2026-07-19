package zw.gov.mohcc.impilo.tshepo.identity.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.*;
import zw.gov.mohcc.impilo.tshepo.identity.config.IdentityProperties;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.ScopedTokenEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.ScopedTokenRepository;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Issues, introspects, and revokes scoped JWS tokens.
 *
 * <h3>Token flow:</h3>
 * <ol>
 *   <li>Build JWT claims (tenantId, actorId, purpose, scope, subjectRef, exp, jti)</li>
 *   <li>Serialize as JWS with Ed25519 signature via tshepo-keys-service POST /v1/sign</li>
 *   <li>Persist token metadata in scoped_token table for revocation/introspection</li>
 * </ol>
 *
 * <p>Tokens are NOT OIDC tokens. They are internal Impilo service-scoped tokens
 * with short TTL (default 300s).</p>
 */
@Service
public class TokenIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(TokenIssuanceService.class);

    private final ScopedTokenRepository tokenRepo;
    private final EventOutboxRepository outboxRepo;
    private final RestTemplate keysRestTemplate;
    private final ObjectMapper objectMapper;
    private final int defaultTtlSeconds;

    public TokenIssuanceService(ScopedTokenRepository tokenRepo,
                                 EventOutboxRepository outboxRepo,
                                 @Qualifier("keysRestTemplate") RestTemplate keysRestTemplate,
                                 ObjectMapper objectMapper,
                                 IdentityProperties properties) {
        this.tokenRepo = tokenRepo;
        this.outboxRepo = outboxRepo;
        this.keysRestTemplate = keysRestTemplate;
        this.objectMapper = objectMapper;
        this.defaultTtlSeconds = properties.tokenTtlSeconds();
    }

    /**
     * Issue a scoped access token.
     *
     * <p>The token is a JWS signed with Ed25519 by the keys-service. It carries
     * the tenantId, actorId, purpose, scope, subjectRef, and expiry. The JTI
     * is a random UUID stored in the scoped_token table for revocation.</p>
     */
    @Transactional
    public ScopedTokenResponse issueToken(IssueScopedTokenRequest request) {
        int ttl = (request.ttlSeconds() != null && request.ttlSeconds() > 0)
                ? request.ttlSeconds()
                : defaultTtlSeconds;

        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttl);

        // Build JWT claims
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(jti)
                .issuer("tshepo-identity-service")
                .claim("tenant_id", request.tenantId().toString())
                .claim("actor_id", request.actorId())
                .claim("purpose", request.purpose())
                .claim("scope", request.scope())
                .claim("target_service", request.targetService())
                .claim("sub_ref", request.subjectRef())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();

        // Sign via keys-service
        String signedToken = signViaKeysService(claims);

        // Persist token record for revocation/introspection
        ScopedTokenEntity entity = new ScopedTokenEntity();
        entity.setTenantId(request.tenantId());
        entity.setActorId(request.actorId());
        entity.setTargetService(request.targetService());
        entity.setScope(request.scope());
        entity.setSubjectRef(request.subjectRef());
        entity.setJti(jti);
        entity.setExpiresAt(expiresAt);
        entity.setStatus("ACTIVE");
        tokenRepo.save(entity);

        publishOutboxEvent("ScopedToken", jti, "TOKEN_ISSUED",
                Map.of("tenantId", request.tenantId(),
                       "actorId", request.actorId(),
                       "targetService", request.targetService(),
                       "jti", jti));

        log.info("Issued scoped token: jti={}, actor={}, target={}, ttl={}s",
                jti, request.actorId(), request.targetService(), ttl);

        return new ScopedTokenResponse(signedToken, jti, request.scope(),
                request.targetService(), expiresAt);
    }

    /** Default work-session token TTL (D-P3: 15-min silent reissue while context unchanged). */
    static final int WORK_CONTEXT_DEFAULT_TTL_SECONDS = 900;

    /**
     * Issue a duty-scoped WORK_CONTEXT token (D-P3). The caller (BFF) has
     * already proven the assignment against Vashandi; this method binds the
     * proven facility/workspace context into a short-lived revocable token and
     * — on a context switch — revokes the session's previous work token first,
     * so two contexts are never live for one session.
     */
    @Transactional
    public ScopedTokenResponse issueWorkContextToken(IssueWorkContextTokenRequest request) {
        if (request.previousJti() != null && !request.previousJti().isBlank()) {
            tokenRepo.findByTenantIdAndJti(request.tenantId(), request.previousJti())
                    .filter(t -> "ACTIVE".equals(t.getStatus()))
                    .ifPresent(t -> {
                        t.setStatus("REVOKED");
                        t.setRevokedAt(Instant.now());
                        tokenRepo.save(t);
                        log.info("Work-context switch: revoked previous token jti={}", t.getJti());
                    });
        }

        int ttl = (request.ttlSeconds() != null && request.ttlSeconds() > 0)
                ? request.ttlSeconds()
                : WORK_CONTEXT_DEFAULT_TTL_SECONDS;

        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttl);

        // Stable-for-the-session context only; live licence/scope truth stays PDP-resolved.
        Map<String, Object> context = new java.util.LinkedHashMap<>();
        context.put("provider_id", request.providerPublicId());
        context.put("facility_id", request.facilityId().toString());
        if (request.departmentId() != null) {
            context.put("department_id", request.departmentId().toString());
        }
        if (request.workspaceId() != null) {
            context.put("workspace_id", request.workspaceId().toString());
        }
        if (request.roleTemplateId() != null && !request.roleTemplateId().isBlank()) {
            context.put("role", request.roleTemplateId());
        }
        context.put("purpose_of_use", request.purposeOfUse() != null ? request.purposeOfUse() : "TREATMENT");
        if (request.sessionAssurance() != null && !request.sessionAssurance().isBlank()) {
            context.put("session_assurance", request.sessionAssurance());
        }

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .jwtID(jti)
                .issuer("tshepo-identity-service")
                .claim("tenant_id", request.tenantId().toString())
                .claim("actor_id", request.actorId())
                .claim("token_kind", "WORK_CONTEXT")
                .claim("scope", "work:context")
                .claim("target_service", "tshepo-authz")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt));
        context.forEach(claims::claim);

        String signedToken = signViaKeysService(claims.build());

        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise work-context claims", e);
        }

        ScopedTokenEntity entity = new ScopedTokenEntity();
        entity.setTenantId(request.tenantId());
        entity.setActorId(request.actorId());
        entity.setTargetService("tshepo-authz");
        entity.setScope("work:context");
        entity.setSubjectRef(request.providerPublicId());
        entity.setJti(jti);
        entity.setExpiresAt(expiresAt);
        entity.setStatus("ACTIVE");
        entity.setTokenKind("WORK_CONTEXT");
        entity.setContextClaims(contextJson);
        tokenRepo.save(entity);

        publishOutboxEvent("ScopedToken", jti, "WORK_CONTEXT_TOKEN_ISSUED",
                Map.of("tenantId", request.tenantId(),
                       "actorId", request.actorId(),
                       "providerPublicId", request.providerPublicId(),
                       "facilityId", request.facilityId().toString(),
                       "jti", jti));

        log.info("Issued work-context token: jti={}, actor={}, facility={}, workspace={}, ttl={}s",
                jti, request.actorId(), request.facilityId(), request.workspaceId(), ttl);

        return new ScopedTokenResponse(signedToken, jti, "work:context", "tshepo-authz", expiresAt);
    }

    /**
     * Introspect a token: check validity by JTI, expiry, and revocation status.
     *
     * <p>This does NOT cryptographically verify the JWS signature (that is the
     * responsibility of the consuming service using the public key). This endpoint
     * checks the server-side state: is the token still active and not expired?</p>
     */
    @Transactional(readOnly = true)
    public IntrospectResponse introspect(IntrospectRequest request) {
        // Parse the token to extract the JTI
        String jti;
        try {
            SignedJWT signedJWT = SignedJWT.parse(request.token());
            jti = signedJWT.getJWTClaimsSet().getJWTID();
            if (jti == null || jti.isBlank()) {
                return inactiveIntrospectResponse();
            }
        } catch (ParseException e) {
            log.warn("Failed to parse token for introspection: {}", e.getMessage());
            return inactiveIntrospectResponse();
        }

        // Look up in database
        ScopedTokenEntity entity = tokenRepo.findByJti(jti).orElse(null);
        if (entity == null) {
            return inactiveIntrospectResponse();
        }

        // Check revocation
        if ("REVOKED".equals(entity.getStatus()) || entity.getRevokedAt() != null) {
            return inactiveIntrospectResponse();
        }

        // Check expiry
        if (Instant.now().isAfter(entity.getExpiresAt())) {
            return inactiveIntrospectResponse();
        }

        return new IntrospectResponse(
                true,
                entity.getJti(),
                entity.getTenantId(),
                entity.getActorId(),
                entity.getScope(),
                entity.getTargetService(),
                entity.getSubjectRef(),
                entity.getExpiresAt()
        );
    }

    /**
     * Revoke a token by its JTI.
     */
    @Transactional
    public void revokeToken(String jti) {
        ScopedTokenEntity entity = tokenRepo.findByJti(jti)
                .orElseThrow(() -> new IdentityNotFoundException("Token not found"));

        entity.setStatus("REVOKED");
        entity.setRevokedAt(Instant.now());
        tokenRepo.save(entity);

        publishOutboxEvent("ScopedToken", jti, "TOKEN_REVOKED",
                Map.of("jti", jti, "revokedAt", Instant.now().toString()));

        log.info("Revoked scoped token: jti={}", jti);
    }

    // ── Internal methods ────────────────────────────────────────────────────

    /**
     * Signs the JWT claims by calling tshepo-keys-service POST /v1/sign.
     *
     * <p>Request body: { "algorithm": "EdDSA", "payload": "<base64url-encoded claims>" }
     * Expected response: { "data": { "signature": "<base64url-encoded signature>" } }</p>
     *
     * <p>We construct the full JWS compact serialization:
     * header.payload.signature</p>
     */
    private String signViaKeysService(JWTClaimsSet claims) {
        try {
            // Build the JWS header
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                    .type(JOSEObjectType.JWT)
                    .build();

            // Create the signing input (header.payload). claims.toPayload()
            // emits canonical JSON — claims.toJSONObject().toString() emits Java
            // Map.toString ({k=v, ...}), producing a malformed non-JSON payload
            // that every consumer fails to parse even though signing succeeds.
            Base64URL headerB64 = header.toBase64URL();
            Base64URL payloadB64 = Base64URL.encode(claims.toPayload().toBytes());
            String signingInput = headerB64 + "." + payloadB64;

            // Call keys-service for signature. The /v1/sign contract requires
            // tenantId (validated @NotNull) + jwsCompact=false for a raw
            // signature over the signing input; the response is FLAT
            // {keyId, algorithm, signature}, not wrapped in {data}.
            String tenant = claims.getStringClaim("tenant_id");
            Map<String, Object> signRequest = new java.util.LinkedHashMap<>();
            signRequest.put("tenantId", tenant);
            signRequest.put("payload", signingInput);
            signRequest.put("jwsCompact", false);

            ResponseEntity<String> response = keysRestTemplate.postForEntity(
                    "/v1/sign", signRequest, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new IllegalStateException("Keys service returned non-OK response");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String signature = root.path("signature").asText();
            if (signature == null || signature.isBlank()) {
                throw new IllegalStateException("Keys service returned empty signature");
            }

            // Construct the compact JWS: header.payload.signature
            return signingInput + "." + signature;
        } catch (Exception e) {
            log.error("Failed to sign token via keys-service: {}", e.getMessage());
            throw new IllegalStateException("Token signing failed", e);
        }
    }

    private IntrospectResponse inactiveIntrospectResponse() {
        return new IntrospectResponse(false, null, null, null, null, null, null, null);
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                     String eventType, Object payload) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            outboxRepo.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }
}
