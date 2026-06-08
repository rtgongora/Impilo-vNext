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
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-impilo-50">
            <Shield className="h-7 w-7 text-impilo-500" />
          </div>

          <h2 className="mt-4 text-xl font-semibold text-gray-900">
            Basic Account
          </h2>
          <p className="mt-2 text-sm text-gray-500 leading-relaxed">
            You can access wellness, communities, and marketplace features.
          </p>

          <div className="mt-6 rounded-xl border border-gray-200 bg-gray-50 p-4 text-left">
            <p className="text-sm text-gray-700 leading-relaxed">
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
              className="w-full flex items-center justify-center gap-2 py-3 border-2 border-impilo-500 text-impilo-600 text-sm font-medium rounded-lg hover:bg-impilo-50 transition-colors"
            >
              Request Full Verification
              <ArrowRight className="w-4 h-4" />
            </Link>
            <button
              type="button"
              onClick={handleContinue}
              className="w-full py-3 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 transition-colors flex items-center justify-center gap-2"
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
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-amber-50">
            <Clock className="h-7 w-7 text-amber-600" />
          </div>

          <h2 className="mt-4 text-xl font-semibold text-gray-900">
            Temporary Health Access
          </h2>

          <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-left space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-gray-500 uppercase tracking-wide">
                Provisional Health ID
              </span>
              <span className="text-sm font-mono font-semibold text-amber-700">
                {tmpHealthId}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-gray-500 uppercase tracking-wide">
                Valid until
              </span>
              <span className="text-sm font-medium text-amber-700">
                {tmpExpiry}
              </span>
            </div>
          </div>

          <p className="mt-4 text-sm text-gray-500 leading-relaxed">
            Visit a facility to complete verification and receive your permanent
            Health ID.
          </p>

          <div className="mt-6 space-y-3">
            <Link
              href="/discover?type=facility"
              className="w-full flex items-center justify-center gap-2 py-3 border-2 border-amber-500 text-amber-700 text-sm font-medium rounded-lg hover:bg-amber-50 transition-colors"
            >
              <MapPin className="w-4 h-4" />
              Find a Facility
              <ArrowRight className="w-4 h-4" />
            </Link>
            <button
              type="button"
              onClick={handleContinue}
              className="w-full py-3 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 transition-colors flex items-center justify-center gap-2"
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
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-50">
          <CheckCircle2 className="h-7 w-7 text-emerald-600" />
        </div>

        <h2 className="mt-4 text-xl font-semibold text-gray-900">
          Fully Verified Account
        </h2>
        <p className="mt-2 text-sm text-gray-500 leading-relaxed">
          Your Health ID is verified. You have full access to all Impilo health
          services, records, and facilities.
        </p>

        <div className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-left">
          <p className="text-sm text-emerald-800 font-medium">
            All health services are available to you, including consultations,
            prescriptions, health records, and facility access.
          </p>
        </div>

        <div className="mt-6">
          <button
            type="button"
            onClick={handleContinue}
            className="w-full py-3 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 transition-colors flex items-center justify-center gap-2"
          >
            Continue to Impilo
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </AuthLayout>
  );
}
