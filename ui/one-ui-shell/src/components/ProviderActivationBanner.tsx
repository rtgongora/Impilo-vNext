"use client";

/**
 * Provider Activation Banner — Health OS §6
 *
 * Shown when the user has a linked Provider ID but has not yet
 * opted into professional mode. This is a helpful notification,
 * not a gate — the user can dismiss it and stay in citizen mode.
 */

import { ShieldCheck } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useQueryClient } from "@tanstack/react-query";
import { useLinkedIds } from "@/hooks/queries/useLinkedIds";

function ProviderActivationBannerInner() {
  const { user, activateProvider } = useAuthStore();
  const { data } = useLinkedIds();

  const linkedIds = data?.data?.attributes;
  const hasProviderId = !!linkedIds?.providerId;
  const isActivated = user?.providerActivated;

  // Deliberately no `isError` branch (experience-bff empty-200 honesty sweep).
  // /internal/v1/identity/linked-ids now returns 502 rather than an empty 200, and on that path
  // this banner stays hidden. That is an absent invitation, not a fabricated finding: nothing is
  // rendered, so no reader can mistake it for "you have no Provider ID". The regulatory claim
  // this banner would otherwise imply is made on /professional, which does show an explicit
  // unavailable state. Activation remains reachable there, so a silent fallback loses a prompt,
  // not a fact.
  if (!hasProviderId || isActivated) return null;

  return (
    <div className="border-b border-white/10 px-4 py-3">
      <div className="rounded-2xl border border-impilo-400/30 bg-primary/10 p-4">
        <div className="flex items-start gap-3">
          <ShieldCheck className="h-5 w-5 text-impilo-400 shrink-0 mt-0.5" />
          <div className="min-w-0">
            <p className="text-sm font-medium text-white">
              Your Provider ID has been linked to your account
            </p>
            <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
              Activate your professional profile to access clinical tools,
              prescribing, and patient records.
            </p>
            {linkedIds?.licenceValid === false && (
              <p className="text-xs text-amber-300 mt-1">
                Note: Your licence status may need updating before clinical access is granted.
              </p>
            )}
            <button
              onClick={() => activateProvider(linkedIds?.providerId)}
              className="mt-3 px-4 py-2 text-xs font-medium text-white bg-primary rounded-lg hover:bg-primary-hover transition-colors"
            >
              Activate Professional Profile
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export function ProviderActivationBanner() {
  // Guard: only render if QueryClientProvider is available
  try {
    useQueryClient();
  } catch {
    return null;
  }
  return <ProviderActivationBannerInner />;
}
