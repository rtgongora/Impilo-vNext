package zw.gov.mohcc.impilo.tshepo.authz.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A person who has never been asked for consent, and a person who has refused, are not in the same
 * situation. Telling both "access denied" turns a next step into a dead end.
 *
 * <p>Both still refuse the request — this governs what the caller is <em>told</em>, never whether
 * access is granted.</p>
 */
class ConsentFeedbackTest {

    @Test
    @DisplayName("no consent yet is obtainable — the person can still be asked")
    void missingConsentIsObtainable() {
        assertThat(PolicyEngine.CONSENT_OBTAINABLE_REASONS).contains("NO_ACTIVE_CONSENT");
        // This is the reason the live consent service actually returns for a subject with no
        // directive -- verified against the running service, not assumed from the source.
    }

    @Test
    @DisplayName("an expired directive is obtainable — it can be renewed")
    void expiredConsentIsObtainable() {
        assertThat(PolicyEngine.CONSENT_OBTAINABLE_REASONS).contains("CONSENT_EXPIRED");
    }

    @Test
    @DisplayName("a withdrawal or explicit refusal is NOT presented as 'just ask again'")
    void withdrawnConsentIsNotObtainable() {
        // Someone who has withdrawn consent has given an answer. Inviting the caller to ask again
        // would be both misleading and a nudge to pressure the person.
        assertThat(PolicyEngine.CONSENT_OBTAINABLE_REASONS)
                .doesNotContain("CONSENT_WITHDRAWN")
                .doesNotContain("CONSENT_REFUSED")
                .doesNotContain("CONSENT_DENIED");
    }

    @Test
    @DisplayName("the set is an ALLOWLIST — an unknown reason must not become actionable")
    void unknownReasonsAreNotActionable() {
        // A new refusal reason added to the consent service later must default to the
        // non-actionable message rather than silently inheriting "ask again".
        assertThat(PolicyEngine.CONSENT_OBTAINABLE_REASONS)
                .doesNotContain("SOME_FUTURE_REFUSAL_REASON")
                .hasSize(3);
    }
}
