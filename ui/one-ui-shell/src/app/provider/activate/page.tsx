"use client";

/**
 * Provider Activation Page — Health OS §6
 *
 * "Sign in as a person; practice as a provider only under activated Provider ID."
 */

import { useState, useEffect, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ShieldCheck, Loader2, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { ProviderRoleActivationRail } from "@/components/auth/ProviderRoleActivationRail";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient } from "@/lib/api-client";
import {
  normalizeProviderListingResponse,
  recordFromLinkedProviderId,
  type ProviderActivationRecord,
} from "@/lib/provider-activation";

export default function ProviderActivatePage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo") ?? "/clinical";
  const { user, activateProvider } = useAuthStore();

  const [providers, setProviders] = useState<ProviderActivationRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activating, setActivating] = useState(false);

  const handleActivate = useCallback(
    (provider: ProviderActivationRecord) => {
      if (!user || !provider.providerId) return;
      setActivating(true);
      activateProvider(provider.providerId);

      if (typeof window !== "undefined") {
        sessionStorage.setItem("exp:provider_display", provider.displayName);
        sessionStorage.setItem("exp:provider_cadre", provider.cadre);
      }

      router.replace(returnTo);
    },
    [user, activateProvider, returnTo, router],
  );

  useEffect(() => {
    if (!user) return;

    let cancelled = false;
    setLoading(true);
    setError("");

    apiClient
      .get<unknown>(`/internal/v1/identity/providers?actorId=${encodeURIComponent(user.id)}`)
      .then((res) => {
        if (cancelled) return;
        const active = normalizeProviderListingResponse(res).filter((p) => p.status === "ACTIVE");

        if (active.length === 0 && user.linkedIds?.providerId) {
          const fallback = recordFromLinkedProviderId(
            user.linkedIds.providerId,
            user.displayName,
          );
          setProviders([fallback]);
          return;
        }

        setProviders(active);
      })
      .catch(() => {
        if (cancelled) return;
        if (user.linkedIds?.providerId) {
          setProviders([
            recordFromLinkedProviderId(user.linkedIds.providerId, user.displayName),
          ]);
          return;
        }
        setError("Could not load provider registrations.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [user]);

  useEffect(() => {
    if (loading || activating || providers.length !== 1) return;
    handleActivate(providers[0]!);
  }, [loading, activating, providers, handleActivate]);

  function handleSkip() {
    router.replace("/home");
  }

  if (!user) return null;

  return (
    <AppLayout>
      <PageShell
        title="Activate Provider Role"
        subtitle="Health OS §6 — Sign in as a person; practice as a provider only under activated Provider ID"
      >
        <div className="max-w-xl mx-auto space-y-4">
          <ProviderRoleActivationRail compact returnTo={returnTo} />
          <div className="mb-6 rounded-lg border border-primary/25 bg-primary-soft p-4 text-sm text-impilo-800">
            <div className="flex items-center gap-2 mb-1">
              <ShieldCheck className="h-4 w-4 text-primary" />
              <strong>Provider Role Activation</strong>
            </div>
            <p>
              You are signed in as <strong>{user.displayName}</strong> (Health ID: {user.id.slice(0, 8)}...).
              To perform regulated clinical work, select and activate one of your registered Provider IDs below.
            </p>
          </div>

          {loading && (
            <div className="flex items-center justify-center py-12 text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin mr-2" /> Loading provider registrations...
            </div>
          )}

          {error && (
            <div className="rounded-lg border border-danger/28 bg-danger-soft p-4 text-sm text-red-800">
              <AlertTriangle className="h-4 w-4 inline mr-1" /> {error}
            </div>
          )}

          {!loading && !error && providers.length === 0 && (
            <div className="rounded-lg border border-warning/35 bg-warning-soft p-4 text-sm text-warning-foreground">
              <p className="font-medium mb-1">No active Provider IDs found</p>
              <p>
                Your Health ID is not linked to any active provider registration.
                Contact your facility administrator or the Health Professions Authority if you believe this is an error.
              </p>
              <button onClick={handleSkip} className="mt-3 text-xs underline text-warning-foreground">
                Continue as non-provider →
              </button>
            </div>
          )}

          {!loading && providers.length > 1 && !activating && (
            <div className="space-y-3">
              {providers.map((p) => (
                <button
                  key={p.providerId}
                  type="button"
                  onClick={() => handleActivate(p)}
                  className="w-full text-left rounded-lg border border-border bg-card p-4 hover:border-impilo-400 hover:ring-1 hover:ring-impilo-200 transition-all"
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-foreground">{p.displayName}</p>
                      <p className="text-sm text-muted-foreground">
                        {p.cadre} — {p.registrationNumber}
                      </p>
                      {p.licensureExpiry && (
                        <p className="text-xs text-muted-foreground mt-1">
                          Licence valid until {p.licensureExpiry}
                        </p>
                      )}
                    </div>
                    <ShieldCheck className="h-5 w-5 text-green-500" />
                  </div>
                </button>
              ))}
              <button
                type="button"
                onClick={handleSkip}
                className="block mx-auto mt-4 text-xs text-muted-foreground underline"
              >
                Skip — continue without provider role
              </button>
            </div>
          )}

          {activating && (
            <div className="flex items-center justify-center py-12 text-primary">
              <Loader2 className="h-5 w-5 animate-spin mr-2" /> Activating provider context...
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
