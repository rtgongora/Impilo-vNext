package zw.gov.mohcc.impilo.tshepo.authz.session;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.*;

/**
 * Keycloak OIDC session validation adapter.
 *
 * <p>Validates JWTs issued by Keycloak by verifying the signature against
 * the Keycloak realm's JWKS endpoint. Extracts realm_access.roles,
 * actor type from custom claims, tenant ID from organization, and LoA
 * from the acr claim.</p>
 */
@Component
public class KeycloakAdapter implements SessionAssurance {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdapter.class);

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    @PostConstruct
    public void init() {
        try {
            String jwksUri = issuerUri + "/protocol/openid-connect/certs";
            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                    .create(new URL(jwksUri))
                    .retrying(true)
                    .build();

            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);

            jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(keySelector);

            log.info("Keycloak adapter initialized with issuer: {}", issuerUri);
        } catch (Exception e) {
            log.warn("Keycloak adapter initialization deferred — JWKS endpoint not available: {}", e.getMessage());
            jwtProcessor = null;
        }
    }

    @Override
    public boolean canHandle(String token) {
        try {
            com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(token);
            String tokenIssuer = signedJWT.getJWTClaimsSet().getIssuer();
            if (tokenIssuer == null) return false;
            if (tokenIssuer.equals(issuerUri)) return true;
            // Accept tokens where the realm path matches, regardless of hostname
            // (handles Docker-internal vs external hostname mismatch)
            String realmSuffix = issuerUri.replaceFirst("https?://[^/]+", "");
            return tokenIssuer.endsWith(realmSuffix);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public SessionInfo validateSession(String token) throws SessionValidationException {
        if (jwtProcessor == null) {
            throw new SessionValidationException("IDP_UNAVAILABLE",
                    "Keycloak JWKS endpoint not initialized");
        }

        try {
            JWTClaimsSet claims = jwtProcessor.process(token, null);

            // Validate expiration
            Date expiration = claims.getExpirationTime();
            if (expiration != null && expiration.before(new Date())) {
                throw new SessionValidationException("TOKEN_EXPIRED", "JWT has expired");
            }

            // Extract subject (actor ID)
            String actorId = claims.getSubject();
            if (actorId == null || actorId.isBlank()) {
                throw new SessionValidationException("INVALID_TOKEN", "Missing sub claim");
            }

            // Extract roles from realm_access.roles
            List<String> roles = extractRealmRoles(claims);

            // Extract actor type from custom claim or derive from roles
            String actorType = deriveActorType(claims, roles);

            // Extract tenant ID from organization claim or azp
            UUID tenantId = extractTenantId(claims);

            // Extract LoA from acr claim
            int loaLevel = extractLoaLevel(claims);

            // Extract session ID
            String sessionId = claims.getStringClaim("sid");

            return new SessionInfo(actorId, actorType, roles, tenantId, loaLevel,
                    sessionId, issuerUri);

        } catch (SessionValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionValidationException("TOKEN_VALIDATION_FAILED",
                    "JWT validation failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(JWTClaimsSet claims) {
        try {
            Map<String, Object> realmAccess =
                    (Map<String, Object>) claims.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                List<String> roles = (List<String>) realmAccess.get("roles");
                return roles != null ? roles : List.of();
            }
        } catch (Exception e) {
            log.debug("Could not extract realm_access.roles: {}", e.getMessage());
        }
        return List.of();
    }

    private String deriveActorType(JWTClaimsSet claims, List<String> roles) {
        try {
            String actorTypeClaim = claims.getStringClaim("actor_type");
            if (actorTypeClaim != null && !actorTypeClaim.isBlank()) {
                return actorTypeClaim.toUpperCase();
            }
        } catch (Exception e) {
            // Fall through to role-based derivation
        }

        // Derive from roles
        if (roles.contains("admin") || roles.contains("system-admin")) {
            return "ADMIN";
        }
        if (roles.contains("provider") || roles.contains("clinician") || roles.contains("doctor")) {
            return "PROVIDER";
        }
        if (roles.contains("citizen") || roles.contains("patient")) {
            return "CITIZEN";
        }
        if (roles.contains("service-account")) {
            return "SERVICE";
        }

        return "PROVIDER"; // Default for Keycloak-issued tokens
    }

    private UUID extractTenantId(JWTClaimsSet claims) {
        try {
            // Try custom tenant claim first
            String tenantClaim = claims.getStringClaim("tenant_id");
            if (tenantClaim != null && !tenantClaim.isBlank()) {
                return UUID.fromString(tenantClaim);
            }

            // Try organization claim
            String orgClaim = claims.getStringClaim("organization");
            if (orgClaim != null && !orgClaim.isBlank()) {
                return UUID.fromString(orgClaim);
            }
        } catch (Exception e) {
            log.debug("Could not extract tenant_id from token: {}", e.getMessage());
        }
        return null;
    }

    private int extractLoaLevel(JWTClaimsSet claims) {
        try {
            String acr = claims.getStringClaim("acr");
            if (acr == null) return 1;

            return switch (acr) {
                case "0" -> 0;
                case "urn:mace:incommon:iap:bronze", "1" -> 1;
                case "urn:mace:incommon:iap:silver", "2" -> 2;
                case "urn:mace:incommon:iap:gold", "3" -> 3;
                default -> 1;
            };
        } catch (Exception e) {
            return 1;
        }
    }
}
