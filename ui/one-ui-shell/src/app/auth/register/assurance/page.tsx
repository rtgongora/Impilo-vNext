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
import { Shield, Clock, CheckCircle2, ChevronRight, Loader2, Calendar, IdCard } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { useAuthStore } from "@/hooks/useAuthStore";

type AssuranceTier = "BASIC" | "TEMPORARY" | "FULL";

export default function AssuranceChoicePage() {
  const router = useRouter();
  const { user, setAuth, token, refreshToken, expiresAt } = useAuthStore();

  const [selected, setSelected] = useState<AssuranceTier | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Temporary Health Access — extra fields
  const [showTemporaryForm, setShowTemporaryForm] = useState(false);
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [idNumber, setIdNumber] = useState("");

  function handleSelect(tier: AssuranceTier) {
    setSelected(tier);
    setShowTemporaryForm(tier === "TEMPORARY");
  }

  async function handleContinue() {
    if (!selected || !user || !token) return;
    setSubmitting(true);

    try {
      const assuranceLevel =
        selected === "BASIC" ? "UNVERIFIED" as const
        : selected === "TEMPORARY" ? "TEMPORARY" as const
        : "VERIFIED" as const;

      // Update the auth store with the chosen assurance level
      setAuth(
        { ...user, assuranceLevel },
        token,
        refreshToken,
        expiresAt,
      );

      // All tiers go through consent first
      router.push("/consent");
    } finally {
      setSubmitting(false);
    }
  }

  const tiers = [
    {
      id: "BASIC" as AssuranceTier,
      icon: Shield,
      title: "Basic Access",
      description:
        "Access wellness, communities, marketplace, and public health content.",
      detail: "No Health ID required.",
      color: "impilo",
      borderColor: "border-impilo-200",
      bgColor: "bg-impilo-50",
      iconColor: "text-impilo-500",
      selectedBorder: "border-impilo-500 ring-2 ring-impilo-200",
    },
    {
      id: "TEMPORARY" as AssuranceTier,
      icon: Clock,
      title: "Temporary Health Access",
      description:
        "Get a provisional Health ID for remote consultations, prescription collection, and basic health records.",
      detail: "Valid for 90 days — visit a facility to complete verification.",
      color: "amber",
      borderColor: "border-amber-200",
      bgColor: "bg-amber-50",
      iconColor: "text-amber-600",
      selectedBorder: "border-amber-500 ring-2 ring-amber-200",
    },
    {
      id: "FULL" as AssuranceTier,
      icon: CheckCircle2,
      title: "Full Activation",
      description:
        "I have a Health ID or will complete in-person verification now.",
      detail: "Full access to all health services.",
      color: "emerald",
      borderColor: "border-emerald-200",
      bgColor: "bg-emerald-50",
      iconColor: "text-emerald-600",
      selectedBorder: "border-emerald-500 ring-2 ring-emerald-200",
    },
  ];

  return (
    <AuthLayout>
      <h2 className="text-xl font-semibold text-gray-900 mb-1">
        How would you like to get started?
      </h2>
      <p className="text-sm text-gray-500 mb-6">
        Choose the level of access that suits you. You can upgrade at any time.
      </p>

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
                    isSelected ? "bg-white shadow-sm" : "bg-white/80",
                  ].join(" ")}
                >
                  <Icon className={`h-5 w-5 ${tier.iconColor}`} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-gray-900">
                    {tier.title}
                  </p>
                  <p className="mt-1 text-xs text-gray-600 leading-relaxed">
                    {tier.description}
                  </p>
                  <p className="mt-1 text-xs font-medium text-gray-500">
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

      {/* Temporary access — extra info collection */}
      {showTemporaryForm && selected === "TEMPORARY" && (
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50/50 p-4 space-y-3">
          <p className="text-sm font-medium text-gray-700">
            We need a few more details for your provisional Health ID
          </p>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Date of birth
            </label>
            <div className="relative">
              <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                type="date"
                value={dateOfBirth}
                onChange={(e) => setDateOfBirth(e.target.value)}
                className="w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              National ID number
            </label>
            <div className="relative">
              <IdCard className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                type="text"
                value={idNumber}
                onChange={(e) => setIdNumber(e.target.value)}
                placeholder="e.g. 63-123456-A-78"
                className="w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
              />
            </div>
          </div>
        </div>
      )}

      <button
        type="button"
        onClick={handleContinue}
        disabled={
          !selected ||
          submitting ||
          (selected === "TEMPORARY" && (!dateOfBirth || !idNumber))
        }
        className="mt-6 w-full py-3 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
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
