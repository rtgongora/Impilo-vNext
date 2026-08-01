"use client";

/** Provider ID identifies an account; Keycloak performs every authentication step. */

import { useState, type FormEvent } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, BadgeCheck, KeyRound } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { buildPostLoginResolvingPath } from "@/lib/resolve-post-login-destination";
import { beginOidcLogin } from "@/lib/auth/web-session";

export default function ProviderIdLoginPage() {
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const setFocusedWorkMode = useOperationalContextStore((state) => state.setFocusedWorkMode);
  const [providerId, setProviderId] = useState(searchParams.get("providerId") ?? "");
  const [focused, setFocused] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!providerId.trim()) {
      setError("Please enter your Provider ID.");
      return;
    }
    if (focused) setFocusedWorkMode(true);
    beginOidcLogin({
      loginHint: providerId,
      requiredAcr: "urn:impilo:aal2",
      returnTo: buildPostLoginResolvingPath(returnTo),
    });
  }

  return (
    <AuthLayout>
      <Link href="/auth/login" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> Back to sign in
      </Link>
      <h2 className="mb-1 text-xl font-semibold text-foreground">Health Provider Login</h2>
      <p className="mb-6 text-sm text-muted-foreground">
        Your Provider ID identifies your account. Authentication and workforce MFA continue on the protected Impilo identity service.
      </p>

      {error && <div role="alert" className="mb-4 rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-danger">{error}</div>}

      <form onSubmit={submit} className="space-y-4">
        <div>
          <label htmlFor="provider-id" className="mb-1 block text-sm font-medium text-foreground">Provider ID</label>
          <div className="relative">
            <BadgeCheck className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input id="provider-id" type="text" required autoComplete="username" value={providerId}
              onChange={(event) => { setProviderId(event.target.value); setError(null); }}
              placeholder="e.g. PRV-2024-00001"
              className="w-full rounded-lg border border-border py-2.5 pl-10 pr-4 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-primary/40" />
          </div>
          <p className="mt-1 text-xs text-muted-foreground">A Provider ID alone never authenticates or grants authority.</p>
        </div>

        <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-border bg-background px-3 py-3">
          <input type="checkbox" checked={focused} onChange={(event) => setFocused(event.target.checked)}
            className="mt-0.5 h-4 w-4 rounded border-border text-primary focus:ring-primary/40" />
          <span>
            <span className="block text-sm font-medium text-foreground">Sign in to focused work mode</span>
            <span className="mt-0.5 block text-xs text-muted-foreground">Show the work zones for your active role and context.</span>
          </span>
        </label>

        <button type="submit" disabled={!providerId.trim()}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-primary py-2.5 text-sm font-medium text-white hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50">
          <KeyRound className="h-4 w-4" /> Continue to secure sign-in
        </button>
      </form>
    </AuthLayout>
  );
}
