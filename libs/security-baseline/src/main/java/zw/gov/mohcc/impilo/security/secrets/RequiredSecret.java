package zw.gov.mohcc.impilo.security.secrets;

import java.util.Locale;

/**
 * Fail-closed validation for key material that a service cannot safely operate without.
 *
 * <p>The estate learned this the expensive way. Five cryptographic secrets — Impilo ID
 * encryption, VITO QR signing, card-print assertion signing, the card verify key, and the
 * eligibility token secret — each shipped a source-visible dev fallback and a startup warning.
 * A warning is a note to a reader who is not there: it fires once, nobody reads the hundredth
 * restart, and after boot the weak-key path is indistinguishable from the strong-key path. None
 * of the five was ever provisioned in {@code deploy/}, {@code infra/} or {@code compose/}, so the
 * fallback was not a preview convenience that production overrode — it was the live configuration
 * everywhere.
 *
 * <p>Exactly one secret in the estate was provisioned:
 * {@code wallet.card.encryption-master-key}, whose provider refuses to start without it. Failing
 * closed is what forced someone to supply a value; warning and continuing meant nobody had to.
 * That is the whole argument for this class, and the reason the check lives here once rather than
 * being copied into each constructor, where it drifted before.
 *
 * <p>Rejects null, blank, anything shorter than {@link #MIN_LENGTH}, and any value carrying a
 * placeholder marker such as {@code change-me} or {@code dev-seed} — a 40-character
 * {@code "...-dev-key-change-me-32b"} passes a length check while being public knowledge.
 *
 * <p>Security: the value is never logged, echoed, or included in the exception message.
 */
public final class RequiredSecret {

    /** Floor for symmetric key material and signing seeds across the estate. */
    public static final int MIN_LENGTH = 32;

    /**
     * Substrings that mark a value as a placeholder rather than a secret. Matched
     * case-insensitively against the whole value, so they catch both bare markers
     * ({@code change-me}) and decorated ones ({@code vito-qr-signing-dev-seed-change-me-32b}).
     */
    private static final String[] PLACEHOLDER_MARKERS = {
            "change-me", "changeme", "change_me",
            "dev-seed", "dev-key", "dev_seed", "dev_key",
            "not-for-production", "placeholder", "example",
    };

    private RequiredSecret() {
    }

    /**
     * Return the secret, or refuse to start.
     *
     * @param property the configuration property name, for the operator reading the failure
     * @param value    the configured value
     * @param purpose  what breaks if this is weak, phrased for someone who has to fix it now
     * @return {@code value}, stripped of surrounding whitespace
     * @throws IllegalStateException if the value is absent, too short, or a placeholder
     */
    public static String require(String property, String value, String purpose) {
        String candidate = value == null ? "" : value.strip();
        if (candidate.isEmpty()) {
            throw failure(property, purpose, "is not set");
        }
        if (candidate.length() < MIN_LENGTH) {
            throw failure(property, purpose, "is shorter than the " + MIN_LENGTH + "-character minimum");
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        for (String marker : PLACEHOLDER_MARKERS) {
            if (lower.contains(marker)) {
                throw failure(property, purpose, "is a placeholder, not a secret (contains '" + marker + "')");
            }
        }
        return candidate;
    }

    /** True when a value would satisfy {@link #require}, for callers choosing between two sources. */
    public static boolean isUsable(String value) {
        if (value == null) {
            return false;
        }
        String candidate = value.strip();
        if (candidate.length() < MIN_LENGTH) {
            return false;
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        for (String marker : PLACEHOLDER_MARKERS) {
            if (lower.contains(marker)) {
                return false;
            }
        }
        return true;
    }

    private static IllegalStateException failure(String property, String purpose, String fault) {
        return new IllegalStateException(
                "Refusing to start: " + property + " " + fault + ". " + purpose
                        + " Supply a strong secret of at least " + MIN_LENGTH
                        + " characters (out-of-band Secret 'impilo-app-secrets'; see "
                        + "scripts/secrets/bootstrap-secrets.sh and docs/runbooks/key-ceremony.md).");
    }
}
