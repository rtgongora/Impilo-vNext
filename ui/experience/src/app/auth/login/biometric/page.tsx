"use client";

/**
 * Biometric Verification — triggers Keycloak OIDC with gold LoA (biometric acr).
 * Route: /auth/login/biometric | pageTitle: "Biometric Verification"
 */

import { useState, useCallback } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  Fingerprint,
  Loader2,
} from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { buildAuthUrl } from "@/lib/oidc";

export default function BiometricLoginPage() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleVerify = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const redirectUri = `${window.location.origin}/auth/callback`;
      const url = await buildAuthUrl(redirectUri, "urn:mace:incommon:iap:gold");
      window.location.href = url;
    } catch {
      setError("Could not reach the identity provider. Please try again.");
      setLoading(false);
    }
  }, []);

  return (
    <AuthLayout>
      <div className="mb-4">
        <Link
          href="/auth/login"
          className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to sign in
        </Link>
      </div>

      <h2 className="text-xl font-semibold text-gray-900 mb-1">
        Biometric Verification
      </h2>
      <p className="text-sm text-gray-500 mb-6">
        You will be redirected to complete biometric verification
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="flex flex-col items-center py-6">
        <div className="w-32 h-32 rounded-full border-4 border-gray-300 text-gray-400 bg-gray-50 flex items-center justify-center">
          {loading ? (
            <Loader2 className="w-16 h-16 animate-spin text-blue-500" />
          ) : (
            <Fingerprint className="w-16 h-16" />
          )}
        </div>
        <p className="mt-4 text-sm text-gray-500 text-center">
          {loading
            ? "Redirecting to identity provider…"
            : "Tap the button below to begin biometric verification"}
        </p>
        <p className="mt-2 text-xs text-gray-400 text-center">
          Biometric verification is handled by your device hardware via Keycloak
        </p>
      </div>

      <button
        type="button"
        onClick={handleVerify}
        disabled={loading}
        className="w-full py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
      >
        {loading ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            Redirecting…
          </>
        ) : (
          "Verify with Biometrics"
        )}
      </button>
    </AuthLayout>
  );
}
