"use client";

/**
 * Identity Assurance Choice — Health OS Identity Doctrine
 * Route: /auth/register/assurance
 *
 * After account creation, the user selects their identity tier:
 * - Basic Access: wellness, communities, marketplace (no Health ID)
 * - Temporary Health Access: provisional Health ID, 90-day validity
 * - Full Activation: in-person verification, full health services
 *
 * Sets assurance level in the auth store and navigates accordingly.
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Shield, Clock, CheckCircle2, ChevronRight, Loader2 } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRequestAssuranceUpgrade, type AssuranceUpgradeTier } from "@/hooks/queries/useIdentityAssurance";

export default function AssuranceChoicePage() {
  const router = useRouter();
  const { user, setAuth, token, refreshToken, expiresAt } = useAuthStore();
  const requestUpgrade = useRequestAssuranceUpgrade();

  const [selected, setSelected] = useState<AssuranceUpgradeTier | null>(null);
  const [upgradeError, setUpgradeError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function handleSelect(tier: AssuranceUpgradeTier) {
    setSelected(tier);
  }

  async function handleContinue() {
    if (!selected || !user || !token) return;
    setSubmitting(true);

    try {
      setUpgradeError(null);
      const assuranceLevel =
        selected === "BASIC" ? "UNVERIFIED" as const
        : selected === "TEMPORARY" ? "TEMPORARY" as const
        : "VERIFIED" as const;

      await requestUpgrade.mutateAsync(selected);

      setAuth(
        { ...user, assuranceLevel },
        token,
        refreshToken,
        expiresAt,
      );

      router.push("/consent?returnTo=/auth/register/status");
    } catch {
      setUpgradeError("Could not record your assurance choice. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  const tiers = [
    {
      id: "BASIC" as AssuranceUpgradeTier,
      icon: Shield,
      title: "Basic Access",
      description:
        "Access wellness, communities, marketplace, and public health content.",
      detail: "No Impilo ID required.",
      color: "impilo",
      borderColor: "border-primary/25",
      bgColor: "bg-primary-soft",
      iconColor: "text-primary",
      selectedBorder: "border-impilo-500 ring-2 ring-impilo-200",
    },
    {
      id: "TEMPORARY" as AssuranceUpgradeTier,
      icon: Clock,
      title: "Temporary Health Access",
      description:
        "Get a provisional Impilo ID for remote consultations, prescription collection, and basic health records.",
      detail: "Valid for 90 days — visit a facility to complete verification.",
      color: "amber",
      borderColor: "border-warning/35",
      bgColor: "bg-warning-soft",
      iconColor: "text-amber-600",
      selectedBorder: "border-amber-500 ring-2 ring-amber-200",
    },
    {
      id: "FULL" as AssuranceUpgradeTier,
      icon: CheckCircle2,
      title: "Full Activation",
      description:
        "I have an Impilo ID or will complete in-person verification now.",
      detail: "Full access to all health services.",
      color: "emerald",
      borderColor: "border-success/25",
      bgColor: "bg-success-soft",
      iconColor: "text-primary",
      selectedBorder: "border-emerald-500 ring-2 ring-emerald-200",
    },
  ];

  return (
    <AuthLayout>
      <h2 className="text-xl font-semibold text-foreground mb-1">
        How would you like to get started?
      </h2>
      <p className="text-sm text-muted-foreground mb-6">
        Choose the level of access that suits you. You can upgrade at any time.
      </p>

      {upgradeError ? (
        <div className="mb-4 rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-danger">
          {upgradeError}
        </div>
      ) : null}

      <div className="space-y-3">
        {tiers.map((tier) => {
          const Icon = tier.icon;
          const isSelected = selected === tier.id;

          return (
            <button
              key={tier.id}
              type="button"
              onClick={() => handleSelect(tier.id)}
              className={[
                "w-full text-left rounded-xl border-2 p-4 transition-all duration-150",
                isSelected ? tier.selectedBorder : `${tier.borderColor} hover:shadow-md`,
                tier.bgColor,
              ].join(" ")}
            >
              <div className="flex items-start gap-3">
                <div
                  className={[
                    "flex h-10 w-10 shrink-0 items-center justify-center rounded-lg",
                    isSelected ? "bg-card shadow-sm" : "bg-card/80",
                  ].join(" ")}
                >
                  <Icon className={`h-5 w-5 ${tier.iconColor}`} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-foreground">
                    {tier.title}
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground leading-relaxed">
                    {tier.description}
                  </p>
                  <p className="mt-1 text-xs font-medium text-muted-foreground">
                    {tier.detail}
                  </p>
                </div>
                {isSelected && (
                  <CheckCircle2 className={`h-5 w-5 shrink-0 mt-0.5 ${tier.iconColor}`} />
                )}
              </div>
            </button>
          );
        })}
      </div>

      {/* Temporary access — verification happens at a facility (G-CZO-13). We don't collect
          identity details here that we can't yet verify or persist; the provisional Health ID is
          confirmed in person, where DOB + National ID are captured and deduplicated via Vito. */}
      {selected === "TEMPORARY" && (
        <div className="mt-4 rounded-xl border border-warning/35 bg-warning-soft/50 p-4">
          <p className="text-sm font-medium text-foreground">Verification completes at a facility</p>
          <p className="mt-1 text-xs text-muted-foreground leading-relaxed">
            Your provisional Impilo ID works for 90 days. Bring your National ID to any health facility
            to confirm your identity and unlock full health services — your details are captured and
            checked there.
          </p>
        </div>
      )}

      <button
        type="button"
        onClick={handleContinue}
        disabled={!selected || submitting}
        className="mt-6 w-full py-3 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
      >
        {submitting ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            Processing...
          </>
        ) : (
          <>
            Continue
            <ChevronRight className="w-4 h-4" />
          </>
        )}
      </button>

      <NompiloHint
        message="Choose how much access you need right now. You can always upgrade later by visiting a health facility or completing verification online."
        suggestions={["Basic access is fine for wellness, communities, and marketplace", "Temporary access lets you start using health services immediately"]}
      />
    </AuthLayout>
  );
}
