package zw.gov.mohcc.impilo.experience.bootstrap;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class BootstrapTokenService {

    private final BootstrapProperties properties;

    public BootstrapTokenService(BootstrapProperties properties) {
        this.properties = properties;
    }

    public TokenValidationResult validate(String method, String token, String authorisationReference) {
        if (!properties.isEnabled()) {
            return TokenValidationResult.denied("Bootstrap is disabled in this environment.");
        }
        if (!properties.getAllowedMethods().contains(normalize(method))) {
            return TokenValidationResult.denied("Bootstrap method not allowed in this environment.");
        }
        if ("signed_authorisation".equals(normalize(method))) {
            if (authorisationReference == null || authorisationReference.isBlank()) {
                return TokenValidationResult.denied("Signed authorisation reference is required.");
            }
            return TokenValidationResult.valid(fingerprint(authorisationReference));
        }
        if (token == null || token.isBlank()) {
            return TokenValidationResult.denied("Bootstrap token is required.");
        }
        if (properties.getTokenExpiresAt() != null && !properties.getTokenExpiresAt().isBlank()) {
            OffsetDateTime expires = OffsetDateTime.parse(properties.getTokenExpiresAt());
            if (OffsetDateTime.now().isAfter(expires)) {
                return TokenValidationResult.denied("Bootstrap token has expired.");
            }
        }
        String configuredHash = properties.getTokenHash();
        if (configuredHash == null || configuredHash.isBlank()) {
            if ("development".equalsIgnoreCase(properties.getEnvironment())
                    || "test".equalsIgnoreCase(properties.getEnvironment())
                    || "preview".equalsIgnoreCase(properties.getEnvironment())) {
                return TokenValidationResult.valid(fingerprint(token));
            }
            return TokenValidationResult.denied("Bootstrap token is not configured.");
        }
        if (!configuredHash.equalsIgnoreCase(sha256(token))) {
            return TokenValidationResult.denied("Bootstrap token is invalid.");
        }
        return TokenValidationResult.valid(fingerprint(token));
    }

    public String fingerprint(String value) {
        return sha256(value);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private static String normalize(String method) {
        return method == null ? "" : method.trim().toLowerCase(Locale.ROOT);
    }

    public record TokenValidationResult(boolean valid, String message, String fingerprint) {
        static TokenValidationResult valid(String fingerprint) {
            return new TokenValidationResult(true, "Token validated.", fingerprint);
        }
        static TokenValidationResult denied(String message) {
            return new TokenValidationResult(false, message, null);
        }
    }
}
