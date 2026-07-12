package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Contact-channel OTP lifecycle for the R1 "Reachable" rung (gateway doctrine §4):
 * issues a one-time code to a phone/email and verifies possession of that channel.
 *
 * <p>Mirrors tshepo-authz {@code StepUpService} SMS_OTP conventions rather than inventing
 * new ones: server-generated 6-digit code, SHA-256 hashed at rest, constant-time compare,
 * 300s expiry, 5-attempt lockout, and <strong>fail-closed issue</strong> — if the
 * notification service does not accept the delivery, the challenge is destroyed and never
 * issued (no unverifiable/undeliverable challenges).</p>

 * <p>Challenge state lives in Redis (the BFF is stateless and has no sovereign datasource;
 * an OTP challenge is transient session-adjacent state with a 300s TTL — the durable truth,
 * the CONTACT_VERIFIED attestation, is owned by identity-assurance-service). Redis being
 * unavailable therefore fails closed with 503 semantics: a code that cannot be stored can
 * never be verified, so it is never sent.</p>
 */
@Service
public class ContactOtpService {

    private static final Logger log = LoggerFactory.getLogger(ContactOtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final int OTP_TTL_SECONDS = 300;
    public static final int MAX_ATTEMPTS = 5;
    public static final int RESEND_COOLDOWN_SECONDS = 60;
    public static final int RATE_WINDOW_SECONDS = 900;
    public static final int MAX_REQUESTS_PER_VALUE = 5;
    public static final int MAX_REQUESTS_PER_IP = 15;

    private static final String CHALLENGE_PREFIX = "auth:contact-otp:ch:";
    private static final String COOLDOWN_PREFIX = "auth:contact-otp:cd:";
    private static final String VALUE_RATE_PREFIX = "auth:contact-otp:rl:v:";
    private static final String IP_RATE_PREFIX = "auth:contact-otp:rl:ip:";

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final StringRedisTemplate redis;
    private final NotificationServiceClient notificationClient;
    private final KeycloakAdminClient keycloakAdminClient;
    private final ObjectMapper objectMapper;

    /** Default country calling code applied to national-format (0-leading) numbers. */
    private final String defaultCountryCode;

    public ContactOtpService(StringRedisTemplate redis,
                             NotificationServiceClient notificationClient,
                             KeycloakAdminClient keycloakAdminClient,
                             ObjectMapper objectMapper,
                             @Value("${impilo.auth.contact-otp.default-country-code:+263}")
                             String defaultCountryCode) {
        this.redis = redis;
        this.notificationClient = notificationClient;
        this.keycloakAdminClient = keycloakAdminClient;
        this.objectMapper = objectMapper;
        this.defaultCountryCode = defaultCountryCode;
    }

    // ── contract types ────────────────────────────────────────────────────────

    /** A normalized contact: canonical value + channel + display mask. */
    public record Contact(String channel, String normalizedValue, String maskedValue) {}

    public enum VerifyOutcome { VERIFIED, INVALID_CODE, EXPIRED_OR_UNKNOWN, LOCKED }

    public record VerifyResult(VerifyOutcome outcome, Contact contact) {}

    /** Client error: malformed contact value / unsupported channel. */
    public static class InvalidContactException extends RuntimeException {
        public InvalidContactException(String message) { super(message); }
    }

    /** Too many requests for this contact/IP — carries a retry-after hint. */
    public static class OtpRateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;
        public OtpRateLimitedException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }

    /** Fail-closed: challenge could not be stored or delivery was not accepted. */
    public static class OtpUnavailableException extends RuntimeException {
        public OtpUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    // ── normalization / masking ───────────────────────────────────────────────

    /**
     * Normalize a raw contact value; channel is inferred (contains '@' → EMAIL, else PHONE)
     * and validated against the explicit channel when one is supplied.
     */
    public Contact normalize(String channel, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new InvalidContactException("Contact value is required");
        }
        String trimmed = rawValue.trim();
        String inferredChannel = trimmed.contains("@") ? "EMAIL" : "PHONE";
        if (channel != null && !channel.isBlank()
                && !inferredChannel.equalsIgnoreCase(channel.trim())) {
            throw new InvalidContactException("Value does not match channel " + channel.trim());
        }

