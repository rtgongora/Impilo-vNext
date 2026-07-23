package zw.gov.mohcc.impilo.telemonitoring.domain;

import java.util.Locale;

/**
 * MVUMO consent POINTER state carried on a monitoring plan (§14.2 capture item 9).
 *
 * <p>The consent journey itself — capture, scope, delegation, revocation — is MVUMO's;
 * this enum only mirrors where that journey stands so activation can apply the §14.2
 * posture: <b>fail closed only on an explicit refusal</b> ({@link #REFUSED} /
 * {@link #REVOKED}). An absent consent record is honest {@link #UNVERIFIED} — activation
 * proceeds with that state recorded, never silently upgraded.</p>
 */
public enum ConsentStatus {
    /** No consent pointer verified against MVUMO yet — the honest default, never implied consent. */
    UNVERIFIED,
    /** MVUMO journey started but not concluded. */
    PENDING,
    /** MVUMO reports consent granted for the monitoring programme scope. */
    GRANTED,
    /** Explicit refusal — activation MUST fail closed. */
    REFUSED,
    /** Previously granted, since revoked — activation MUST fail closed. */
    REVOKED;

    /** True when the state is an explicit refusal that blocks activation. */
    public boolean blocksActivation() {
        return this == REFUSED || this == REVOKED;
    }

    /** Lenient parse: null/blank → {@link #UNVERIFIED}; unknown values are rejected. */
    public static ConsentStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return UNVERIFIED;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
