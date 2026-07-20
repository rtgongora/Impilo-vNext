"use client";

/**
 * Passkey callback — WebAuthn passwordless return trip.
 * Route: /auth/login/passkey/callback | pageTitle: "Completing passkey sign-in"
 *
 * Keycloak redirects here with ?code&state after the WebAuthn ceremony. We verify
 * the returned `state` against the value stashed on initiate (CSRF), then POST the
 * code + PKCE verifier to the BFF callback which exchanges it for a REAL session
 * (same shape as password login). A session is set ONLY on a successful exchange —
 * an absent code, a state mismatch, or a failed exchange lands on an honest error.
 */

import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { Loader2, ShieldAlert } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { usePasskeyCallback } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConsentStore } from "@/hooks/useConsentStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { buildPostLoginResolvingPath } from "@/lib/resolve-post-login-destination";
import { readPasskeyHandoff, clearPasskeyHandoff } from "../passkey-session";

export default function PasskeyCallbackPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const callback = usePasskeyCallback();
  const { setAuth } = useAuthStore();

  const [error, setError] = useState<string | null>(null);
  const ran = useRef(false);

  useEffect(() => {
    // Guard against React StrictMode double-invoke — an auth code is single-use.
    if (ran.current) return;
    ran.current = true;

    const code = searchParams.get("code");
    const returnedState = searchParams.get("state");
    const kcError = searchParams.get("error");
    const handoff = readPasskeyHandoff();

    if (kcError) {
      setError("Passkey sign-in was cancelled or failed. Please try again.");
      clearPasskeyHandoff();
      return;
    }
    if (!code) {
      setError("Sign-in could not be completed — no authorization code was returned.");
      clearPasskeyHandoff();
      return;
    }
    if (!handoff || (returnedState && returnedState !== handoff.state)) {
      // CSRF/expiry guard: never exchange a code we cannot bind to our initiate.
      setError("This sign-in link has expired. Please start again.");
      clearPasskeyHandoff();
      return;
    }

    callback.mutate(
      { code, codeVerifier: handoff.codeVerifier },
      {
        onSuccess: (res) => {
          clearPasskeyHandoff();
          const attrs = res.data.attributes;
          const { token, user } = attrs;
          const expiresAt = (attrs as Record<string, unknown>).expiresAt as string | undefined;
          setAuth(
            {
              id: user.id,
              healthId: (user as { healthId?: string }).healthId ?? user.id,
              email: user.email,
              displayName: user.displayName,
              roles: user.roles,
              actorType: user.actorType as "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM",
              assuranceLevel: "VERIFIED",
              providerActivated: false,
              loginMethod: "biometric",
            },
            token,
            null,
            expiresAt
          );
          useWorkModeStore.getState().deriveFromRoles(user.roles);
          useConsentStore.getState().hydrate(user.id);
          router.replace(buildPostLoginResolvingPath(null));
        },
        onError: () => {
          clearPasskeyHandoff();
          setError("We couldn't verify your passkey. Please try again or use another method.");
        },
      }
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <AuthLayout>
      {error ? (
        <div className="flex flex-col items-center py-8 text-center">
          <div className="w-16 h-16 rounded-full bg-danger-soft text-danger flex items-center justify-center">
            <ShieldAlert className="w-8 h-8" />
          </div>
          <h2 className="mt-4 text-lg font-semibold text-foreground">Passkey sign-in failed</h2>
          <p data-testid="passkey-callback-error" className="mt-1 text-sm text-muted-foreground">
            {error}
          </p>
          <Link
            href="/auth/login"
            className="mt-6 w-full py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 flex items-center justify-center gap-2 transition-colors"
          >
            Back to sign in
          </Link>
        </div>
      ) : (
        <div className="flex flex-col items-center py-10 text-center">
          <Loader2 className="w-10 h-10 animate-spin text-primary" />
          <h2 className="mt-4 text-lg font-semibold text-foreground">Completing passkey sign-in…</h2>
          <p className="mt-1 text-sm text-muted-foreground">Verifying your passkey with the identity service.</p>
        </div>
      )}
    </AuthLayout>
  );
}
