"use client";

/**
 * Left-zone continuity block. Always renders something, which is the point: the hero
 * previously showed a returning-user card only when a masked hint existed and nothing
 * at all otherwise, so a first-time visitor saw a gap where the continuation belongs.
 *
 * Recognised → welcome back, continue, use another account.
 * Not recognised → a compact, warm sign-in and guest invitation.
 *
 * Recognition is the opt-in masked hint only (first name + timestamp, no IDs, no health
 * data). It never claims a workspace or a record before TSHEPO has resolved anything.
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight, ShieldCheck, UserRound } from "lucide-react";
import { clearReturnHint, readReturnHint, relativeSince, type ReturnHint } from "@/lib/return-hint";

export function HeroContinuity() {
  const [hint, setHint] = useState<ReturnHint | null>(null);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setHint(readReturnHint());
    setHydrated(true);
  }, []);

  // Render the guest form during hydration so the block never pops in late.
  if (hydrated && hint) {
    const since = relativeSince(hint.ts);
    return (
      <div
        data-testid="hero-continuity"
        className="relative mt-5 overflow-hidden rounded-2xl border border-white/15 bg-white/10 p-3.5 backdrop-blur-md before:pointer-events-none before:absolute before:inset-x-0 before:top-0 before:h-1/2 before:bg-gradient-to-b before:from-white/10 before:to-transparent supports-[not(backdrop-filter:blur(0px))]:bg-emerald-950/70 [.low-blur_&]:bg-emerald-950/80 [.low-blur_&]:backdrop-blur-none"
      >
        <div className="flex items-center gap-2.5">
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-teal-400/20 text-teal-200 ring-1 ring-teal-300/30">
            <UserRound className="h-4.5 w-4.5" aria-hidden />
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-bold text-white">Welcome back, {hint.name}</p>
            {since && <p className="text-xs text-emerald-100/70">Last here {since}</p>}
          </div>
        </div>
        <div className="mt-2.5 flex flex-wrap items-center gap-2">
          <Link
            href="/auth/login?returnTo=%2Fhome"
            className="inline-flex min-h-9 items-center gap-1.5 rounded-lg bg-gradient-to-br from-emerald-500 to-emerald-700 px-3 py-1.5 text-xs font-bold text-white shadow-[inset_0_1px_0_rgba(255,255,255,.3)] hover:brightness-110"
          >
            My Impilo
            <ArrowRight className="h-3.5 w-3.5" aria-hidden />
          </Link>
          <Link
            href="/auth/login?returnTo=%2Fwork"
            className="inline-flex min-h-9 items-center rounded-lg border border-white/25 px-3 py-1.5 text-xs font-semibold text-emerald-50 hover:bg-white/10"
          >
            My Professional
          </Link>
          <button
            type="button"
            onClick={() => {
              clearReturnHint();
              setHint(null);
            }}
            className="min-h-9 rounded-lg px-2 text-xs font-medium text-emerald-100/70 underline-offset-2 hover:text-white hover:underline"
          >
            Use another account
          </button>
        </div>
      </div>
    );
  }

  return (
    <div
      data-testid="hero-continuity"
      className="relative mt-5 overflow-hidden rounded-2xl border border-white/15 bg-white/10 p-3.5 backdrop-blur-md before:pointer-events-none before:absolute before:inset-x-0 before:top-0 before:h-1/2 before:bg-gradient-to-b before:from-white/10 before:to-transparent supports-[not(backdrop-filter:blur(0px))]:bg-emerald-950/70 [.low-blur_&]:bg-emerald-950/80 [.low-blur_&]:backdrop-blur-none"
    >
      <p className="flex items-center gap-2 text-sm font-semibold text-white">
        <ShieldCheck className="h-4 w-4 text-teal-300" aria-hidden />
        Most of Impilo works without an account
      </p>
      <p className="mt-1 text-xs leading-5 text-emerald-50/80">
        Sign in only for your own records, appointments and prescriptions — or to work as a
        health professional.
      </p>
      <div className="mt-2.5 flex flex-wrap items-center gap-2">
        <Link
          href="/auth/login?returnTo=%2Fhome"
          className="inline-flex min-h-9 items-center gap-1.5 rounded-lg bg-gradient-to-br from-emerald-500 to-emerald-700 px-3 py-1.5 text-xs font-bold text-white shadow-[inset_0_1px_0_rgba(255,255,255,.3)] hover:brightness-110"
        >
          My Impilo
          <ArrowRight className="h-3.5 w-3.5" aria-hidden />
        </Link>
        <Link
          href="/provider/get-access"
          className="inline-flex min-h-9 items-center rounded-lg border border-white/25 px-3 py-1.5 text-xs font-semibold text-emerald-50 hover:bg-white/10"
        >
          Work on Impilo
        </Link>
      </div>
    </div>
  );
}
