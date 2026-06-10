"use client";

/**
 * Sign In with Provider ID — Provider ID + PIN login form.
 * Route: /auth/login/provider-id | pageTitle: "Sign In with Provider ID"
 */

import { useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, BadgeCheck, KeyRound, Loader2 } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { useLogin } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConsentStore } from "@/hooks/useConsentStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { buildPostLoginResolvingPath } from "@/lib/resolve-post-login-destination";

export default function ProviderIdLoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const login = useLogin();
  const setAuth = useAuthStore((s) => s.setAuth);

  const [providerId, setProviderId] = useState("");
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!providerId.trim()) {
      setError("Please enter your Provider ID.");
      return;
    }

    if (!pin.trim() || pin.length < 4) {
      setError("Please enter a valid PIN (at least 4 digits).");
      return;
    }

    login.mutate(
      {
        email: providerId,
        password: pin,
        method: "provider_id",
      } as { email: string; password: string },
      {
        onSuccess: (res) => {
          const { token, user } = res.data.attributes;
          setAuth(
            {
              id: user.id,
              email: user.email,
              displayName: user.displayName,
              roles: user.roles,
              actorType: user.actorType as
                | "PROVIDER"
                | "OPERATOR"
                | "CITIZEN"
                | "SYSTEM",
              assuranceLevel: "VERIFIED",
              providerActivated: false,
              loginMethod: "provider_id",
            },
            token,
          );
          useWorkModeStore.getState().deriveFromRoles(user.roles);
          useConsentStore.getState().hydrate(user.id);
          router.push(buildPostLoginResolvingPath(returnTo));
        },
        onError: () => {
          setError("Invalid Provider ID or PIN. Please try again.");
        },
      },
    );
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
        Enter your registered provider number and PIN
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label
            htmlFor="provider-id"
            className="block text-sm font-medium text-gray-700 mb-1"
          >
            Provider ID
          </label>
          <div className="relative">
            <BadgeCheck className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              id="provider-id"
              type="text"
              required
              value={providerId}
              onChange={(e) => setProviderId(e.target.value)}
              placeholder="e.g. PRV-2024-00001"
              className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
            />
          </div>
          <p className="mt-1 text-xs text-gray-400">
            Your provider number was assigned during registration
          </p>
        </div>

        <div>
          <label
            htmlFor="pin"
            className="block text-sm font-medium text-gray-700 mb-1"
          >
            PIN
          </label>
          <div className="relative">
            <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              id="pin"
              type="password"
              required
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={8}
              value={pin}
              onChange={(e) => {
                const val = e.target.value.replace(/\D/g, "");
                setPin(val);
              }}
              placeholder="Enter your PIN"
              className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400 tracking-widest"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={login.isPending || !providerId.trim() || !pin.trim()}
          className="w-full py-2.5 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
        >
          {login.isPending ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Signing in...
            </>
          ) : (
            "Sign In"
          )}
        </button>
      </form>

      <div className="mt-6 text-center">
        <Link
          href="/auth/login"
          className="text-xs text-impilo-500 hover:text-impilo-700"
        >
          Sign in with email instead
        </Link>
      </div>
    </AuthLayout>
  );
}
