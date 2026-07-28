"use client";

/**
 * Preparing Your Experience — Post-login identity resolution screen.
 * Route: /auth/resolving
 */

import { useEffect, useState, useRef, useCallback } from "react";
import { useSearchParams } from "next/navigation";
import { CheckCircle2, Loader2, Stethoscope } from "lucide-react";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { useAffiliations } from "@/hooks/queries/useAffiliations";
import { useWorkAssignments } from "@/hooks/queries/useWorkAssignments";
import { useLinkedIds } from "@/hooks/queries/useLinkedIds";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { sessionContractAllowsRoute } from "@/lib/trust";
import { isSafeReturnTo, resolvePostLoginDestination } from "@/lib/resolve-post-login-destination";
import { consumeIntent, peekIntent, resolveIntentDestination } from "@/lib/gateway-intent";

// Phase F10: two-phase deadline, each armed EXACTLY ONCE on mount (see the empty-dep effect
// below). The previous single 5s timer was armed in a useEffect keyed on `navigate` — a
// useCallback recreated every time contract/linkedIds/affiliations/user changed, which
// re-triggered the effect and reset the timer on every dependency change. Real logins take
// several async round-trips (identity, affiliations, work assignments, the session contract),
// each of which recreates `navigate` — so the "5-second fallback" almost never actually fired
// 5 seconds after mount; it fired 5 seconds after whichever dependency happened to settle last,
// making the deadline nondeterministic and, on a slow network, effectively unbounded.
const SOFT_DEADLINE_MS = 8000;
const HARD_DEADLINE_MS = 12000;
const PROFESSIONAL_NOTICE_DELAY_MS = 1200;

interface ResolverStep {
  label: string;
  status: "pending" | "active" | "done";
}

