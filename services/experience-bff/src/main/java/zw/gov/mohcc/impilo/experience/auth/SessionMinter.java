package zw.gov.mohcc.impilo.experience.auth;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Mints a Keycloak session for an <em>already-proven</em> person WITHOUT a password.
 *
 * <p>This is the single seam through which the biometric scan-to-login path
 * (L3 — ABIS 1:N) obtains a real Keycloak token for a person whose identity was
 * established out-of-band (a strong 1:N biometric match). It exists ONLY so that
 * session minting stays Keycloak-owned: the BFF never fabricates a token, it asks
 * Keycloak to issue one for a resolved subject via OAuth2 token exchange.</p>
 *
 * <p><b>Governance / EXTERNAL prerequisites</b> — a concrete implementation is wired
 * in only when {@code impilo.auth.biometric-login.enabled=true}. Beyond the flag,
 * production use additionally requires:</p>
 * <ul>
 *   <li>a Keycloak client (the BFF service account) granted the
 *       {@code token-exchange} permission and the {@code impersonation} role so it
 *       may mint tokens for another subject;</li>
 *   <li>Keycloak configured so the resolved person anchor (Health ID) is resolvable
 *       as {@code requested_subject} (username / id / a subject resolver);</li>
 *   <li>a deployed ABIS with enrolled biometric templates + capture devices;</li>
 *   <li>1:N false-accept-rate governance sign-off for the LOGIN reason.</li>
 * </ul>
 *
 * <p>When the flag is off, NO bean of this type exists; the biometric-login endpoint
 * detects its absence and returns 501 — password/ROPC/passkey login is untouched.</p>
 */
public interface SessionMinter {

    /**
     * Ask Keycloak to mint tokens for an already-authenticated subject.
     *
     * @param subjectUsernameOrId the resolved person anchor Keycloak can resolve as
     *                            {@code requested_subject} (Health ID / username / sub)
     * @return the Keycloak token response, or {@code null} when the exchange fails —
     *         the caller MUST treat {@code null} as fail-closed and NEVER mint a session
     */
    KeycloakTokens mintForSubject(String subjectUsernameOrId);

    /**
     * Opaque holder for a Keycloak token response ({@code access_token},
     * {@code refresh_token}, {@code expires_in}, …). The raw node is fed straight
     * into the controller's canonical session builder so the biometric-minted
     * session is byte-for-byte the same shape as a password/passkey session.
     */
    record KeycloakTokens(JsonNode tokenResponse) {

        public boolean hasAccessToken() {
            return tokenResponse != null && tokenResponse.hasNonNull("access_token");
        }
    }
}
