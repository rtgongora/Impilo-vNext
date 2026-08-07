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
     * while {@code http://keycloak:8080/realms/impilo/…certs} returns 200.</p>
     *
     * <p><b>The failure wore three disguises, which is why it lasted.</b> {@link JWKSourceBuilder}
     * is lazy, so startup resolved nothing and logged {@code "Keycloak adapter initialized"} —
     * reassuringly. The fetch then failed on <em>every</em> validation instead, and the catch-all
     * flattened it to {@code TOKEN_VALIDATION_FAILED}, which is indistinguishable from a malformed
     * or expired token. And no bearer reaches this PDP on the browser path anyway — the very defect
     * the shadow measurement exists to size — so nothing was exercising it. Startup said fine,
     * runtime said bad token, and traffic said nothing at all.</p>
     *
     * <p><b>And the value was already there.</b> This estate has set
     * {@code SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://keycloak:8080/...} on
     * this very deployment all along — the operator applied the internal-JWKS pattern to six
     * services, with a comment saying so. It had no effect here because this adapter is not a
     * Spring resource server: it hand-rolls a Nimbus processor and derived its own URL from the
     * issuer, silently ignoring the property every other service honours. So the estate was
     * correctly configured and the code did not read the configuration.</p>
     *
     * <p>Hence the STANDARD property, not a new one. An {@code impilo.trust.keycloak.jwks-uri} was
     * briefly introduced here and removed: it would have been a second name for a setting the
     * estate already expresses, and the next person to configure this service correctly would have
     * been ignored for a second time.</p>
     *
     * <p>It means the gap is worse than "489 of 525 rules cannot match a signed-in human": a bearer
     * that did arrive could not be validated either. See {@link #verifyJwksReachable(String)} for
     * the startup probe that removes the first disguise and the {@code JWKS_UNREACHABLE} branch
     * that removes the second.</p>
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String configuredJwksUri;

    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    /** The URL keys are actually fetched from, kept for error messages that must name it. */
    private String resolvedJwksUri;
    private volatile boolean jwksReachable;
    private final java.util.concurrent.atomic.AtomicLong jwksFailuresLogged =
            new java.util.concurrent.atomic.AtomicLong();

    @PostConstruct
    public void init() {
        String jwksUri = (configuredJwksUri == null || configuredJwksUri.isBlank())
                ? issuerUri + "/protocol/openid-connect/certs"
                : configuredJwksUri.trim();
        this.resolvedJwksUri = jwksUri;
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
            verifyJwksReachable(jwksUri);
        } catch (Exception e) {
            // Reached only for a malformed URL — the key source resolves nothing here. A network
            // failure does NOT land in this branch, which is precisely why verifyJwksReachable
            // exists below. Deliberately ERROR: this leaves the adapter unable to validate ANY
            // token, and it previously logged at WARN, without the URL, calling itself "deferred".
            log.error("Keycloak adapter DISABLED — JWKS URL unusable: {} ({}). EVERY bearer "
                            + "presented to this PDP will be rejected. Set spring.security.oauth2"
                            + ".resourceserver.jwt.jwk-set-uri to a cluster-reachable endpoint "
                            + "when the issuer is a public URL.",
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
        } catch (com.nimbusds.jose.RemoteKeySourceException e) {
            // NOT a token problem, and it must never be reported as one. The key source is fetched
            // LAZILY on first use, so an unreachable JWKS endpoint produces a per-request failure
            // that the catch-all below flattened into TOKEN_VALIDATION_FAILED — indistinguishable
            // from a malformed or expired token, for every token, forever.
            jwksReachable = false;
            if (jwksFailuresLogged.getAndIncrement() % 100 == 0) {
                log.error("JWKS UNREACHABLE at {} — this is NOT a bad token. Every bearer presented "
                                + "to this PDP is being rejected. ({} such failures so far.)",
                        resolvedJwksUri, jwksFailuresLogged.get(), e);
            }
            throw new SessionValidationException("JWKS_UNREACHABLE",
                    "Signing keys could not be fetched; the token was never actually checked", e);
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

    /**
     * Fetch the JWKS once, at startup, so a misconfiguration is loud immediately.
     *
     * <h3>Why this is not optional</h3>
     * <p>{@link JWKSourceBuilder} is <b>lazy</b>: it resolves nothing at construction. Before this
     * existed, an estate pointed at an unreachable JWKS endpoint logged
     * {@code "Keycloak adapter initialized"} — reassuringly — and then failed every single token
     * validation, with the cause flattened into {@code TOKEN_VALIDATION_FAILED}. Measured
     * 2026-08-07: the PDP could not validate ANY bearer and said so nowhere. A misconfiguration
     * that presents as "no user has any roles" is exactly the failure that hides for months.</p>
     *
     * <h3>Why it warns rather than refuses to start</h3>
     * <p>Crashing would be worse than the bug. This PDP is on the request path; a cold or restarting
     * Keycloak would turn a temporary key-server blip into a total authorization outage. So the
     * condition is made impossible to miss and left recoverable — the key source retries on its own,
     * and {@link #jwksReachable} flips back the first time a validation succeeds.</p>
     */
    private void verifyJwksReachable(String jwksUri) {
        try {
            java.net.HttpURLConnection connection =
                    (java.net.HttpURLConnection) new URL(jwksUri).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            connection.disconnect();
            jwksReachable = status >= 200 && status < 300;
            if (jwksReachable) {
                log.info("JWKS endpoint verified reachable at {} ({})", jwksUri, status);
            } else {
                log.error("JWKS endpoint at {} answered {} — EVERY bearer presented to this PDP "
                        + "will be rejected until this is fixed.", jwksUri, status);
            }
        } catch (Exception e) {
            jwksReachable = false;
            log.error("JWKS endpoint at {} is UNREACHABLE ({}). EVERY bearer presented to this PDP "
                            + "will be rejected, and it will look like a token problem rather than "
                            + "a connectivity one. If the issuer is a public URL the pods cannot "
                            + "reach, set spring.security.oauth2.resourceserver.jwt.jwk-set-uri "
                            + "to the in-cluster endpoint.",
                    jwksUri, e.toString());
        }
    }

    /** Whether signing keys could last be fetched. False means no bearer can be validated at all. */
    public boolean isJwksReachable() { return jwksReachable; }
}
