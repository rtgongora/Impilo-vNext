"use client";

/**
 * Work re-entry — where a facility-guarded route sends someone with no duty session.
 *
 * Route: /work/resume. Guard is "auth", NOT "facility" — a facility guard here would redirect
 * to itself forever.
 *
 * Replaces the old destination for that guard, `/facility`, which listed the national facility
 * registry and asked a clinician to find their own hospital in it, three pickers deep, every
 * single sign-in. The BFF has known the answer the whole time; this surface offers it back as
 * one tap and mints a real duty token on the way through.
 *
 * Resuming is never the only option. Someone may have signed in to check their own record, do
 * some learning, or work somewhere other than where they were yesterday — so "not working
 * right now" is a first-class exit, not a dead end.
 */

import { useMemo } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowRight, Building2, GraduationCap, Heart, Loader2, MapPin, Stethoscope } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { useSwitchWorkContext } from "@/hooks/queries/useSwitchWorkContext";
import { WorkHomeWorkplacePicker } from "@/components/work-home/WorkHomeWorkplacePicker";
import { buildResumeOffer } from "@/lib/work-home/resume-context";
import { describeWorkContextRestrictions, describeWorkMode } from "@/lib/work-home/restriction-copy";
import { isSafeReturnTo } from "@/lib/resolve-post-login-destination";
import { useState } from "react";

/**
 * Where "I'm not working right now" can go.
 *
 * Every href is verified registered in `routes.ts` with `guard: "auth"` — an unregistered path
 * now renders the deny-by-default surface, and a role-guarded one would refuse them. An escape
 * hatch that dead-ends is worse than none. `audience-reachable-links` covers this file.
 */
const OTHER_WAYS = [
  { href: "/work", label: "Work Home", detail: "Everything waiting for you across your workplaces", icon: Building2 },
  { href: "/home", label: "My Impilo", detail: "Your own health record, appointments and coverage", icon: Heart },
  { href: "/home/credentials", label: "Credentials & CPD", detail: "Licence, registration and CPD standing", icon: Stethoscope },
  { href: "/learning", label: "Impilo Fundo", detail: "Courses and continuing education", icon: GraduationCap },
];

