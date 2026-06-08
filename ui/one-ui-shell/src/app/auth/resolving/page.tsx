"use client";

/**
 * Preparing Your Experience — Post-login identity resolution screen.
 * Route: /auth/resolving
 *
 * A lightweight loading screen shown after login while the system
 * queries linked IDs (Provider ID, Staff ID, Caregiver ID).
 *
 * Behaviour:
 * 1. Calls useLinkedIds() to discover linked professional identities
 * 2. Waits for the response (max 5 seconds)
 * 3. Updates the auth store with discovered linked IDs
 * 4. If professional IDs found, shows a brief notification before navigating
 * 5. Navigates via resolvePostLoginDestination (honors returnTo)
 */

import { useEffect, useState, useRef, useCallback } from "react";
import { useSearchParams } from "next/navigation";
import { CheckCircle2, Loader2, Stethoscope } from "lucide-react";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { useLinkedIds } from "@/hooks/queries/useLinkedIds";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { resolvePostLoginDestination } from "@/lib/resolve-post-login-destination";

const RESOLUTION_TIMEOUT_MS = 5000;
const PROFESSIONAL_NOTICE_DELAY_MS = 1500;

interface ResolverStep {
  label: string;
  status: "pending" | "active" | "done";
}

export default function ResolvingPage() {
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const { user } = useAuthStore();
  const { data, isLoading, isSuccess, isError } = useLinkedIds();
  const [hasNavigated, setHasNavigated] = useState(false);
  const [showProfessionalNotice, setShowProfessionalNotice] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const linkedAttrs = data?.data?.attributes;
  const hasProfessionalId = !!(linkedAttrs?.providerId || linkedAttrs?.staffId);

  const navigate = useCallback(() => {
    if (hasNavigated) return;
    setHasNavigated(true);

    const destination = resolvePostLoginDestination({
      user,
      linkedProviderId: linkedAttrs?.providerId,
      hasFacility: useFacilityStore.getState().hasFacility,
      returnTo,
    });

    if (destination.operationalMode) {
      useOperationalContextStore.getState().setOperationalMode(destination.operationalMode);
    }

    window.location.assign(destination.href);
  }, [hasNavigated, linkedAttrs?.providerId, returnTo, user]);

  const steps: ResolverStep[] = [
    {
      label: "Checking your identity",
      status: isSuccess || isError ? "done" : isLoading ? "active" : "pending",
    },
    {
      label: "Resolving linked profiles",
      status: isSuccess ? "done" : isLoading ? "active" : "pending",
    },
    {
      label: "Loading your preferences",
      status: isSuccess ? "done" : "pending",
    },
  ];

  useEffect(() => {
    timeoutRef.current = setTimeout(() => {
      navigate();
    }, RESOLUTION_TIMEOUT_MS);

    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, [navigate]);

  useEffect(() => {
    if (!isSuccess || hasNavigated) return;

    if (hasProfessionalId) {
      setShowProfessionalNotice(true);
      const timer = setTimeout(() => {
        navigate();
      }, PROFESSIONAL_NOTICE_DELAY_MS);
      return () => clearTimeout(timer);
    }

    navigate();
  }, [isSuccess, hasProfessionalId, hasNavigated, navigate]);

  useEffect(() => {
    if (!isError || hasNavigated) return;
    const timer = setTimeout(() => {
      navigate();
    }, 800);
    return () => clearTimeout(timer);
  }, [isError, hasNavigated, navigate]);

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-b from-gray-50 to-white px-6">
      <div className="flex flex-col items-center max-w-sm w-full">
        <ImpiloBrandLogo variant="hero" />

        <h1 className="mt-8 text-lg font-semibold text-gray-900">
          Preparing your experience...
        </h1>

        <div className="mt-6">
          <Loader2 className="h-8 w-8 text-impilo-500 animate-spin" />
        </div>

        <div className="mt-8 w-full space-y-3">
          {steps.map((step) => (
            <div
              key={step.label}
              className="flex items-center gap-3 px-4 py-2.5 rounded-lg transition-colors"
            >
              {step.status === "done" ? (
                <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
              ) : step.status === "active" ? (
                <Loader2 className="h-4 w-4 text-impilo-500 animate-spin shrink-0" />
              ) : (
                <div className="h-4 w-4 rounded-full border-2 border-gray-200 shrink-0" />
              )}
              <span
                className={[
                  "text-sm transition-colors",
                  step.status === "done"
                    ? "text-gray-900 font-medium"
                    : step.status === "active"
                      ? "text-gray-700"
                      : "text-gray-400",
                ].join(" ")}
              >
                {step.label}
              </span>
            </div>
          ))}
        </div>

        {showProfessionalNotice && (
          <div className="mt-6 w-full rounded-xl border border-impilo-200 bg-impilo-50 p-4 animate-in fade-in duration-300">
            <div className="flex items-start gap-3">
              <Stethoscope className="h-5 w-5 text-impilo-600 shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-semibold text-impilo-800">
                  Professional capability detected
                </p>
                <p className="mt-1 text-xs text-impilo-600 leading-relaxed">
                  {linkedAttrs?.providerId
                    ? `Provider ID: ${linkedAttrs.providerId}`
                    : "Staff profile linked"}
                  . You can activate professional mode from the sidebar.
                </p>
              </div>
            </div>
          </div>
        )}

        {user && (
          <p className="mt-6 text-xs text-gray-400">
            Welcome back, {user.displayName || user.email}
          </p>
        )}
      </div>

      <NompiloHint
        message="I'm checking if you have any linked professional identities, staff roles, or facility affiliations. This only takes a moment."
      />
    </div>
  );
}
