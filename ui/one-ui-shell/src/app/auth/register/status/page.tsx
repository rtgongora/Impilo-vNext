"use client";

/**
 * Impilo ID Status — Post-assurance, post-consent ID status summary.
 * Route: /auth/register/status
 *
 * Shows the user their account status based on their chosen assurance tier:
 * - Basic: access to wellness, communities, marketplace only
 * - Temporary: provisional Health ID with expiry, link to facility verification
 * - Full/Verified: full access confirmation
 */

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Shield,
  Clock,
  CheckCircle2,
  MapPin,
  ArrowRight,
  ChevronRight,
} from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { CitizenOnboardingOrchestrationRail } from "@/components/onboarding/CitizenOnboardingOrchestrationRail";
import { useAuthStore } from "@/hooks/useAuthStore";

/** Generate a deterministic temporary Health ID from the user's account ID. */
function generateTmpHealthId(userId: string): string {
  const hash = userId.replace(/[^A-Z0-9]/gi, "").toUpperCase();
  const seg1 = hash.substring(0, 4).padEnd(4, "0");
  const seg2 = hash.substring(4, 8).padEnd(4, "0");
  return `TMP-${seg1}-${seg2}`;
}

/** Calculate 90-day expiry from now. */
function getTemporaryExpiry(): string {
  const date = new Date();
  date.setDate(date.getDate() + 90);
  return date.toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

export default function IdStatusPage() {
  const router = useRouter();
  const { user } = useAuthStore();

  const assurance = user?.assuranceLevel ?? "UNVERIFIED";

  const tmpHealthId = useMemo(
    () => (user?.id ? generateTmpHealthId(user.id) : "TMP-0000-0000"),
    [user?.id],
  );
  const tmpExpiry = useMemo(() => getTemporaryExpiry(), []);

  function handleContinue() {
    router.push("/home");
  }

  // --- BASIC / UNVERIFIED ---
  if (assurance === "UNVERIFIED") {
    return (
      <AuthLayout>
        <div className="text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-soft">
            <Shield className="h-7 w-7 text-primary" />
          </div>

          <h2 className="mt-4 text-xl font-semibold text-foreground">
            Basic Account
          </h2>
          <p className="mt-2 text-sm text-muted-foreground leading-relaxed">
            You can access wellness, communities, and marketplace features.
          </p>

          <div className="mt-6 rounded-xl border border-border bg-background p-4 text-left">
            <p className="text-sm text-foreground leading-relaxed">
              To unlock full health services, visit a registered facility or
              request verification online.
            </p>
          </div>

          <div className="mt-6 text-left">
            <CitizenOnboardingOrchestrationRail />
          </div>

          <div className="mt-6 space-y-3">
            <Link
              href="/citizen/health-id/request"
              className="w-full flex items-center justify-center gap-2 py-3 border-2 border-impilo-500 text-primary text-sm font-medium rounded-lg hover:bg-primary-soft transition-colors"
            >
              Request Full Verification
              <ArrowRight className="w-4 h-4" />
            </Link>
            <button
              type="button"
              onClick={handleContinue}
              className="w-full py-3 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors flex items-center justify-center gap-2"
            >
              Continue to Impilo
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </AuthLayout>
    );
  }

  // --- TEMPORARY ---
  if (assurance === "TEMPORARY") {
    return (
      <AuthLayout>
        <div className="text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-warning-soft">
            <Clock className="h-7 w-7 text-amber-600" />
          </div>

          <h2 className="mt-4 text-xl font-semibold text-foreground">
            Temporary Health Access
          </h2>

          <div className="mt-4 rounded-xl border border-warning/35 bg-warning-soft p-4 text-left space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                Provisional Health ID
              </span>
              <span className="text-sm font-mono font-semibold text-warning-foreground">
                {tmpHealthId}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                Valid until
              </span>
              <span className="text-sm font-medium text-warning-foreground">
                {tmpExpiry}
              </span>
            </div>
          </div>

          <p className="mt-4 text-sm text-muted-foreground leading-relaxed">
            Visit a facility to complete verification and receive your permanent
            Health ID.
          </p>

          <div className="mt-6 space-y-3">
            <Link
              href="/discover?type=facility"
              className="w-full flex items-center justify-center gap-2 py-3 border-2 border-amber-500 text-warning-foreground text-sm font-medium rounded-lg hover:bg-warning-soft transition-colors"
            >
              <MapPin className="w-4 h-4" />
              Find a Facility
              <ArrowRight className="w-4 h-4" />
            </Link>
            <button
              type="button"
              onClick={handleContinue}
              className="w-full py-3 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors flex items-center justify-center gap-2"
            >
              Continue to Impilo
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </AuthLayout>
    );
  }

  // --- VERIFIED / FULL ---
  return (
    <AuthLayout>
      <div className="text-center">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-success-soft">
          <CheckCircle2 className="h-7 w-7 text-primary" />
        </div>

        <h2 className="mt-4 text-xl font-semibold text-foreground">
          Fully Verified Account
        </h2>
        <p className="mt-2 text-sm text-muted-foreground leading-relaxed">
          Your Health ID is verified. You have full access to all Impilo health
          services, records, and facilities.
        </p>

        <div className="mt-6 rounded-xl border border-success/25 bg-success-soft p-4 text-left">
          <p className="text-sm text-primary-hover font-medium">
            All health services are available to you, including consultations,
            prescriptions, health records, and facility access.
          </p>
        </div>

        <div className="mt-6">
          <button
            type="button"
            onClick={handleContinue}
            className="w-full py-3 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors flex items-center justify-center gap-2"
          >
            Continue to Impilo
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </AuthLayout>
  );
}