        if ("EMAIL".equals(inferredChannel)) {
            String email = trimmed.toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(email).matches()) {
                throw new InvalidContactException("Invalid email address");
            }
            return new Contact("EMAIL", email, maskEmail(email));
        }

        String digits = trimmed.replaceAll("[\\s().-]", "");
        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
        }
        if (digits.startsWith("0") && digits.length() >= 9) {
            digits = defaultCountryCode + digits.substring(1);
        } else if (!digits.startsWith("+") && digits.matches("[0-9]+")) {
            digits = "+" + digits;
        }
        if (!E164.matcher(digits).matches()) {
            throw new InvalidContactException("Invalid phone number");
        }
        return new Contact("PHONE", digits, maskPhone(digits));
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }

    static String maskPhone(String phone) {
        return phone.substring(0, 4) + "*".repeat(Math.max(1, phone.length() - 6))
                + phone.substring(phone.length() - 2);
    }

    // ── issue ─────────────────────────────────────────────────────────────────

    /**
     * Issue an OTP to the contact. Response is uniform whether or not an account exists for
     * the contact (no enumeration). Fail-closed: the challenge is only left in place if the
     * notification service accepted the delivery.
     */
    public Contact requestOtp(String channel, String rawValue, String clientIp) {
        Contact contact = normalize(channel, rawValue);
        String contactHash = sha256Hex(contact.normalizedValue());
        String challengeKey = CHALLENGE_PREFIX + contactHash;
        String cooldownKey = COOLDOWN_PREFIX + contactHash;

        try {
            // Resend cooldown, then per-value and per-IP fixed windows.
            if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
                Long ttl = redis.getExpire(cooldownKey);
                throw new OtpRateLimitedException("A code was sent recently. Please wait before retrying.",
                        ttl != null && ttl > 0 ? ttl : RESEND_COOLDOWN_SECONDS);
            }
            enforceWindow(VALUE_RATE_PREFIX + contactHash, MAX_REQUESTS_PER_VALUE,
                    "Too many codes requested for this contact.");
            if (clientIp != null && !clientIp.isBlank()) {
                enforceWindow(IP_RATE_PREFIX + sha256Hex(clientIp), MAX_REQUESTS_PER_IP,
                        "Too many verification requests.");
            }

            String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
            ObjectNode challenge = objectMapper.createObjectNode();
            challenge.put("codeHash", sha256Hex(otp));
            challenge.put("attempts", 0);
            challenge.put("channel", contact.channel());
            challenge.put("maskedValue", contact.maskedValue());
            challenge.put("issuedAt", Instant.now().toString());
            redis.opsForValue().set(challengeKey, challenge.toString(),
                    Duration.ofSeconds(OTP_TTL_SECONDS));

            try {
                deliver(contact, otp);
            } catch (Exception e) {
                // Fail closed — never leave an undeliverable challenge behind.
                redis.delete(challengeKey);
                throw new OtpUnavailableException(
                        "OTP delivery was not accepted by the notification service", e);
            }

            redis.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(RESEND_COOLDOWN_SECONDS));
            log.info("Contact OTP issued: channel={} contact={}", contact.channel(), contact.maskedValue());
            return contact;
        } catch (InvalidContactException | OtpRateLimitedException | OtpUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // Redis unavailable etc. — a code that cannot be stored can never be verified.
            throw new OtpUnavailableException("Contact OTP store unavailable", e);
        }
    }

    private void enforceWindow(String key, int max, String message) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofSeconds(RATE_WINDOW_SECONDS));
        }
        if (count != null && count > max) {
            Long ttl = redis.getExpire(key);
            throw new OtpRateLimitedException(message,
                    ttl != null && ttl > 0 ? ttl : RATE_WINDOW_SECONDS);
        }
    }

    private void deliver(Contact contact, String otp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateKey", "PHONE".equals(contact.channel())
                ? "CONTACT_VERIFY_OTP" : "CONTACT_VERIFY_OTP_EMAIL");
        body.put("channel", "PHONE".equals(contact.channel()) ? "SMS" : "EMAIL");
        body.put("to", contact.normalizedValue());
        body.put("messageKind", "SENSITIVE");
        body.put("variables", Map.of("otp", otp));
        notificationClient.sendNotification(body, serviceOriginHeaders());
    }

    /**
     * Service identity for the OTP delivery hop (service-to-service v1.1 convention).
     * The OTP request is pre-auth by definition — an anonymous R0 visitor proving a
     * contact channel — so the inbound request carries no Authorization to forward,
     * while notification-service's {@code /internal/v1/notify} is fail-closed
     * JWT-authenticated. Without this bearer the whole R1 lane dies with 503
     * OTP_DELIVERY_UNAVAILABLE (rig-caught in the W1 runtime proof). Mirrors
     * {@code AuthContactOtpController#serviceAccountHeaders()}; when the caller IS
     * authenticated, the shared interceptor overwrites these with the inbound values.
     */
    private HttpHeaders serviceOriginHeaders() {
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

    // ── verify ────────────────────────────────────────────────────────────────

    /**
     * Verify a submitted code. Single-use on success; attempts are counted and capped at
     * {@value #MAX_ATTEMPTS} (the challenge is destroyed on lockout); replays after success
     * or expiry surface as EXPIRED_OR_UNKNOWN. Never reveals whether an account exists.
     */
    public VerifyResult verifyOtp(String rawValue, String code) {
        Contact contact = normalize(null, rawValue);
        String challengeKey = CHALLENGE_PREFIX + sha256Hex(contact.normalizedValue());

        try {
            String stored = redis.opsForValue().get(challengeKey);
            if (stored == null) {
                return new VerifyResult(VerifyOutcome.EXPIRED_OR_UNKNOWN, contact);
            }
            ObjectNode challenge = (ObjectNode) objectMapper.readTree(stored);
            int attempts = challenge.path("attempts").asInt(0) + 1;
            if (attempts > MAX_ATTEMPTS) {
                redis.delete(challengeKey);
                return new VerifyResult(VerifyOutcome.LOCKED, contact);
            }

            Contact issued = new Contact(
                    challenge.path("channel").asText(contact.channel()),
                    contact.normalizedValue(),
                    challenge.path("maskedValue").asText(contact.maskedValue()));

            boolean matches = code != null && !code.isBlank()
                    && constantTimeEquals(challenge.path("codeHash").asText(""), sha256Hex(code.trim()));
            if (matches) {
                redis.delete(challengeKey); // single use
                log.info("Contact OTP verified: channel={} contact={}", issued.channel(), issued.maskedValue());
                return new VerifyResult(VerifyOutcome.VERIFIED, issued);
            }

            // Persist the attempt count without extending the expiry window.
            challenge.put("attempts", attempts);
            Long ttl = redis.getExpire(challengeKey);
            if (ttl != null && ttl > 0) {
                redis.opsForValue().set(challengeKey, challenge.toString(), Duration.ofSeconds(ttl));
            }
            if (attempts >= MAX_ATTEMPTS) {
                redis.delete(challengeKey);
                return new VerifyResult(VerifyOutcome.LOCKED, issued);
            }
            return new VerifyResult(VerifyOutcome.INVALID_CODE, issued);
        } catch (InvalidContactException e) {
            throw e;
        } catch (Exception e) {
            throw new OtpUnavailableException("Contact OTP store unavailable", e);
        }
    }

    // ── crypto helpers (mirrors tshepo-authz StepUpVerifier) ─────────────────

    static String sha256Hex(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Test seam. */
    JsonNode readChallenge(String normalizedValue) {
        String stored = redis.opsForValue().get(CHALLENGE_PREFIX + sha256Hex(normalizedValue));
        try {
            return stored == null ? null : objectMapper.readTree(stored);
        } catch (Exception e) {
            return null;
        }
    }
}
