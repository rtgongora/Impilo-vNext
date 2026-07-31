package zw.gov.mohcc.impilo.experience.auth.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Encrypted Redis store for browser OIDC transactions and sessions. */
@Service
public class WebAuthSessionStore {
    public static final String SESSION_COOKIE = "__Host-impilo_session";
    public static final String CSRF_COOKIE = "__Host-impilo_csrf";
    private static final String SESSION_PREFIX = "experience:auth:session:";
    private static final String TRANSACTION_PREFIX = "experience:auth:transaction:";
    private static final byte[] AAD = "impilo-web-auth-v1".getBytes(StandardCharsets.UTF_8);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final WebAuthSessionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey encryptionKey;

    public WebAuthSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper,
                               WebAuthSessionProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    void validateConfiguration() {
        if (!properties.isEnabled()) return;
        if (properties.getClientSecret() == null || properties.getClientSecret().isBlank()) {
            throw new IllegalStateException("OIDC web session client secret is required");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.getEncryptionKey());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("web session encryption key must be base64", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("web session encryption key must decode to exactly 32 bytes");
        }
        encryptionKey = new SecretKeySpec(decoded, "AES");
    }

    public String newOpaqueValue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void saveTransaction(AuthTransaction transaction) {
        requireEnabled();
        redis.opsForValue().set(TRANSACTION_PREFIX + transaction.state(), encrypt(transaction),
                properties.getTransactionTtlSeconds(), TimeUnit.SECONDS);
    }

    public Optional<AuthTransaction> consumeTransaction(String state) {
        requireEnabled();
        if (state == null || state.isBlank()) return Optional.empty();
        String encrypted = redis.opsForValue().getAndDelete(TRANSACTION_PREFIX + state);
        return encrypted == null ? Optional.empty() : Optional.of(decrypt(encrypted, AuthTransaction.class));
    }

    public void saveSession(String sessionId, SessionData session) {
        requireEnabled();
        redis.opsForValue().set(SESSION_PREFIX + sessionId, encrypt(session),
                properties.getSessionTtlSeconds(), TimeUnit.SECONDS);
    }

    public Optional<SessionData> findSession(String sessionId) {
        if (!properties.isEnabled() || sessionId == null || sessionId.isBlank()) return Optional.empty();
        String encrypted = redis.opsForValue().get(SESSION_PREFIX + sessionId);
        return encrypted == null ? Optional.empty() : Optional.of(decrypt(encrypted, SessionData.class));
    }

    public void deleteSession(String sessionId) {
        if (properties.isEnabled() && sessionId != null && !sessionId.isBlank()) {
            redis.delete(SESSION_PREFIX + sessionId);
        }
    }

    public boolean csrfMatches(String sessionId, String candidate) {
        Optional<SessionData> session = findSession(sessionId);
        if (session.isEmpty() || candidate == null) return false;
        return MessageDigest.isEqual(session.get().csrfToken().getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    private String encrypt(Object value) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(objectMapper.writeValueAsBytes(value));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) {
            throw new IllegalStateException("failed to encrypt browser auth state", e);
        }
    }

    private <T> T decrypt(String encoded, Class<T> type) {
        try {
            byte[] combined = Base64.getUrlDecoder().decode(encoded);
            if (combined.length < 29) throw new GeneralSecurityException("ciphertext too short");
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            return objectMapper.readValue(cipher.doFinal(encrypted), type);
        } catch (Exception e) {
            throw new IllegalStateException("invalid encrypted browser auth state", e);
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new IllegalStateException("web OIDC sessions are disabled");
    }

    public record AuthTransaction(String state, String nonce, String codeVerifier, String returnTo,
                                  String requestedAcr, String requiredAction,
                                  String previousSessionId, Instant createdAt) {}

    public record SessionData(String subject, String accessToken, String refreshToken, String idToken,
                              Instant accessTokenExpiresAt, String csrfToken, String acr,
                              List<String> amr, Instant authTime, Instant stepUpTime,
                              String flowId, Map<String, Object> profile) {}
}
