"use client";

/**
 * Sign In with Provider ID — triggers Keycloak OIDC with silver LoA.
 * Route: /auth/login/provider-id | pageTitle: "Sign In with Provider ID"
 */

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, BadgeCheck, Loader2 } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { buildAuthUrl } from "@/lib/oidc";

export default function ProviderIdLoginPage() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleLogin() {
    try {
      setError(null);
      setLoading(true);
      const redirectUri = `${window.location.origin}/auth/callback`;
      const url = await buildAuthUrl(redirectUri, "urn:mace:incommon:iap:silver");
      window.location.href = url;
    } catch {
      setError("Could not reach the identity provider. Please try again.");
      setLoading(false);
    }
  }

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
        Sign In with Provider ID
      </h2>
      <p className="text-sm text-gray-500 mb-6">
        You will be redirected to Keycloak to authenticate with your Provider ID
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="flex flex-col items-center py-6">
        <div className="w-20 h-20 rounded-full border-2 border-blue-200 bg-blue-50 flex items-center justify-center">
          <BadgeCheck className="w-10 h-10 text-blue-500" />
        </div>
        <p className="mt-4 text-sm text-gray-500 text-center">
          Your provider number was assigned during registration.
          <br />
          Enter it on the Keycloak login screen.
        </p>
      </div>

      <button
        type="button"
        onClick={handleLogin}
        disabled={loading}
        className="w-full py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
      >
        {loading ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            Redirecting…
          </>
        ) : (
          <>
            <BadgeCheck className="w-4 h-4" />
            Continue with Provider ID
          </>
        )}
      </button>

      <div className="mt-6 text-center">
        <Link
          href="/auth/login"
          className="text-xs text-blue-600 hover:text-blue-800"
        >
          Sign in with email instead
        </Link>
      </div>
    </AuthLayout>
  );
}
