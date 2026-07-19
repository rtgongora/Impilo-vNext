"use client";

/**
 * Health Provider Login — identify-then-authenticate (D-P4, PJ5).
 *
 * The Provider ID only IDENTIFIES the account (it is often publicly known and
 * is never a secret); the person then authenticates with their own account
 * password. A Provider ID + short PIN as the sole credential is retired —
 * LOGIN-PROVIDERID-DENY (impilo.authz): a professional identifier never
 * authenticates by itself.
 *
 * Route: /auth/login/provider-id | pageTitle: "Health Provider Login"
 */

import { useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, BadgeCheck, KeyRound, Loader2 } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { useLogin } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConsentStore } from "@/hooks/useConsentStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { buildPostLoginResolvingPath } from "@/lib/resolve-post-login-destination";

export default function ProviderIdLoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const login = useLogin();
  const setAuth = useAuthStore((s) => s.setAuth);
  const setFocusedWorkMode = useOperationalContextStore((s) => s.setFocusedWorkMode);

  const [providerId, setProviderId] = useState(searchParams.get("providerId") ?? "");
  const [password, setPassword] = useState("");
  const [signInFocusedWorkMode, setSignInFocusedWorkMode] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!providerId.trim()) {
      setError("Please enter your Provider ID.");
      return;
    }

    if (!password.trim() || password.length < 8) {
      setError("Please enter your account password (at least 8 characters).");
      return;
    }

    login.mutate(
      {
        email: providerId,
        password,
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
          if (signInFocusedWorkMode) {
            setFocusedWorkMode(true);
          }
          router.push(buildPostLoginResolvingPath(returnTo));
        },
        onError: () => {
          // Generic on purpose — never confirm whether the Provider ID exists.
          setError("We could not sign you in with those details. Please try again.");
        },
      },
    );
  }

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

      <h2 className="text-xl font-semibold text-foreground mb-1">
        Health Provider Login
      </h2>
      <p className="text-sm text-muted-foreground mb-6">
        Your Provider ID identifies your account. You still sign in as yourself
        — with your own password.
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label
            htmlFor="provider-id"
            className="block text-sm font-medium text-foreground mb-1"
          >
            Provider ID
          </label>
          <div className="relative">
            <BadgeCheck className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              id="provider-id"
              type="text"
              required
              value={providerId}
              onChange={(e) => setProviderId(e.target.value)}
              placeholder="e.g. PRV-2024-00001"
              className="w-full pl-10 pr-4 py-2.5 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
            />
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            Identifies your account only — a Provider ID alone never signs you in
          </p>
        </div>

        <div>
          <label
            htmlFor="account-password"
            className="block text-sm font-medium text-foreground mb-1"
          >
            Account password
          </label>
          <div className="relative">
            <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              id="account-password"
              type="password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Your Impilo account password"
              className="w-full pl-10 pr-4 py-2.5 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
            />
          </div>
        </div>

        <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-border bg-background px-3 py-3">
          <input
            type="checkbox"
            className="mt-0.5 h-4 w-4 rounded border-border text-primary focus:ring-primary/40"
            checked={signInFocusedWorkMode}
            onChange={(e) => setSignInFocusedWorkMode(e.target.checked)}
          />
          <span>
            <span className="block text-sm font-medium text-foreground">Sign in to focused work mode</span>
            <span className="mt-0.5 block text-xs text-muted-foreground">
              Hide personal and professional tabs; show only work zones after sign-in
            </span>
          </span>
        </label>

        <button
          type="submit"
          disabled={login.isPending || !providerId.trim() || !password.trim()}
          className="w-full py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
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
          className="text-xs text-primary hover:text-primary-hover"
        >
          Sign in with email instead
        </Link>
      </div>
    </AuthLayout>
  );
}
