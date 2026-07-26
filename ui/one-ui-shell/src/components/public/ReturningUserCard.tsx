"use client";

/**
 * Returning-user greeting for the signed-out landing. Renders ONLY when a masked
 * "remember me on this device" hint exists (opt-in). Shows a first name + when they
 * were last active, and routes into sign-in — it never shows records or workspace
 * detail before authentication (TSHEPO resolves the real destination post sign-in).
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight, ShieldCheck, UserRound } from "lucide-react";
import { clearReturnHint, readReturnHint, relativeSince, type ReturnHint } from "@/lib/return-hint";

export function ReturningUserCard() {
  const [hint, setHint] = useState<ReturnHint | null>(null);

  useEffect(() => {
    setHint(readReturnHint());
  }, []);

  if (!hint) return null;
  const since = relativeSince(hint.ts);

  return (
    <div
      data-testid="returning-user-card"
      className="mt-6 rounded-2xl border border-emerald-200/90 bg-white/80 p-4 shadow-sm backdrop-blur-sm"
    >
      <div className="flex items-center gap-3">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-emerald-100 text-emerald-700">
          <UserRound className="h-5 w-5" aria-hidden />
        </span>
        <div className="min-w-0">
          <p className="truncate font-bold text-slate-900">Welcome back, {hint.name}</p>
          {since && <p className="text-xs text-slate-500">Last active {since} on this device</p>}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Link
          href="/auth/login?returnTo=%2Fhome"
          className="inline-flex min-h-10 items-center gap-2 rounded-xl bg-emerald-700 px-4 py-2 text-sm font-bold text-white hover:bg-emerald-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-700 focus-visible:ring-offset-2"
        >
          Continue to Impilo
          <ArrowRight className="h-4 w-4" aria-hidden />
        </Link>
        <button
          type="button"
          onClick={() => {
            clearReturnHint();
            setHint(null);
          }}
          className="inline-flex min-h-10 items-center rounded-xl px-3 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600"
        >
          Use another account
        </button>
      </div>

      <p className="mt-2.5 flex items-center gap-1.5 text-[11px] leading-snug text-slate-400">
        <ShieldCheck className="h-3.5 w-3.5 shrink-0 text-emerald-500" aria-hidden />
        Only your first name is remembered on this device. No records are shown until you sign in.
      </p>
    </div>
  );
}
