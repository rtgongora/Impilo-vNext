package zw.gov.mohcc.impilo.vito.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

import java.util.Locale;

/**
 * Deterministic HMAC lookup keys for private contact matching (Identity Journey
 * Doctrine §2). Normalises phone/email consistently so the value written at
 * registration and the value presented at resolve produce the same hash. Uses
 * the shared {@link HmacService} (pepper-keyed HMAC-SHA256, hex) — the same
 * mechanism as the Impilo-ID alias vault, so contacts never live in plaintext.
 *
 * <p>The hash is a one-way lookup key: it identifies a candidate row, never
 * reveals the contact, and is only ever compared server-side.</p>
 */
@Component
public class ContactHasher {

    private final HmacService hmacService;

    public ContactHasher(HmacService hmacService) {
        this.hmacService = hmacService;
    }

    /** Hash a phone number (digits only, leading + preserved). Null/blank → null. */
    public String hashPhone(String phone) {
        String n = normalisePhone(phone);
        return n == null ? null : hmacService.computeLookupHash(n);
    }

    /** Hash an email (trimmed, lowercased). Null/blank → null. */
    public String hashEmail(String email) {
        String n = normaliseEmail(email);
        return n == null ? null : hmacService.computeLookupHash(n);
    }

    /** Hash by contact kind ("phone" | "email"); unknown/blank → null. */
    public String hash(String kind, String value) {
        if (kind == null) {
            return null;
        }
        return switch (kind.trim().toLowerCase(Locale.ROOT)) {
            case "phone" -> hashPhone(value);
            case "email" -> hashEmail(value);
            default -> null;
        };
    }

    private static String normalisePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        boolean plus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        return plus ? "+" + digits : digits;
    }

    private static String normaliseEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
