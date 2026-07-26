"use client";

/**
 * "Continue where you left off" — the honest Khuluma continuity surface for the
 * signed-out landing.
 *
 * The landing concept shows a Khuluma updates rail ("appointment confirmed",
 * "message from your clinician", "medicine ready"). None of that exists for a
 * guest: live guest messaging is not enabled, and there is no public feed of
 * personal updates — rendering those would be fabrication. What IS real is the
 * locally-persisted care search (need/service + optional facility, no PII), so
 * this card resumes that and nothing more.
 */

import { useEffect } from "react";
import Link from "next/link";
import { ArrowRight, Clock } from "lucide-react";
import { useFindCareJourneyStore } from "@/hooks/useFindCareJourneyStore";

interface ResumeJourneyCardProps {
  /**
   * "dark" sits on the hero canvas; "light" sits inside the white discovery
   * surface. Same content either way — only the contrast pairing changes.
   */
  tone?: "dark" | "light";
}

export function ResumeJourneyCard({ tone = "dark" }: ResumeJourneyCardProps = {}) {
  const hydrate = useFindCareJourneyStore((s) => s.hydrate);
  const hydrated = useFindCareJourneyStore((s) => s.hydrated);
  const need = useFindCareJourneyStore((s) => s.need);
  const serviceLabel = useFindCareJourneyStore((s) => s.serviceLabel);
  const serviceToken = useFindCareJourneyStore((s) => s.serviceToken);
  const returnTo = useFindCareJourneyStore((s) => s.returnTo);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  const label = serviceLabel || need;
  if (!hydrated || !label) return null;

  // Prefer the exact place they were (a facility detail), else re-seed the search.
  const href =
    returnTo && returnTo.startsWith("/welcome/")
      ? returnTo
      : serviceToken
      ? `/welcome/find-care?service=${encodeURIComponent(serviceToken)}`
      : `/welcome/find-care?q=${encodeURIComponent(label)}`;

  if (tone === "light") {
    return (
      <div
        data-testid="resume-journey-card"
        className="rounded-xl border border-emerald-200 bg-emerald-50/70 p-3"
      >
        <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-[0.08em] text-emerald-700">
          <Clock className="h-3.5 w-3.5" aria-hidden />
          Continue where you left off
        </p>
        <p className="mt-1 truncate text-sm font-semibold text-slate-900">
          You were looking for {label}
        </p>
        <Link
          href={href}
          className="mt-2 inline-flex min-h-9 items-center gap-1.5 rounded-lg bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500"
        >
          Resume your search
          <ArrowRight className="h-3.5 w-3.5" aria-hidden />
        </Link>
      </div>
    );
  }

  return (
    <div
      data-testid="resume-journey-card"
      className="mt-4 rounded-2xl border border-white/15 bg-white/10 p-4 backdrop-blur-md supports-[not(backdrop-filter:blur(0px))]:bg-emerald-950/70 [.low-blur_&]:bg-emerald-950/80 [.low-blur_&]:backdrop-blur-none"
    >
      <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.08em] text-teal-200">
        <Clock className="h-3.5 w-3.5" aria-hidden />
        Continue where you left off
      </p>
      <p className="mt-1.5 truncate font-semibold text-white">You were looking for {label}</p>
      <Link
        href={href}
        className="mt-3 inline-flex min-h-10 items-center gap-2 rounded-xl bg-white/15 px-4 py-2 text-sm font-bold text-white ring-1 ring-white/25 hover:bg-white/25 focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-300"
      >
        Resume your search
        <ArrowRight className="h-4 w-4" aria-hidden />
      </Link>
    </div>
  );
}
