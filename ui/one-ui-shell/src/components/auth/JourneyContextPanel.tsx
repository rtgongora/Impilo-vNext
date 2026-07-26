"use client";

/**
 * Journey Context Panel — Preserves and renders active user journey state
 * (care booking, emergency tracking, provider work access, or default welcome)
 * on the left side of the Auth Trust Scene.
 */

import Link from "next/link";
import {
  Building2,
  Calendar,
  Clock,
  FileCheck2,
  HeartHandshake,
  ShieldCheck,
  Siren,
  Sparkles,
  Stethoscope,
  UserCheck,
} from "lucide-react";
import { useFindCareJourneyStore } from "@/hooks/useFindCareJourneyStore";

interface JourneyContextPanelProps {
  returnTo?: string | null;
  intentGoal?: string | null;
}

export function JourneyContextPanel({ returnTo, intentGoal }: JourneyContextPanelProps) {
  const findCareNeed = useFindCareJourneyStore((s) => s.need);
  const findCareService = useFindCareJourneyStore((s) => s.serviceLabel);

  // Only claim a saved booking when the store actually holds one — a returnTo hint alone
  // must never fabricate a facility/appointment (no invented data on a health surface).
  const hasRealFindCareJourney = !!(findCareService || findCareNeed);
  const isFindCareBooking = hasRealFindCareJourney;
  const isEmergencyTrack = returnTo?.includes("/emergency") || returnTo?.includes("/welcome/emergency");
  const isRegulatory = returnTo?.includes("/regulatory") || returnTo?.includes("/organization-admin");
  const isProviderAccess = returnTo?.includes("/provider") || returnTo?.includes("/work");

  if (isFindCareBooking) {
    return (
      <div
        data-testid="journey-context-booking"
        className="rounded-[2rem] border border-emerald-200 bg-gradient-to-br from-emerald-900 to-slate-900 p-7 text-white shadow-lg sm:p-8"
      >
        <div className="flex items-center gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-500/20 text-emerald-300 ring-1 ring-emerald-400/30">
            <HeartHandshake className="h-5 w-5" aria-hidden />
          </span>
          <span className="text-xs font-bold uppercase tracking-widest text-emerald-300">
            Journey Saved & Preserved
          </span>
        </div>

        <h2 className="mt-5 text-2xl font-bold tracking-tight text-white sm:text-3xl">
          Continue your care booking
        </h2>

        <div className="mt-6 space-y-4 rounded-2xl bg-white/10 p-5 ring-1 ring-white/15 backdrop-blur-sm">
          <div className="flex items-start gap-3">
            <Building2 className="mt-0.5 h-5 w-5 text-emerald-300 shrink-0" aria-hidden />
            <div>
              <p className="text-xs font-medium text-emerald-200 uppercase tracking-wider">Facility / Service</p>
              <p className="font-semibold text-white">
                {findCareService || findCareNeed}
              </p>
            </div>
          </div>

          {intentGoal && (
            <div className="flex items-start gap-3">
              <Calendar className="mt-0.5 h-5 w-5 text-emerald-300 shrink-0" aria-hidden />
              <div>
                <p className="text-xs font-medium text-emerald-200 uppercase tracking-wider">Appointment Type</p>
                <p className="text-sm font-medium text-emerald-50">{intentGoal}</p>
              </div>
            </div>
          )}

          <div className="flex items-start gap-3">
            <Clock className="mt-0.5 h-5 w-5 text-emerald-300 shrink-0" aria-hidden />
            <div>
              <p className="text-xs font-medium text-emerald-200 uppercase tracking-wider">Status</p>
              <p className="text-xs text-emerald-100/90">
                Your selections are saved. Sign in to confirm your time slot and connect this visit to your health record.
              </p>
            </div>
          </div>
        </div>

        <div className="mt-6 flex items-center justify-between text-xs text-emerald-200/80">
          <span className="flex items-center gap-1.5">
            <ShieldCheck className="h-4 w-4 text-emerald-400" />
            Protected by TSHEPO Privacy Guard
          </span>
          <Link href="/welcome/find-care" className="underline hover:text-white">
            Change facility
          </Link>
        </div>
      </div>
    );
  }

  if (isEmergencyTrack) {
    return (
      <div
        data-testid="journey-context-emergency"
        className="rounded-[2rem] border border-red-200 bg-gradient-to-br from-red-950 to-slate-900 p-7 text-white shadow-lg sm:p-8"
      >
        <div className="flex items-center gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-xl bg-red-500/20 text-red-300 ring-1 ring-red-400/30">
            <Siren className="h-5 w-5" aria-hidden />
          </span>
          <span className="text-xs font-bold uppercase tracking-widest text-red-300">
            Emergency Tracking Context
          </span>
        </div>

        <h2 className="mt-5 text-2xl font-bold tracking-tight text-white">
          Track active emergency response
        </h2>

        <p className="mt-3 text-sm leading-relaxed text-red-100/90">
          Signing in connects your emergency request to your profile for real-time dispatch updates and PHR continuity.
        </p>

        <div className="mt-6 rounded-2xl bg-white/10 p-4 ring-1 ring-white/15 text-xs text-red-100">
          <strong>Need immediate emergency assistance?</strong> Emergency help remains accessible on every screen without signing in.
        </div>
      </div>
    );
  }

  if (isRegulatory || isProviderAccess) {
    return (
      <div
        data-testid="journey-context-provider"
        className="rounded-[2rem] border border-amber-200 bg-gradient-to-br from-amber-950 to-slate-900 p-7 text-white shadow-lg sm:p-8"
      >
        <div className="flex items-center gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-xl bg-amber-500/20 text-amber-300 ring-1 ring-amber-400/30">
            <Stethoscope className="h-5 w-5" aria-hidden />
          </span>
          <span className="text-xs font-bold uppercase tracking-widest text-amber-300">
            Professional & Work Access
          </span>
        </div>

        <h2 className="mt-5 text-2xl font-bold tracking-tight text-white">
          Work on Impilo
        </h2>

        <p className="mt-3 text-sm leading-relaxed text-amber-50/85">
          Sign in to practise as a verified health provider, manage facility operations, conduct shifts, or process regulatory registrations.
        </p>

        <div className="mt-6 space-y-3 rounded-2xl bg-white/10 p-4 text-xs text-amber-100 ring-1 ring-white/15">
          <div className="flex items-center gap-2">
            <UserCheck className="h-4 w-4 text-amber-300 shrink-0" />
            <span>Single identity entry -- TSHEPO automatically resolves your active provider, facility & regulatory roles.</span>
          </div>
          <div className="flex items-center gap-2">
            <FileCheck2 className="h-4 w-4 text-amber-300 shrink-0" />
            <span>Direct landing into your active work workspace without personal citizen tabs.</span>
          </div>
        </div>
      </div>
    );
  }

  // Calm Default Welcome Scene
  return (
    <div
      data-testid="journey-context-default"
      className="rounded-[2rem] border border-slate-200 bg-gradient-to-br from-emerald-950 via-slate-900 to-slate-950 p-7 text-white shadow-lg sm:p-9"
    >
      <div className="flex items-center gap-3">
        <span className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-500/20 text-emerald-300 ring-1 ring-emerald-400/30">
          <Sparkles className="h-5 w-5" aria-hidden />
        </span>
        <span className="text-xs font-bold uppercase tracking-widest text-emerald-300">
          Trust & Identity Step
        </span>
      </div>

      <h2 className="mt-5 text-3xl font-bold tracking-tight text-white">
        Welcome to Impilo
      </h2>

      <p className="mt-4 text-base leading-relaxed text-emerald-50/85">
        Impilo connects care, records, telemedicine, diagnostics, and professional work in one governed environment. Signing in allows Impilo to recognise you safely without breaking your momentum.
      </p>

      <ul className="mt-7 space-y-3 text-sm text-emerald-100/90">
        <li className="flex items-center gap-2.5">
          <span className="h-2 w-2 rounded-full bg-emerald-400" />
          <span>Access personal health records, bookings & prescriptions</span>
        </li>
        <li className="flex items-center gap-2.5">
          <span className="h-2 w-2 rounded-full bg-emerald-400" />
          <span>Conduct healthcare practice & clinical operations</span>
        </li>
        <li className="flex items-center gap-2.5">
          <span className="h-2 w-2 rounded-full bg-emerald-400" />
          <span>Progressive trust: protected actions remain safe</span>
        </li>
      </ul>
    </div>
  );
}
