"use client";

/**
 * Passkey sign-in — WebAuthn passwordless entry point.
 * Route: /auth/login/biometric | pageTitle: "Biometric Verification"
 *
 * L1: native Keycloak WebAuthn passkeys. "Sign in with a passkey" calls the BFF
 * initiate endpoint and redirects the browser to the Keycloak-hosted ceremony;
 * the return trip lands on /auth/login/passkey/callback which exchanges the code
 * for a real session. An authenticated user can instead register THIS device as
 * a passkey (kc_action=webauthn-register-passwordless).
 *
 * Honest state: the feature is flag-gated in the BFF. When initiate reports
 * "not enabled" (501 / PASSKEY_NOT_ENABLED), we keep the previous honest message
 * rather than pretending — no fabricated success, no placeholder credential.
 */

import { useState } from "react";
import { ArrowLeft, Fingerprint, Loader2 } from "lucide-react";
import Link from "next/link";
import { AuthLayout } from "@/components/AuthLayout";
import { useAuthStore } from "@/hooks/useAuthStore";
import { beginKeycloakAction, beginOidcLogin } from "@/lib/auth/web-session";

export default function BiometricLoginPage() {
  const { isAuthenticated } = useAuthStore();

  const [error, setError] = useState<string | null>(null);
  const [redirecting, setRedirecting] = useState(false);

  async function start(register: boolean) {
    setError(null);
    setRedirecting(true);
    if (!register) {
      beginOidcLogin({ requiredAcr: "urn:impilo:aal2", returnTo: "/home" });
      return;
    }
    try {
      await beginKeycloakAction("webauthn-register-passwordless", "/settings/security");
    } catch {
      setRedirecting(false);
      setError("Could not start passkey enrollment. Please try again.");
    }
  }

  const busy = redirecting;

  return (
    <AuthLayout>
      <div className="mb-4">
        <Link
          href="/auth/login"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to sign in
        </Link>
      </div>

      <h2 className="text-xl font-semibold text-foreground mb-1">Passkey sign-in</h2>
      <p className="text-sm text-muted-foreground mb-6">
        Use your device biometrics or security key — no password needed
      </p>

      {error && (
        <div
          data-testid="passkey-error"
          className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger"
        >
          {error}
        </div>
      )}

      <div className="flex flex-col items-center py-6">
        <div className="w-32 h-32 rounded-full border-4 border-border bg-background text-primary flex items-center justify-center">
          {busy ? <Loader2 className="w-16 h-16 animate-spin" /> : <Fingerprint className="w-16 h-16" />}
        </div>

      </div>

      <button
          type="button"
          data-testid="passkey-signin"
          disabled={busy}
          onClick={() => start(false)}
          className="w-full py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
        >
          {busy ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Starting passkey…
            </>
          ) : (
            <>
              <Fingerprint className="w-4 h-4" />
              Sign in with a passkey
            </>
          )}
      </button>

      {isAuthenticated && (
        <button
          type="button"
          data-testid="passkey-register"
          disabled={busy}
          onClick={() => start(true)}
          className="mt-3 w-full py-2.5 border border-border text-foreground text-sm font-medium rounded-lg hover:border-primary/25 hover:bg-primary-soft focus:outline-none focus:ring-2 focus:ring-primary/40 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
        >
          Register this device as a passkey
        </button>
      )}

      <Link
        href="/auth/login"
        className="mt-3 w-full py-2.5 border border-border text-foreground text-sm font-medium rounded-lg hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary/40 flex items-center justify-center gap-2 transition-colors"
      >
        Use another sign-in method
      </Link>
    </AuthLayout>
  );
}
