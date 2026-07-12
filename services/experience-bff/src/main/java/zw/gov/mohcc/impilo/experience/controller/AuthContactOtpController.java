package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService.Contact;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService.VerifyOutcome;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService.VerifyResult;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contact verification + phone/email-OTP registration for the R1 "Reachable" rung
 * (gateway doctrine §4; roadmap W1 Workstream B; entitlements per PD-2).
 *
 * <p>All flows are app-layer: Keycloak realm files are untouched (KEYCLOAK-GATE).
 * Registration stays at the BFF door — the user is created server-side via the
 * Keycloak admin client with the CITIZEN role at LOA1, and the R1 rung is expressed
 * as a CONTACT_VERIFIED attestation in identity-assurance-service (the assurance SoR),
 * never as a parallel identity model.</p>
 *
 * <p>Deliberately absent: {@code POST /internal/v1/auth/login/otp}. Minting a session
 * for an <em>existing</em> user from OTP alone cannot be done with the current
 * server-side brokering (ROPC requires the password; Keycloak token-exchange /
 * impersonation would require realm + client configuration changes, violating
 * KEYCLOAK-GATE; the local fallback token is unsigned and rejected by every resource
 * server). Login remains password + existing flows.</p>
 */
@RestController
@RequestMapping("/internal/v1/auth/contact/otp")
public class AuthContactOtpController {

    private static final Logger log = LoggerFactory.getLogger(AuthContactOtpController.class);
    private static final String REFRESH_COOKIE_NAME = "exp_refresh_token";

