/**
 * Passkey (WebAuthn passwordless) ceremony hand-off state.
 *
 * The ceremony spans two browser navigations: `initiate` (which redirects to the
 * Keycloak-hosted authorize URL) and the `/auth/login/passkey/callback` return.
 * The PKCE code-verifier and CSRF `state` are minted server-side by the BFF and
 * handed back on initiate; we stash them in sessionStorage so the callback can
 * verify `state` (CSRF) and replay the verifier for the token exchange. The
 * verifier is the browser's secret — it never leaves this origin except to the
 * trusted BFF callback.
 */

const STATE_KEY = "exp:passkey_state";
const VERIFIER_KEY = "exp:passkey_code_verifier";

export interface PasskeyHandoff {
  state: string;
  codeVerifier: string;
}

export function storePasskeyHandoff(handoff: PasskeyHandoff): void {
  try {
    sessionStorage.setItem(STATE_KEY, handoff.state);
    sessionStorage.setItem(VERIFIER_KEY, handoff.codeVerifier);
  } catch {
    /* sessionStorage unavailable — callback will treat as an expired ceremony */
  }
}

export function readPasskeyHandoff(): PasskeyHandoff | null {
  try {
    const state = sessionStorage.getItem(STATE_KEY);
    const codeVerifier = sessionStorage.getItem(VERIFIER_KEY);
    if (!state || !codeVerifier) return null;
    return { state, codeVerifier };
  } catch {
    return null;
  }
}

export function clearPasskeyHandoff(): void {
  try {
    sessionStorage.removeItem(STATE_KEY);
    sessionStorage.removeItem(VERIFIER_KEY);
  } catch {
    /* no-op */
  }
}

/** True when the BFF signalled the passkey feature is disabled (flag OFF). */
export function isPasskeyNotEnabled(err: unknown): boolean {
  const status = (err as { status?: number })?.status;
  const code = (err as { error?: { code?: string } })?.error?.code;
  return status === 501 || code === "PASSKEY_NOT_ENABLED";
}
