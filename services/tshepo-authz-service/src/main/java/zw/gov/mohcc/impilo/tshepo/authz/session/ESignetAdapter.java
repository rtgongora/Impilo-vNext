package zw.gov.mohcc.impilo.tshepo.authz.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;

import java.util.*;

/**
 * eSignet OIDC session validation adapter.
 *
 * <p>Validates opaque/JWT tokens issued by eSignet (the national identity
 * gateway) via the introspection endpoint. Used for citizen authentication
 * (patient portal, mHealth). Only active when {@code tshepo.authz.esignet.enabled=true}.</p>
 *
 * <p>eSignet tokens carry a Level of Assurance (LoA) based on the citizen's
 * identity verification level (e.g., biometric verification = LoA 3).</p>
 */
@Component
public class ESignetAdapter implements SessionAssurance {

    private static final Logger log = LoggerFactory.getLogger(ESignetAdapter.class);

    private final AuthzProperties properties;
    private final RestTemplate restTemplate;

    public ESignetAdapter(AuthzProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean canHandle(String token) {
        if (!properties.getEsignet().isEnabled()) {
            return false;
        }

        try {
            // Try to decode as JWT and check issuer
            com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(token);
            String tokenIssuer = signedJWT.getJWTClaimsSet().getIssuer();
            return tokenIssuer != null &&
                   tokenIssuer.startsWith(properties.getEsignet().getIssuerUri());
        } catch (Exception e) {
            // Might be an opaque token — try introspection
            return properties.getEsignet().isEnabled();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public SessionInfo validateSession(String token) throws SessionValidationException {
        if (!properties.getEsignet().isEnabled()) {
            throw new SessionValidationException("ESIGNET_DISABLED",
                    "eSignet integration is not enabled");
        }

        try {
            // Call eSignet introspection endpoint
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(
                    properties.getEsignet().getClientId(),
                    properties.getEsignet().getClientSecret()
            );

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getEsignet().getIntrospectionUri(),
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map<String, Object> introspection = response.getBody();
            if (introspection == null || !Boolean.TRUE.equals(introspection.get("active"))) {
                throw new SessionValidationException("TOKEN_INACTIVE",
                        "eSignet token is not active");
            }

            // Extract fields from introspection response
            String actorId = (String) introspection.getOrDefault("sub", "");
            if (actorId.isBlank()) {
                throw new SessionValidationException("INVALID_TOKEN",
                        "eSignet token missing sub claim");
            }

            // Citizens from eSignet are always CITIZEN type
            String actorType = "CITIZEN";

            // Extract roles (may be empty for citizen tokens)
            List<String> roles = new ArrayList<>();
            Object scopeObj = introspection.get("scope");
            if (scopeObj instanceof String scopeStr) {
                roles.addAll(Arrays.asList(scopeStr.split("\\s+")));
            }

            // Extract tenant (may come from audience or custom claim)
            UUID tenantId = null;
            Object tenantObj = introspection.get("tenant_id");
            if (tenantObj instanceof String tenantStr && !tenantStr.isBlank()) {
                try {
                    tenantId = UUID.fromString(tenantStr);
                } catch (IllegalArgumentException ignored) {
                    // Non-UUID tenant — skip
                }
            }

            // Extract LoA from acr
            int loaLevel = 1;
            Object acrObj = introspection.get("acr");
            if (acrObj instanceof String acrStr) {
                loaLevel = parseLoaFromAcr(acrStr);
            }

            // Session ID
            String sessionId = (String) introspection.get("sid");

            String issuer = (String) introspection.getOrDefault("iss",
                    properties.getEsignet().getIssuerUri());

            return new SessionInfo(actorId, actorType, roles, tenantId, loaLevel,
                    sessionId, issuer);

        } catch (SessionValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("eSignet token introspection failed: {}", e.getMessage(), e);
            throw new SessionValidationException("INTROSPECTION_FAILED",
                    "eSignet introspection failed: " + e.getMessage(), e);
        }
    }

    private int parseLoaFromAcr(String acr) {
        return switch (acr) {
            case "mosip:idp:acr:generated-code" -> 1;
            case "mosip:idp:acr:biometrics" -> 3;
            case "mosip:idp:acr:static-code" -> 2;
            case "0" -> 0;
            case "1" -> 1;
            case "2" -> 2;
            case "3" -> 3;
            default -> 1;
        };
    }
}
