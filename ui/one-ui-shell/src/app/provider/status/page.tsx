"use client";

/**
 * Provider Status Resolution — restricted work access when Provider ID is not active.
 * Route: /provider/status
 */

import Link from "next/link";
import { AlertTriangle, Heart, ShieldCheck, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useIdentityContext } from "@/hooks/useIdentityContext";
import { providerStatusResolutionMessage } from "@/lib/identity-context";

export default function ProviderStatusPage() {
  const user = useAuthStore((s) => s.user);
  const deactivateProvider = useAuthStore((s) => s.deactivateProvider);
  const { providerStatus, linkedProviderId } = useIdentityContext();
  const copy = providerStatusResolutionMessage(providerStatus);

  function handlePersonalHealth() {
    deactivateProvider();
    window.location.assign("/home");
  }

  return (
    <AppLayout>
      <PageShell title={copy.title} subtitle="Provider ID status affects work access">
        <div className="max-w-xl mx-auto space-y-4">
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
            <div className="flex items-start gap-3">
              <AlertTriangle className="h-5 w-5 text-amber-600 shrink-0 mt-0.5" />
              <div className="text-sm text-amber-900">
                <p>{copy.body}</p>
                {linkedProviderId && (
                  <p className="mt-2 font-mono text-xs">Provider ID: {linkedProviderId}</p>
                )}
                {providerStatus && (
                  <p className="mt-1 text-xs uppercase tracking-wide">Status: {providerStatus}</p>
                )}
              </div>
            </div>
          </div>

          {user && (
            <p className="text-sm text-gray-600">
              Signed in as <strong>{user.displayName}</strong> (Health ID: {user.id.slice(0, 8)}…)
            </p>
          )}

          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            {copy.canUpdate && (
              <Link
                href="/professional"
                className="inline-flex items-center justify-center gap-2 rounded-lg bg-impilo-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-impilo-600"
              >
                <RefreshCw className="h-4 w-4" />
                Review professional profile
              </Link>
            )}
            {copy.canContactSupport && (
              <Link
                href="/support"
                className="inline-flex items-center justify-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                <ShieldCheck className="h-4 w-4" />
                Contact support
              </Link>
            )}
            {copy.canUsePersonalHealth && (
              <button
                type="button"
                onClick={handlePersonalHealth}
                className="inline-flex items-center justify-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                <Heart className="h-4 w-4" />
                Switch to My Life / My Health
              </button>
            )}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