    @Value("${KEYCLOAK_URL:http://localhost:8080}")
    private String keycloakUrl;
    @Value("${KEYCLOAK_REALM:impilo}")
    private String realm;
    @Value("${KEYCLOAK_CLIENT_ID:experience-ui}")
    private String clientId;
    @Value("${KEYCLOAK_TOKEN_SCOPE:openid profile email impilo-trust-headers}")
    private String keycloakTokenScope;
    @Value("${impilo.auth.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    private final ContactOtpService contactOtpService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final IdentityAssuranceServiceClient identityAssuranceClient;
    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final RestTemplate restTemplate;

    public AuthContactOtpController(ContactOtpService contactOtpService,
                                    KeycloakAdminClient keycloakAdminClient,
                                    IdentityAssuranceServiceClient identityAssuranceClient,
                                    ObjectProvider<JwtDecoder> jwtDecoderProvider,
                                    RestTemplate serviceRestTemplate) {
        this.contactOtpService = contactOtpService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.identityAssuranceClient = identityAssuranceClient;
        this.jwtDecoderProvider = jwtDecoderProvider;
        this.restTemplate = serviceRestTemplate;
    }

    /**
     * Issue a contact-verification OTP. Uniform response whether or not an account exists
     * for the contact (no enumeration); rate-limited per contact and per client IP;
     * fail-closed if delivery is not accepted.
     */
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestOtp(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest servletRequest) {

        String channel = str(body, "channel");
        String value = str(body, "value");
        if (value.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", "value is required");
        }

        try {
            Contact contact = contactOtpService.requestOtp(channel, value, clientIp(servletRequest));
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("status", "SENT");
            attributes.put("channel", contact.channel());
            attributes.put("maskedValue", contact.maskedValue());
            attributes.put("expiresInSeconds", ContactOtpService.OTP_TTL_SECONDS);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(envelope(
                    "contact-otp", attributes, requestId, correlationId));
        } catch (ContactOtpService.InvalidContactException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (ContactOtpService.OtpRateLimitedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                    .body(Map.of("error", Map.of(
                            "code", "RATE_LIMITED", "message", e.getMessage())));
        } catch (ContactOtpService.OtpUnavailableException e) {
            log.error("Contact OTP issue failed (fail-closed): {}", e.getMessage());
            return error(HttpStatus.SERVICE_UNAVAILABLE, "OTP_DELIVERY_UNAVAILABLE",
                    "Verification codes are temporarily unavailable. Please try again shortly.");
        }
    }

    /**
     * Verify an OTP. purpose=REGISTER additionally creates the CITIZEN Keycloak user
     * server-side (username = the verified contact) and records the CONTACT_VERIFIED
     * attestation; purpose=ATTACH records the attestation for the already-authenticated
     * caller only.
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyOtp(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody Map<String, Object> body) {

        String value = str(body, "value");
        String code = str(body, "code");
        String purpose = str(body, "purpose").toUpperCase();
        if (value.isBlank() || code.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", "value and code are required");
        }
        if (!"REGISTER".equals(purpose) && !"ATTACH".equals(purpose)) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", "purpose must be REGISTER or ATTACH");
        }

        VerifyResult result;
        try {
            result = contactOtpService.verifyOtp(value, code);
        } catch (ContactOtpService.InvalidContactException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (ContactOtpService.OtpUnavailableException e) {
            log.error("Contact OTP verify unavailable: {}", e.getMessage());
            return error(HttpStatus.SERVICE_UNAVAILABLE, "OTP_STORE_UNAVAILABLE",
                    "Verification is temporarily unavailable. Please try again shortly.");
        }

        switch (result.outcome()) {
            case EXPIRED_OR_UNKNOWN:
                return error(HttpStatus.BAD_REQUEST, "OTP_EXPIRED",
                        "The code has expired or was never issued. Request a new code.");
            case LOCKED:
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                        "error", Map.of("code", "OTP_LOCKED",
                                "message", "Too many incorrect attempts. Request a new code.")));
            case INVALID_CODE:
                return error(HttpStatus.UNAUTHORIZED, "OTP_INVALID", "Incorrect code.");
            case VERIFIED:
                break;
        }

        Contact contact = result.contact();
        return "REGISTER".equals(purpose)
                ? completeRegistration(contact, body, requestId, correlationId)
                : completeAttach(contact, authorization, requestId, correlationId);
    }

    // ── REGISTER ─────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> completeRegistration(
            Contact contact, Map<String, Object> body, String requestId, String correlationId) {

        String password = str(body, "password");
        if (password.length() < 12) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Password must be at least 12 characters and include upper, lower, digit, and special character");
        }
        String firstName = str(body, "firstName");
        String lastName = str(body, "lastName");
        String fullName = str(body, "fullName");
        if (firstName.isBlank() && !fullName.isBlank()) {
            String[] parts = fullName.split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : parts[0];
        }

        Map<String, List<String>> attributes = new LinkedHashMap<>();
        attributes.put("contactChannel", List.of(contact.channel()));
        attributes.put("contactVerified", List.of("true"));
        if ("PHONE".equals(contact.channel())) {
            attributes.put("phoneNumber", List.of(contact.normalizedValue()));
        }

        // Health OS Identity Doctrine: everyone registers as a person (CITIZEN), at LOA1.
        KeycloakAdminClient.KeycloakUserResult created = keycloakAdminClient.createContactUser(
                new KeycloakAdminClient.ContactUserCommand(
                        contact.normalizedValue(),
                        "EMAIL".equals(contact.channel()) ? contact.normalizedValue() : null,
                        firstName, lastName, password,
                        attributes, List.of("CITIZEN"),
                        "EMAIL".equals(contact.channel())));

        if (!created.available()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE",
                    "Registration is temporarily unavailable.");
        }
        if (!created.created()) {
            HttpStatus status = "USER_EXISTS".equals(created.code())
                    ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR;
            return error(status, created.code(), created.message());
        }

        // R1 truth: record the CONTACT_VERIFIED attestation in identity-assurance (SoR).
        // Without it the account is not "Reachable", so failure rolls the user back.
        if (!recordAttestation(created.userId(), contact, serviceAccountHeaders())) {
            keycloakAdminClient.deleteUser(created.userId());
            return error(HttpStatus.SERVICE_UNAVAILABLE, "ATTESTATION_UNAVAILABLE",
                    "Registration is temporarily unavailable. Please try again shortly.");
        }

        log.info("R1 contact registration complete: channel={} contact={} keycloakId={}",
                contact.channel(), contact.maskedValue(), created.userId());

        // Auto-login via the existing ROPC brokering (password grant with the password
        // chosen at registration; app-layer only, no realm change).
        ResponseEntity<Map<String, Object>> login = ropcLogin(
                contact, created.userId(), password, requestId, correlationId);
        if (login != null) {
            return login;
        }

        Map<String, Object> attributesOut = new LinkedHashMap<>();
        attributesOut.put("status", "REGISTERED");
        attributesOut.put("channel", contact.channel());
        attributesOut.put("maskedValue", contact.maskedValue());
        attributesOut.put("message", "Account created. Please sign in.");
        return ResponseEntity.status(HttpStatus.CREATED).body(envelope(
                "registration", attributesOut, requestId, correlationId));
    }

    private ResponseEntity<Map<String, Object>> ropcLogin(Contact contact, String userId,
                                                          String password,
                                                          String requestId, String correlationId) {
        try {
            String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "password");
            form.add("client_id", clientId);
            form.add("username", contact.normalizedValue());
            form.add("password", password);
            if (keycloakTokenScope != null && !keycloakTokenScope.isBlank()) {
                form.add("scope", keycloakTokenScope.trim());
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<JsonNode> tokenResponse = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, new HttpEntity<>(form, headers), JsonNode.class);
            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                return null;
            }
            JsonNode tokenData = tokenResponse.getBody();
            String accessToken = tokenData.path("access_token").asText(null);
            if (accessToken == null) {
                return null;
            }
            String refreshToken = tokenData.hasNonNull("refresh_token")
                    ? tokenData.get("refresh_token").asText() : null;
            int expiresIn = tokenData.path("expires_in").asInt(28800);
            int refreshExpiresIn = tokenData.path("refresh_expires_in").asInt(expiresIn * 6);

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", userId);
            user.put("healthId", userId);
            user.put("identifier", contact.normalizedValue());
            user.put("displayName", contact.maskedValue());
            user.put("roles", List.of("CITIZEN"));
            user.put("actorType", "CITIZEN");
            user.put("method", "PHONE".equals(contact.channel()) ? "phone-otp" : "email-otp");

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("token", accessToken);
            attributes.put("expiresAt", OffsetDateTime.now().plusSeconds(expiresIn).toString());
            attributes.put("expiresIn", expiresIn);
            attributes.put("user", user);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of("id", userId, "type", "auth_token", "attributes", attributes));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

            ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
            if (refreshToken != null && !refreshToken.isBlank()) {
                builder.header(HttpHeaders.SET_COOKIE, ResponseCookie
                        .from(REFRESH_COOKIE_NAME, refreshToken)
                        .httpOnly(true)
                        .secure(refreshCookieSecure)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(Duration.ofSeconds(Math.max(refreshExpiresIn, 0)))
                        .build().toString());
            }
            return builder.body(response);
        } catch (Exception e) {
            log.warn("Auto-login after contact registration failed: {}", e.getMessage());
            return null;
        }
    }

    // ── ATTACH ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> completeAttach(
            Contact contact, String authorization, String requestId, String correlationId) {

        if (authorization == null || !authorization.startsWith(CompanionHeaders.BEARER_PREFIX)) {
            return error(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED",
                    "ATTACH requires an authenticated session.");
        }
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        if (decoder == null) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE",
                    "Token validation is not configured.");
        }
        String subject;
        try {
            Jwt jwt = decoder.decode(authorization.substring(CompanionHeaders.BEARER_PREFIX.length()).trim());
            subject = jwt.getSubject();
        } catch (Exception e) {
            return error(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Invalid or expired session token.");
        }
        if (subject == null || subject.isBlank()) {
            return error(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Session token has no subject.");
        }

        // The trust-header interceptor forwards the caller's own Authorization/actor
        // headers to identity-assurance; no synthesized identity is needed here.
        if (!recordAttestation(subject, contact, new HttpHeaders())) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "ATTESTATION_UNAVAILABLE",
                    "Contact verification could not be recorded. Please try again shortly.");
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("status", "CONTACT_VERIFIED");
        attributes.put("channel", contact.channel());
        attributes.put("maskedValue", contact.maskedValue());
        return ResponseEntity.ok(envelope("contact-verification", attributes, requestId, correlationId));
    }

    // ── shared helpers ───────────────────────────────────────────────────────

    private boolean recordAttestation(String accountId, Contact contact, HttpHeaders headers) {
        try {
            identityAssuranceClient.recordContactVerified(
                    accountId, contact.channel(), contact.maskedValue(), headers);
            return true;
        } catch (Exception e) {
            log.error("CONTACT_VERIFIED attestation recording failed for {}: {}",
                    contact.maskedValue(), e.getMessage());
            return false;
        }
    }

    /**
     * Synthesized service identity for the pre-auth registration hop to identity-assurance
     * (the inbound request has no Authorization/actor yet — service-to-service v1.1 header
     * convention). The interceptor overrides these with inbound values when present.
     */
    private HttpHeaders serviceAccountHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String bearer = keycloakAdminClient.serviceAccountBearer();
        if (bearer != null) {
            headers.set(CompanionHeaders.AUTHORIZATION, CompanionHeaders.BEARER_PREFIX + bearer);
        }
        headers.set(CompanionHeaders.ACTOR_ID, "experience-bff");
        headers.set(CompanionHeaders.ACTOR_TYPE, "SYSTEM");
        headers.set(CompanionHeaders.PURPOSE_OF_USE, "IDENTITY_VERIFICATION");
        return headers;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : v.toString().trim();
    }

    private static Map<String, Object> envelope(String type, Map<String, Object> attributes,
                                                String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("type", type, "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message)));
    }
}
