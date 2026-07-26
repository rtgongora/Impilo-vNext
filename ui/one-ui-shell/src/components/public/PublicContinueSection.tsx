"use client";

/**
 * "Continue your journey" on the signed-out landing.
 *
 * The concept for this section lists appointments, referrals, prescriptions, results
 * and messages. None of those exist for a signed-out visitor, and rendering headings
 * for them would promise a feed that cannot be filled. What IS real is the locally
 * persisted care search (no PII) and the opt-in masked return hint — so the section
 * shows those and, when neither exists, renders nothing at all rather than an empty
 * shell that implies the person has lost something.
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { useFindCareJourneyStore } from "@/hooks/useFindCareJourneyStore";
import { readReturnHint } from "@/lib/return-hint";
import { ReturningUserCard } from "./ReturningUserCard";
import { ResumeJourneyCard } from "./ResumeJourneyCard";

export function PublicContinueSection() {
  const hydrate = useFindCareJourneyStore((s) => s.hydrate);
  const hydrated = useFindCareJourneyStore((s) => s.hydrated);
  const need = useFindCareJourneyStore((s) => s.need);
  const serviceLabel = useFindCareJourneyStore((s) => s.serviceLabel);
  const [hasHint, setHasHint] = useState(false);

  useEffect(() => {
    hydrate();
    setHasHint(!!readReturnHint());
  }, [hydrate]);

  const hasJourney = hydrated && !!(serviceLabel || need);
  if (!hasJourney && !hasHint) return null;

  return (
    <section className="mt-14" aria-labelledby="continue-title">
      <div className="max-w-3xl">
        <p className="text-sm font-semibold uppercase tracking-[0.08em] text-emerald-700">
          Pick up where you were
        </p>
        <h2 id="continue-title" className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
          Continue your journey
        </h2>
        <p className="mt-3 text-base leading-7 text-slate-600">
          Appointments, referrals, prescriptions, results and messages live in My Impilo and need
          you to sign in. What we kept on this device is below.
        </p>
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        {hasJourney && (
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <ResumeJourneyCard tone="light" />
          </div>
        )}
        {hasHint && (
          <div className="rounded-2xl border border-emerald-900/10 bg-gradient-to-br from-emerald-950 to-teal-900 p-5">
            <ReturningUserCard />
          </div>
        )}
      </div>

      <Link
        href="/auth/login?returnTo=%2Fhome"
        className="mt-4 inline-flex min-h-11 items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-800 hover:border-emerald-300 hover:bg-emerald-50"
      >
        Open My Impilo
        <ArrowRight className="h-4 w-4" aria-hidden />
      </Link>
    </section>
  );
}
