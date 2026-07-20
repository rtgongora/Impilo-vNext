"use client";

/**
 * Provider Activation Page — Health OS §6
 *
 * "Sign in as a person; practice as a provider only under activated Provider ID."
 *
 * L2 — optional provider biometric step-up. On activation the provider may add a
 * biometric factor. VARAPI keys provider biometrics by the provider public id, so
 * the subjectRef is the providerId. Care-first outcome handling:
 *   MATCH        → activation proceeds and is marked biometric-verified.
 *   NO_MATCH     → activation is BLOCKED with an honest message (the only blocking outcome).
 *   UNAVAILABLE  → falls back to the existing activation factor; never blocks on a biometric outage.
 *   no probe     → activation behaves exactly as it did before (backward compatible).
 */

import { useState, useEffect, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ShieldCheck, Loader2, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { ProviderRoleActivationRail } from "@/components/auth/ProviderRoleActivationRail";
import { PageShell } from "@/components/PageShell";
import { BiometricVerifyField } from "@/components/biometric/BiometricVerifyField";
import type { Modality } from "@/hooks/queries/useAbisBiometric";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient } from "@/lib/api-client";
import {
  normalizeProviderListingResponse,
  recordFromLinkedProviderId,
  type ProviderActivationRecord,
} from "@/lib/provider-activation";

type BiometricProbe = { modality: Modality; probeBase64: string };

export default function ProviderActivatePage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo") ?? "/clinical";
  const { user, activateProvider } = useAuthStore();

  const [providers, setProviders] = useState<ProviderActivationRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activating, setActivating] = useState(false);
  const [probe, setProbe] = useState<BiometricProbe | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [stepUpError, setStepUpError] = useState("");

  const finaliseActivation = useCallback(
    (provider: ProviderActivationRecord, biometricVerified: boolean) => {
      setActivating(true);
      activateProvider(provider.providerId);

      if (typeof window !== "undefined") {
        sessionStorage.setItem("exp:provider_display", provider.displayName);
        sessionStorage.setItem("exp:provider_cadre", provider.cadre);
        if (biometricVerified) {
          sessionStorage.setItem("exp:provider_biometric_verified", "true");
        } else {
          sessionStorage.removeItem("exp:provider_biometric_verified");
        }
      }

      router.replace(returnTo);
    },
    [activateProvider, returnTo, router],
  );

  const runActivation = useCallback(
    async (provider: ProviderActivationRecord) => {
      if (!user || !provider.providerId || activating || verifying) return;
      setStepUpError("");

      // No biometric captured → activation behaves exactly as it did before.
      if (!probe) {
        finaliseActivation(provider, false);
        return;
      }

      // Optional step-up: verify the captured probe against this provider registration.
      setVerifying(true);
      let outcome = "UNAVAILABLE";
      try {
        const res = await apiClient.post<{ result?: string }>(
          `/internal/v1/identity/providers/${encodeURIComponent(provider.providerId)}/biometric-verify`,
          { modality: probe.modality, probeBase64: probe.probeBase64 },
        );
        outcome = (res?.result ?? "UNAVAILABLE").toUpperCase();
      } catch {
        // Care-first: a biometric outage must never block activation.
        outcome = "UNAVAILABLE";
      } finally {
        setVerifying(false);
      }

      if (outcome === "NO_MATCH") {
        // The only blocking outcome. Honest message; activation is not completed.
        setStepUpError(
          "Biometric did not match this provider registration. Activation was not completed — retry the capture, or contact your facility administrator.",
        );
        return;
      }

      // MATCH → proceed biometric-verified; UNAVAILABLE → fall back to the existing factor.
      finaliseActivation(provider, outcome === "MATCH");
    },
    [user, probe, activating, verifying, finaliseActivation],
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

  function handleSkip() {
    router.replace("/home");
  }

  if (!user) return null;

  const showActivationUi = !loading && !error && providers.length >= 1 && !activating;
  const busy = verifying;

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

          {showActivationUi && (
            <div className="space-y-3">
              {/* L2 — optional provider biometric step-up. */}
              <BiometricVerifyField
                label="Add biometric verification (optional)"
                disabled={busy}
                onProbe={(p) => {
                  setProbe(p);
                  setStepUpError("");
                }}
              />

              {stepUpError && (
                <div
                  className="rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800"
                  data-testid="provider-stepup-error"
                >
                  <AlertTriangle className="h-4 w-4 inline mr-1" /> {stepUpError}
                </div>
              )}

              {providers.length === 1 ? (
                <div className="rounded-lg border border-border bg-card p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-foreground">{providers[0]!.displayName}</p>
                      <p className="text-sm text-muted-foreground">
                        {providers[0]!.cadre} — {providers[0]!.registrationNumber}
                      </p>
                      {providers[0]!.licensureExpiry && (
                        <p className="text-xs text-muted-foreground mt-1">
                          Licence valid until {providers[0]!.licensureExpiry}
                        </p>
                      )}
                    </div>
                    <ShieldCheck className="h-5 w-5 text-green-500" />
                  </div>
                  <button
                    type="button"
                    data-testid="provider-activate-btn"
                    onClick={() => runActivation(providers[0]!)}
                    disabled={busy}
                    className="mt-4 w-full rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary/90 disabled:opacity-60"
                  >
                    {busy ? (
                      <span className="flex items-center justify-center gap-2">
                        <Loader2 className="h-4 w-4 animate-spin" /> Verifying biometric…
                      </span>
                    ) : probe ? (
                      "Verify & activate provider role"
                    ) : (
                      "Activate provider role"
                    )}
                  </button>
                </div>
              ) : (
                <div className="space-y-3">
                  {providers.map((p) => (
                    <button
                      key={p.providerId}
                      type="button"
                      data-testid={`provider-activate-${p.providerId}`}
                      onClick={() => runActivation(p)}
                      disabled={busy}
                      className="w-full text-left rounded-lg border border-border bg-card p-4 hover:border-impilo-400 hover:ring-1 hover:ring-impilo-200 transition-all disabled:opacity-60"
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
                </div>
              )}

              <button
                type="button"
                onClick={handleSkip}
                disabled={busy}
                className="block mx-auto mt-4 text-xs text-muted-foreground underline disabled:opacity-60"
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
