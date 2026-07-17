package zw.gov.mohcc.impilo.participation.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two security-relevant primitives of the anonymous public intake lane: the one-time
 * claim code (which is the only key to a submission's status) and the SHA-256 hash that is the
 * ONLY thing persisted for it. If either regresses, anonymous status lookup silently breaks or
 * — worse — the plaintext code becomes guessable/stored.
 */
class ClaimCodeAndReferenceTest {

    /** Crockford-ish alphabet: no 0/O/1/I so citizens can write the code down without confusion. */
    private static final Pattern CLAIM_CODE = Pattern.compile("^[23456789ABCDEFGHJKMNPQRSTVWXYZ]{20}$");

    @Test
    void claimCodesAreTwentyCharsFromTheUnambiguousAlphabet() {
        for (int i = 0; i < 200; i++) {
            String code = PublicContributionIntakeService.newClaimCode();
            assertTrue(CLAIM_CODE.matcher(code).matches(), "unexpected claim code: " + code);
            assertFalse(code.contains("0"));
            assertFalse(code.contains("O"));
            assertFalse(code.contains("1"));
            assertFalse(code.contains("I"));
        }
    }

    @Test
    void claimCodesAreEffectivelyUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            seen.add(PublicContributionIntakeService.newClaimCode());
        }
        // A 20-char, 30-symbol code has ~98 bits of entropy; 5k draws must not collide.
        assertEquals(5_000, seen.size(), "claim codes collided — entropy regressed");
    }

    @Test
    void sha256HashIsDeterministicStableAndSixtyFourHexChars() {
        String code = "ABCDEFGHJKMNPQRSTVWX";
        String hash = PublicContributionIntakeService.sha256Hex(code);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("^[0-9a-f]{64}$"));
        // Deterministic: the same code must always hash to the same value (status lookup depends on it).
        assertEquals(hash, PublicContributionIntakeService.sha256Hex(code));
        // Known-answer vector guards the algorithm/encoding itself.
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                PublicContributionIntakeService.sha256Hex("hello"));
        // The hash must NOT be the plaintext (the plaintext is never stored).
        assertNotEquals(code, hash);
        assertNotEquals(PublicContributionIntakeService.sha256Hex("ABCDEFGHJKMNPQRSTVWY"), hash);
    }

    @Test
    void referencesAreHumanReadableAndCollisionChecked() {
        ReferenceGenerator gen = new ReferenceGenerator();
        String ref = gen.generate("IDEA", candidate -> false);
        assertTrue(ref.matches("^IDEA-\\d{4}-[0-9A-F]{6}$"), "unexpected reference: " + ref);
    }

    @Test
    void referenceGeneratorFallsBackWhenEverySixCharCandidateClashes() {
        ReferenceGenerator gen = new ReferenceGenerator();
        // Force the 6-char space to always "exist" so the guard has to widen the suffix.
        String ref = gen.generate("IDEA", candidate ->
                candidate.matches("^IDEA-\\d{4}-[0-9A-F]{6}$"));
        // Fallback widens to a 12-char UUID slice (may include a hyphen); the point is it is
        // no longer the clashing 6-char form and stays human-readable.
        assertFalse(ref.matches("^IDEA-\\d{4}-[0-9A-F]{6}$"), "fallback still used the clashing 6-char form");
        assertTrue(ref.matches("^IDEA-\\d{4}-[0-9A-F-]{12}$"),
                "expected widened fallback suffix, got: " + ref);
    }
}
