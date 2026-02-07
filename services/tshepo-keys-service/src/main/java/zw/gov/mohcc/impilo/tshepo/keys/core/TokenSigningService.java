package zw.gov.mohcc.impilo.tshepo.keys.core;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Issues scoped, short-lived JWS tokens for service-to-service trust.
 *
 * <p>These are NOT OIDC tokens. They are internal Impilo service tokens
 * signed with Ed25519 and carrying: tenant, actor, purpose, scope,
 * subject reference, and expiry. Used by identity-service and offline-service.</p>
 *
 * <p>Token claims:</p>
 * <ul>
 *   <li>{@code iss} — "tshepo-keys-service"</li>
 *   <li>{@code sub} — actor ID</li>
 *   <li>{@code aud} — target service</li>
 *   <li>{@code iat} — issued at (epoch seconds)</li>
 *   <li>{@code exp} — expiry (epoch seconds)</li>
 *   <li>{@code tid} — tenant ID</li>
 *   <li>{@code act} — actor type</li>
 *   <li>{@code pur} — purpose of use</li>
 *   <li>{@code scp} — scope</li>
 *   <li>{@code fid} — facility ID (optional)</li>
 *   <li>{@code shid} — subject health ID (optional)</li>
 *   <li>{@code jti} — unique token ID</li>
 * </ul>
 */
@Service
public class TokenSigningService {

    private static final Logger log = LoggerFactory.getLogger(TokenSigningService.class);
    private static final String ISSUER = "tshepo-keys-service";

    private final Ed25519SigningService signingService;
    private final KeysProperties keysProperties;
    private final ObjectMapper objectMapper;

    public TokenSigningService(Ed25519SigningService signingService,
                                KeysProperties keysProperties) {
        this.signingService = signingService;
        this.keysProperties = keysProperties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Issue a scoped, short-lived JWS token.
     *
     * @param tenantId        tenant context
     * @param actorId         the authenticated principal
     * @param actorType       PROVIDER, ADMIN, CITIZEN, SYSTEM, SERVICE
     * @param purpose         purpose of use
     * @param targetService   which service this token is for
     * @param scope           the scope of access
     * @param facilityId      optional facility scope
     * @param subjectHealthId optional subject health ID
     * @param ttlSeconds      TTL in seconds; uses default if <= 0
     * @return JWS compact serialization
     */
    public String issueToken(UUID tenantId, String actorId, String actorType,
                              String purpose, String targetService, String scope,
                              UUID facilityId, UUID subjectHealthId, int ttlSeconds) {

        if (ttlSeconds <= 0) {
            ttlSeconds = keysProperties.getTokenDefaultTtlSeconds();
        }

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(ttlSeconds);
        String jti = UUID.randomUUID().toString();

        // Build claims as JSON
        ObjectNode claims = objectMapper.createObjectNode();
        claims.put("iss", ISSUER);
        claims.put("sub", actorId);
        claims.put("aud", targetService);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiry.getEpochSecond());
        claims.put("tid", tenantId.toString());
        claims.put("act", actorType);
        claims.put("pur", purpose);
        claims.put("scp", scope);
        claims.put("jti", jti);

        if (facilityId != null) {
            claims.put("fid", facilityId.toString());
        }
        if (subjectHealthId != null) {
            claims.put("shid", subjectHealthId.toString());
        }

        // Get the current active key and sign
        SigningKeyEntity keyEntity = signingService.getCurrentActiveKey(tenantId);
        byte[] privateKeyBytes = signingService.decryptPrivateKey(keyEntity.getPrivateKeyEncrypted());
        byte[] publicKeyBytes = signingService.decodePublicKeyPem(keyEntity.getPublicKeyPem());

        try {
            OctetKeyPair jwk = new OctetKeyPair.Builder(
                    Curve.Ed25519,
                    Base64URL.encode(publicKeyBytes))
                    .d(Base64URL.encode(privateKeyBytes))
                    .keyID(keyEntity.getKeyId())
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                    .keyID(keyEntity.getKeyId())
                    .build();

            JWSObject jwsObject = new JWSObject(header, new Payload(claims.toString()));
            jwsObject.sign(new Ed25519Signer(jwk));

            String token = jwsObject.serialize();

            log.debug("Issued token: jti={}, sub={}, aud={}, exp={}", jti, actorId, targetService, expiry);
            return token;

        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign service token", e);
        }
    }

    /**
     * Compute the expiry timestamp for a token issued now with the given TTL.
     */
    public long computeExpiresAt(int ttlSeconds) {
        if (ttlSeconds <= 0) {
            ttlSeconds = keysProperties.getTokenDefaultTtlSeconds();
        }
        return Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
    }
}