export default function WorkResumePage() {
  const router = useRouter();
  const params = useSearchParams();
  const rawReturnTo = params?.get("returnTo") ?? null;
  const returnTo = isSafeReturnTo(rawReturnTo) ? rawReturnTo : null;

  const { contract, isLoading } = useSessionExperienceContract();
  const switchWorkContext = useSwitchWorkContext();
  const [resuming, setResuming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const offer = useMemo(
    () =>
      buildResumeOffer({
        contexts: contract?.resolvedWorkContexts,
        recommendedContextId: contract?.recommendedContextId,
        sourceStatuses: contract?.workContextSourceStatuses,
        isLoading,
      }),
    [contract, isLoading],
  );

  // Land back on whatever they were trying to reach. That is the whole point of the returnTo:
  // the guard interrupted a journey, and minting should resume it rather than dumping them on
  // a landing page to navigate back by hand.
  const destination = returnTo ?? "/work";

  async function resume() {
    if (!offer.recommended || !offer.recommendedMode || resuming) return;
    setResuming(true);
    setError(null);
    try {
      await switchWorkContext(offer.recommended, offer.recommendedMode, destination);
    } catch {
      setError("Could not start your work session. Try again, or choose a different workplace below.");
      setResuming(false);
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Ready to start work?"
        subtitle={returnTo ? "Confirm where you're working and we'll take you straight there." : undefined}
        density="compact"
      >
        {offer.state === "LOADING" && (
          <div className="flex items-center gap-2 py-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" aria-hidden /> Finding your workplaces…
          </div>
        )}

        {offer.state === "DEGRADED" && (
          <div className="rounded-lg border border-warning/40 bg-warning-soft p-5" data-testid="resume-degraded">
            <p className="text-sm font-medium text-foreground">We couldn&apos;t reach your assignment records</p>
            <p className="mt-1 text-sm text-muted-foreground">
              This is a system problem, not a statement about your postings — {offer.degradedSources.join(", ")}{" "}
              {offer.degradedSources.length === 1 ? "did not respond" : "did not respond"}. Your workplaces may
              still be there. Try again shortly.
            </p>
            <div className="mt-3 flex flex-wrap gap-3 text-sm">
              <button type="button" onClick={() => router.refresh()} className="font-medium text-primary hover:underline">
                Try again
              </button>
              <Link href="/facility" className="font-medium text-primary hover:underline">
                Choose a facility manually
              </Link>
            </div>
          </div>
        )}

        {offer.state === "EMPTY" && (
          <div className="rounded-lg border border-border bg-card p-5" data-testid="resume-empty">
            <p className="text-sm font-medium text-foreground">No work assignment found</p>
            <p className="mt-1 text-sm text-muted-foreground">
              You don&apos;t currently hold an active work assignment, appointment or support posting. If that
              seems wrong, your facility or organisation administrator can add one.
            </p>
            <Link href="/facility" className="mt-3 inline-block text-sm font-medium text-primary hover:underline">
              Choose a facility manually
            </Link>
          </div>
        )}

        {error && (
          <p className="mb-4 rounded-md border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger" role="alert">
            {error}
          </p>
        )}

        {offer.state === "RESUMABLE" && offer.recommended && (
          <section className="mb-8" data-testid="resume-card">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Where you were working
            </p>
            <div className="rounded-xl border border-primary/40 bg-card p-5 shadow-sm ring-1 ring-primary/10">
              <div className="flex items-start gap-3">
                <MapPin className="mt-0.5 h-5 w-5 shrink-0 text-primary" aria-hidden />
                <div className="min-w-0">
                  <p className="font-semibold text-foreground">{offer.recommended.label}</p>
                  {offer.recommendedMode && (
                    <p className="text-sm text-muted-foreground">{describeWorkMode(offer.recommendedMode)}</p>
                  )}
                  {describeWorkContextRestrictions(offer.recommended.restrictions).map((line) => (
                    <p key={line} className="mt-1 text-xs text-amber-700 dark:text-amber-400">{line}</p>
                  ))}
                </div>
              </div>

              {offer.recommendedMode ? (
                <button
                  type="button"
                  onClick={resume}
                  disabled={resuming}
                  data-testid="resume-button"
                  className="mt-4 flex w-full items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60 sm:w-auto"
                >
                  {resuming ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
                  Resume here <ArrowRight className="h-4 w-4" aria-hidden />
                </button>
              ) : (
                // Several modes available: which authority they want is a real question, not
                // something to guess. Fall through to the picker's explicit mode choice.
                <p className="mt-4 text-sm text-muted-foreground">
                  This workplace grants more than one kind of work — choose below so the right authority is
                  issued.
                </p>
              )}
              <p className="mt-2 text-xs text-muted-foreground">
                Starting work issues a duty session for this place and mode. You can switch at any time.
              </p>
            </div>
          </section>
        )}

        {(offer.state === "CHOOSE" || offer.state === "RESUMABLE") && (
          <section className="mb-8">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              {offer.state === "RESUMABLE" ? "Somewhere else" : "Choose where you're working"}
            </p>
            <WorkHomeWorkplacePicker
              contexts={offer.state === "RESUMABLE" && offer.recommendedMode ? offer.others : contract?.resolvedWorkContexts ?? []}
              onSelect={(ctx, mode) => switchWorkContext(ctx, mode, destination)}
            />
          </section>
        )}

        {/* Always present. Signing in is not a commitment to start a shift. */}
        <section data-testid="resume-other-ways">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Not working right now?
          </p>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {OTHER_WAYS.map(({ href, label, detail, icon: Icon }) => (
              <Link
                key={href}
                href={href}
                className="flex items-start gap-3 rounded-lg border border-border bg-card p-3 text-sm transition hover:border-primary"
              >
                <Icon className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                <span>
                  <span className="block font-medium text-foreground">{label}</span>
                  <span className="block text-xs text-muted-foreground">{detail}</span>
                </span>
              </Link>
            ))}
          </div>
        </section>
      </PageShell>
    </AppLayout>
  );
}
