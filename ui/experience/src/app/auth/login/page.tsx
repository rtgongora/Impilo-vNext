"use client";

/**
 * Sign In — Keycloak OIDC Authorization Code + PKCE flow.
 * Route: /auth/login | pageTitle: "Sign In"
 *
 * Clicking "Sign In" redirects the browser to Keycloak's authorization
 * endpoint. After login, Keycloak redirects back to /auth/callback where
 * the code is exchanged for an access token.
 */

import { useState } from "react";
import { Loader2, ShieldCheck, BadgeCheck, Fingerprint } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { buildAuthUrl } from "@/lib/oidc";

export default function LoginPage() {
  const [loading, setLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleLogin(acrValues?: string, label?: string) {
    try {
      setError(null);
      setLoading(label ?? "email");
      const redirectUri = `${window.location.origin}/auth/callback`;
      const url = await buildAuthUrl(redirectUri, acrValues);
      window.location.href = url;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`Could not reach the identity provider: ${msg}`);
      setLoading(null);
    }
  }

  return (
    <AuthLayout>
      <h2 className="text-xl font-semibold text-gray-900 mb-1">Sign In</h2>
      <p className="text-sm text-gray-500 mb-6">
        Sign in with your Impilo account to continue
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
          {error}
        </div>
      )}

      <button
        onClick={() => handleLogin(undefined, "email")}
        disabled={loading !== null}
        className="w-full py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
      >
        {loading === "email" ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            Redirecting…
          </>
        ) : (
          <>
            <ShieldCheck className="w-4 h-4" />
            Sign In with Keycloak
          </>
        )}
      </button>

      <div className="mt-6 pt-6 border-t border-gray-200">
        <p className="text-xs text-gray-500 text-center mb-3">
          Or sign in with
        </p>
        <div className="flex gap-3">
          <button
            onClick={() => handleLogin("urn:mace:incommon:iap:silver", "provider-id")}
            disabled={loading !== null}
            className="flex-1 flex items-center justify-center gap-2 px-3 py-2 border border-gray-200 rounded-lg text-sm text-gray-700 hover:border-blue-300 hover:bg-blue-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading === "provider-id" ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <BadgeCheck className="w-4 h-4" />
            )}
            Provider ID
          </button>
          <button
            onClick={() => handleLogin("urn:mace:incommon:iap:gold", "biometric")}
            disabled={loading !== null}
            className="flex-1 flex items-center justify-center gap-2 px-3 py-2 border border-gray-200 rounded-lg text-sm text-gray-700 hover:border-blue-300 hover:bg-blue-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading === "biometric" ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Fingerprint className="w-4 h-4" />
            )}
            Biometric
          </button>
        </div>
      </div>
    </AuthLayout>
  );
}
