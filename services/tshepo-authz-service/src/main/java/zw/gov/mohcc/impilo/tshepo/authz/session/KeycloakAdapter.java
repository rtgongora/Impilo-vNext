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
import java.time.Instant;
import java.util.*;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter.LegacyAuthenticationAssuranceAdapter;

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

    /**
     * Where to FETCH signing keys, when that is not where the issuer claim points. Blank — the
     * default — derives it from {@link #issuerUri}, which is correct wherever the two coincide and
     * changes nothing for those estates.
     *
     * <p><b>Why it has to be separable.</b> {@code issuerUri} was doing two incompatible jobs: it
     * must equal the token's {@code iss} claim, which Keycloak mints as the PUBLIC realm URL, and
     * it was also used to fetch JWKS, which has to be reachable from inside the cluster. Measured
     * 2026-08-07 in preview — from a pod, {@code https://impilo.mohcc.gov.zw/realms/impilo/…certs}
     * returns {@code 000} (the public host is served by an ingress the pods do not hairpin to),
     * while {@code http://keycloak:8080/realms/impilo/…certs} returns 200. JWKS initialisation
     * therefore failed, {@code jwtProcessor} stayed null, and every bearer was rejected.</p>
     *
     * <p>It was invisible because no bearer reaches this PDP on the browser path at all — the very
     * defect the shadow measurement exists to size. It means the gap is worse than "489 of 525
     * rules cannot match a signed-in human": until this is set, a bearer that did arrive could not
     * be validated either.</p>
     */
    @Value("${impilo.trust.keycloak.jwks-uri:}")
    private String configuredJwksUri;

    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    @PostConstruct
    public void init() {
        String jwksUri = (configuredJwksUri == null || configuredJwksUri.isBlank())
                ? issuerUri + "/protocol/openid-connect/certs"
                : configuredJwksUri.trim();
        try {
            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                    .create(new URL(jwksUri))
                    .retrying(true)
                    .build();

            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);

            jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(keySelector);

            // Both are logged. Where they differ that is a deliberate split-horizon deployment and
            // the reader needs to see it; when a bearer is later rejected, the first question is
            // always which endpoint the keys came from.
            log.info("Keycloak adapter initialized — issuer(claim)={} jwks(fetch)={}",
                    issuerUri, jwksUri);
        } catch (Exception e) {
            // Deliberately ERROR. This leaves the adapter unable to validate ANY token. It
            // previously logged at WARN, without the URL, calling it "deferred" — so an estate
            // where every bearer was rejected read, in the logs, exactly like an estate where no
            // bearer ever arrived. That is how this survived unnoticed.
            log.error("Keycloak adapter DISABLED — JWKS unreachable at {} ({}). EVERY bearer "
                            + "presented to this PDP will be rejected. Set impilo.trust.keycloak."
                            + "jwks-uri to a cluster-reachable endpoint when the issuer is public.",
                    jwksUri, e.toString());
            jwtProcessor = null;
        }
    }

    @Override
    public boolean canHandle(String token) {
        try {
            // Decode without verification to check issuer
            com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(token);
            String tokenIssuer = signedJWT.getJWTClaimsSet().getIssuer();
            return tokenIssuer != null && tokenIssuer.equals(issuerUri);
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

            if (!issuerUri.equals(claims.getIssuer())) {
                throw new SessionValidationException("ISSUER_MISMATCH", "JWT issuer is not the configured Keycloak realm");
            }

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

            // Extract session ID
            String sessionId = claims.getStringClaim("sid");
            AuthenticationAssurance authenticationAssurance = extractAuthenticationAssurance(claims, sessionId);

            // Keycloak ACR is authentication assurance, never identity proofing. Identity
            // LoA remains zero here and is resolved independently from the identity plane.
            return new SessionInfo(actorId, actorType, roles, tenantId, 0,
                    sessionId, issuerUri, authenticationAssurance);

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

    private AuthenticationAssurance extractAuthenticationAssurance(JWTClaimsSet claims, String sessionId) {
        try {
            String acr = claims.getStringClaim("acr");
            int aal = switch (acr == null ? "" : acr) {
                case "0" -> 0;
                case "urn:mace:incommon:iap:bronze", "1", "urn:impilo:aal1" -> 1;
                case "urn:mace:incommon:iap:silver", "2", "urn:impilo:aal2" -> 2;
                case "urn:mace:incommon:iap:gold", "3", "urn:impilo:aal3" -> 3;
                default -> 1;
            };
            List<String> methods = Optional.ofNullable(claims.getStringListClaim("amr")).orElse(List.of());
            Instant authTime = claimInstant(claims.getClaim("auth_time"));
            Instant stepUpTime = claimInstant(claims.getClaim("impilo_step_up_at"));
            boolean phishingResistant = methods.stream().anyMatch(method ->
                    method.equalsIgnoreCase("webauthn") || method.equalsIgnoreCase("hwk"));
            AuthenticationAssurance raw = new AuthenticationAssurance(aal, methods, authTime, stepUpTime,
                    phishingResistant, sessionId, claims.getStringClaim("impilo_flow_id"));
            // Constrained recovery: a recovery-code login must not surface as ordinary workforce
            // AAL2. The v1 round-trip classifies recovery from AMR and demotes numeric AAL to at
            // most 1 while keeping the recovery marker visible to policy and downstream headers.
            return LegacyAuthenticationAssuranceAdapter.toLegacy(
                    LegacyAuthenticationAssuranceAdapter.toCanonical(raw));
        } catch (Exception e) {
            log.warn("Authentication-assurance claims could not be classified: {}", e.getMessage());
            return AuthenticationAssurance.none();
        }
    }

    private static Instant claimInstant(Object value) {
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof Number number) return Instant.ofEpochSecond(number.longValue());
        if (value instanceof String text) {
            try { return Instant.ofEpochSecond(Long.parseLong(text)); }
            catch (NumberFormatException ignored) {
                try { return Instant.parse(text); } catch (Exception ignoredAgain) { return null; }
            }
        }
        return null;
    }
}
