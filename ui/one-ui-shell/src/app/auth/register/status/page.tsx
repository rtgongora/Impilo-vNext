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

import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Shield,
  Clock,
  CheckCircle2,
  MapPin,
  ArrowRight,
  ChevronRight,
  Loader2,
} from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { CitizenOnboardingOrchestrationRail } from "@/components/onboarding/CitizenOnboardingOrchestrationRail";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useProvisionalHealthId } from "@/hooks/queries/useIdentity";

/** Format a server-issued ISO expiry for display, or null if absent/invalid. */
function formatExpiry(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
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

  // Real provisional Health ID from VITO (via the BFF). Only requested for the TEMPORARY
  // tier; we render exactly what the server returns — a real id + server expiry, or an
  // honest PENDING state. Never a fabricated id.
  const provisional = useProvisionalHealthId(assurance === "TEMPORARY");
  const provisionalData = provisional.data?.data;
  const provisionalId = provisionalData?.provisionalHealthId ?? null;
  const provisionalExpiry = formatExpiry(provisionalData?.expiresAt);
  const isPending =
    provisional.isLoading ||
    provisional.isError ||
    provisionalData?.status === "PENDING" ||
    !provisionalId;

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

          {isPending ? (
            /* Honest PENDING state — the id is still being issued (or could not be
               retrieved). We never show a fabricated id or a made-up expiry. */
            <div className="mt-4 rounded-xl border border-warning/35 bg-warning-soft p-4 text-left">
              <div className="flex items-center gap-2">
                {provisional.isLoading ? (
                  <Loader2 className="h-4 w-4 animate-spin text-amber-600" aria-hidden />
                ) : (
                  <Clock className="h-4 w-4 text-amber-600" aria-hidden />
                )}
                <span className="text-sm font-medium text-warning-foreground">
                  {provisional.isLoading
                    ? "Issuing your provisional Impilo ID…"
                    : "Your provisional Impilo ID is being prepared"}
                </span>
              </div>
              <p className="mt-2 text-xs text-muted-foreground leading-relaxed">
                {provisional.isError
                  ? "We could not retrieve your provisional Impilo ID just now. It will appear here once it is ready — you can continue and check again shortly."
                  : "This usually only takes a moment. You can continue to Impilo and your provisional Impilo ID will be available from your profile once issued."}
              </p>
            </div>
          ) : (
            <div className="mt-4 rounded-xl border border-warning/35 bg-warning-soft p-4 text-left space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                  Provisional Impilo ID
                </span>
                <span className="text-sm font-mono font-semibold text-warning-foreground">
                  {provisionalId}
                </span>
              </div>
              {provisionalExpiry ? (
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                    Valid until
                  </span>
                  <span className="text-sm font-medium text-warning-foreground">
                    {provisionalExpiry}
                  </span>
                </div>
              ) : null}
            </div>
          )}

          <p className="mt-4 text-sm text-muted-foreground leading-relaxed">
            Visit a facility to complete verification and receive your permanent
            Impilo ID.
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
          Your Impilo ID is verified. You have full access to all Impilo health
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
