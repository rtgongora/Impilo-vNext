package zw.gov.mohcc.impilo.participation.core;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Generates human-readable, collision-checked references for Participation aggregates,
 * e.g. {@code IDEA-2026-4F9A2C}. The random suffix keeps concurrent writers from colliding;
 * the {@code exists} predicate is the final guard against the rare clash. Mirrors the Rito
 * ReferenceGenerator primitive.
 */
@Component
public class ReferenceGenerator {

    public String generate(String prefix, Predicate<String> exists) {
        int year = OffsetDateTime.now().getYear();
        for (int attempt = 0; attempt < 25; attempt++) {
            String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            String candidate = prefix + "-" + year + "-" + suffix;
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        return prefix + "-" + year + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