export default function ResolvingPage() {
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const resolutionReason = searchParams.get("reason");
  const { user, activateProvider } = useAuthStore();
  const { data, isLoading, isSuccess, isError } = useLinkedIds();
  const { data: affiliations = [], isSuccess: affSuccess } = useAffiliations();
  const { data: workAssignments = [], isSuccess: assignSuccess } = useWorkAssignments();
  const { contract } = useSessionExperienceContract();
  const [hasNavigated, setHasNavigated] = useState(false);
  const [showProfessionalNotice, setShowProfessionalNotice] = useState(false);

  const linkedAttrs = data?.data?.attributes;
  const hasProfessionalId = !!(linkedAttrs?.providerId || linkedAttrs?.staffId);
  const loginMethod = user?.loginMethod;

  const navigate = useCallback(() => {
    if (hasNavigated) return;
    setHasNavigated(true);

    // Gateway intent (doctrine §4.1 law 3): journey context captured on a public
    // surface survives authentication. A valid intent destination outranks returnTo
    // in both routing branches below; it is consumed exactly when navigation restores it.
    const pendingIntent = peekIntent();
    const intentDest = resolveIntentDestination(pendingIntent);

    // Contract-authoritative routing: the BFF Session Experience Contract is the single
    // source of truth for the post-login landing (tabs/defaultRoute). The client identity
    // resolver below is only a fallback when the contract is unavailable (offline/dev).
    if (contract?.authenticated && contract.defaultRoute) {
      const intentBlockedAfterResolution =
        resolutionReason === "no_work" && !!intentDest && intentDest.startsWith("/work/");
      if (intentDest && !intentBlockedAfterResolution && sessionContractAllowsRoute(contract, intentDest)) {
        consumeIntent();
        window.location.assign(intentDest);
        return;
      }
      const blockedAfterResolution =
        resolutionReason === "no_work" && !!returnTo && returnTo.startsWith("/work/");
      const href =
        isSafeReturnTo(returnTo) &&
        !blockedAfterResolution &&
        sessionContractAllowsRoute(contract, returnTo)
          ? returnTo
          : contract.defaultRoute;
      window.location.assign(href);
      return;
    }

    const destination = resolvePostLoginDestination({
      user,
      linkedIds: linkedAttrs,
      affiliations,
      workAssignments,
      loginMethod,
      hasFacility: useFacilityStore.getState().hasFacility,
      returnTo,
      resolutionReason,
      intent: pendingIntent,
    });

    if (destination.restoredIntent) {
      consumeIntent();
    }

    if (destination.autoActivateProvider && destination.linkedProviderId) {
      activateProvider(destination.linkedProviderId);
    }

    if (destination.operationalMode) {
      useOperationalContextStore.getState().setOperationalMode(destination.operationalMode);
    }

    window.location.assign(destination.href);
  }, [
    hasNavigated,
    contract,
    linkedAttrs,
    affiliations,
    workAssignments,
    loginMethod,
    returnTo,
    resolutionReason,
    user,
    activateProvider,
  ]);

  const identityResolved = isSuccess || isError;
  const affiliationsResolved = affSuccess || isError;
  const assignmentsResolved = assignSuccess || isError || workAssignments.length >= 0;
  // Prefer to wait for the BFF contract so contract.defaultRoute drives the landing;
  // the SOFT_DEADLINE_MS/HARD_DEADLINE_MS fallback still navigates if it never arrives.
  const contractResolved = !!contract || isError;

  const steps: ResolverStep[] = [
    {
      label: "Checking your identity",
      status: identityResolved ? "done" : isLoading ? "active" : "pending",
    },
    {
      label: "Resolving linked profiles",
      status: identityResolved && affiliationsResolved && assignmentsResolved ? "done" : isLoading ? "active" : "pending",
    },
    {
      label: "Loading your preferences",
      status: identityResolved && affiliationsResolved ? "done" : "pending",
    },
  ];

  // navigateRef always holds the LATEST navigate closure without the timer-arming effect below
  // depending on it — that dependency is exactly what made the old single timer reset on every
  // identity/affiliations/assignments/contract update instead of firing a fixed time after mount.
  const navigateRef = useRef(navigate);
  useEffect(() => {
    navigateRef.current = navigate;
  }, [navigate]);

  // Armed exactly once (empty deps): a soft deadline that accepts a client-side fallback
  // landing if the contract hasn't arrived by then, and a hard ceiling that fires regardless —
  // a genuine second, independent safety net, not merely a later copy of the first. Both call
  // through the ref, and `navigate()` itself is idempotent (`if (hasNavigated) return`), so
  // whichever fires first — a normal data-resolved landing or either deadline — wins cleanly.
  useEffect(() => {
    const softTimer = setTimeout(() => navigateRef.current(), SOFT_DEADLINE_MS);
    const hardTimer = setTimeout(() => navigateRef.current(), HARD_DEADLINE_MS);
    return () => {
      clearTimeout(softTimer);
      clearTimeout(hardTimer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- deliberately armed once on mount, see comment above SOFT_DEADLINE_MS
  }, []);

  useEffect(() => {
    if (!identityResolved || !affiliationsResolved || !assignmentsResolved || !contractResolved || hasNavigated) return;

    if (hasProfessionalId && loginMethod !== "provider_id") {
      setShowProfessionalNotice(true);
      const timer = setTimeout(() => {
        navigate();
      }, PROFESSIONAL_NOTICE_DELAY_MS);
      return () => clearTimeout(timer);
    }

    navigate();
  }, [identityResolved, affiliationsResolved, assignmentsResolved, contractResolved, hasProfessionalId, loginMethod, hasNavigated, navigate]);

  useEffect(() => {
    if (!isError || hasNavigated) return;
    const timer = setTimeout(() => {
      navigate();
    }, 800);
    return () => clearTimeout(timer);
  }, [isError, hasNavigated, navigate]);

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-b from-gray-50 to-card px-6">
      <div className="flex flex-col items-center max-w-sm w-full">
        <ImpiloBrandLogo variant="hero" />

        <h1 className="mt-8 text-lg font-semibold text-foreground">
          Preparing your experience...
        </h1>

        <div className="mt-6">
          <Loader2 className="h-8 w-8 text-primary animate-spin" />
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
                <Loader2 className="h-4 w-4 text-primary animate-spin shrink-0" />
              ) : (
                <div className="h-4 w-4 rounded-full border-2 border-border shrink-0" />
              )}
              <span
                className={[
                  "text-sm transition-colors",
                  step.status === "done"
                    ? "text-foreground font-medium"
                    : step.status === "active"
                      ? "text-foreground"
                      : "text-muted-foreground",
                ].join(" ")}
              >
                {step.label}
              </span>
            </div>
          ))}
        </div>

        {showProfessionalNotice && (
          <div className="mt-6 w-full rounded-xl border border-primary/25 bg-primary-soft p-4 animate-in fade-in duration-300">
            <div className="flex items-start gap-3">
              <Stethoscope className="h-5 w-5 text-primary shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-semibold text-impilo-800">
                  Professional capability detected
                </p>
                <p className="mt-1 text-xs text-primary leading-relaxed">
                  {linkedAttrs?.providerId
                    ? `Provider ID linked: ${linkedAttrs.providerId}`
                    : "Staff profile linked"}
                  . Activate when you are ready to work.
                </p>
              </div>
            </div>
          </div>
        )}

        {user && (
          <p className="mt-6 text-xs text-muted-foreground">
            Welcome back, {user.displayName || user.email}
          </p>
        )}
      </div>

      <NompiloHint
        message="I'm checking your identity, linked Provider ID, facility assignments, and what you're allowed to do before opening your workspace."
      />
    </div>
  );
}
