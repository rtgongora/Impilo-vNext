"use client";

/**
 * Provider Activation Page — Health OS §6
 *
 * "Sign in as a person; practice as a provider only under activated Provider ID."
 *
 * This page is shown to authenticated users who hold a PROVIDER-capable role but
 * have not yet activated their Provider ID for the current session. The user
 * selects which Provider ID to activate (a person may hold multiple), confirms
 * their capacity, and the system persists the selection to sessionStorage for
 * header injection by api-client.ts.
 */

import { useState, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ShieldCheck, Loader2, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { ProviderRoleActivationRail } from "@/components/auth/ProviderRoleActivationRail";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient } from "@/lib/api-client";

interface ProviderRecord {
  providerId: string;
  displayName: string;
  cadre: string;
  registrationNumber: string;
  status: string;
  licensureExpiry?: string;
}

export default function ProviderActivatePage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo") ?? "/clinical";
  const { user, setAuth, token, refreshToken, expiresAt } = useAuthStore();

  const [providers, setProviders] = useState<ProviderRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activating, setActivating] = useState(false);

  useEffect(() => {
    if (!user) return;
    setLoading(true);
    apiClient
      .get<{ data: ProviderRecord[] }>(`/internal/v1/identity/providers?actorId=${encodeURIComponent(user.id)}`)
      .then((res) => {
        const active = (res.data ?? []).filter((p) => p.status === "ACTIVE");
        setProviders(active);
        // Auto-activate if exactly one provider
        if (active.length === 1) {
          handleActivate(active[0]);
        }
      })
      .catch(() => setError("Could not load provider registrations."))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  function handleActivate(provider: ProviderRecord) {
    if (!user || !token) return;
    setActivating(true);

    // Persist the activated Provider ID to auth store and sessionStorage
    const updatedUser = { ...user, providerId: provider.providerId, providerActivated: true, actorType: "PROVIDER" as const };
    setAuth(updatedUser, token, refreshToken, expiresAt);

    // Also persist provider context for the sidebar/header display
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:provider_id", provider.providerId);
      sessionStorage.setItem("exp:provider_display", provider.displayName);
      sessionStorage.setItem("exp:provider_cadre", provider.cadre);
    }

    // Navigate to the originally requested page
    router.replace(returnTo);
  }

  function handleSkip() {
    router.replace("/home");
  }

  if (!user) return null;

  return (
    <AppLayout>
      <PageShell title="Activate Provider Role" subtitle="Health OS §6 — Sign in as a person; practice as a provider only under activated Provider ID">
        <div className="max-w-xl mx-auto space-y-4">
          <ProviderRoleActivationRail compact returnTo={returnTo} />
          {/* Doctrine banner */}
          <div className="mb-6 rounded-lg border border-impilo-200 bg-impilo-50 p-4 text-sm text-impilo-800">
            <div className="flex items-center gap-2 mb-1">
              <ShieldCheck className="h-4 w-4 text-impilo-500" />
              <strong>Provider Role Activation</strong>
            </div>
            <p>
              You are signed in as <strong>{user.displayName}</strong> (Health ID: {user.id.slice(0, 8)}...).
              To perform regulated clinical work, select and activate one of your registered Provider IDs below.
            </p>
          </div>

          {loading && (
            <div className="flex items-center justify-center py-12 text-gray-500">
              <Loader2 className="h-5 w-5 animate-spin mr-2" /> Loading provider registrations...
            </div>
          )}

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
              <AlertTriangle className="h-4 w-4 inline mr-1" /> {error}
            </div>
          )}

          {!loading && !error && providers.length === 0 && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
              <p className="font-medium mb-1">No active Provider IDs found</p>
              <p>
                Your Health ID is not linked to any active provider registration.
                Contact your facility administrator or the Health Professions Authority if you believe this is an error.
              </p>
              <button onClick={handleSkip} className="mt-3 text-xs underline text-amber-700">
                Continue as non-provider →
              </button>
            </div>
          )}

          {!loading && providers.length > 0 && !activating && (
            <div className="space-y-3">
              {providers.map((p) => (
                <button
                  key={p.providerId}
                  onClick={() => handleActivate(p)}
                  className="w-full text-left rounded-lg border border-gray-200 bg-white p-4 hover:border-impilo-400 hover:ring-1 hover:ring-impilo-200 transition-all"
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-gray-900">{p.displayName}</p>
                      <p className="text-sm text-gray-600">{p.cadre} — {p.registrationNumber}</p>
                      {p.licensureExpiry && (
                        <p className="text-xs text-gray-400 mt-1">Licence valid until {p.licensureExpiry}</p>
                      )}
                    </div>
                    <ShieldCheck className="h-5 w-5 text-green-500" />
                  </div>
                </button>
              ))}
              <button onClick={handleSkip} className="block mx-auto mt-4 text-xs text-gray-400 underline">
                Skip — continue without provider role
              </button>
            </div>
          )}

          {activating && (
            <div className="flex items-center justify-center py-12 text-impilo-500">
              <Loader2 className="h-5 w-5 animate-spin mr-2" /> Activating provider context...
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
