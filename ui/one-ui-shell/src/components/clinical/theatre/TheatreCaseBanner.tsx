"use client";

/**
 * Wave 6 §19 — a PERSISTENT theatre context banner. It stays pinned (sticky) at the top of
 * the case surface through the entire data-entry journey so the safety-critical context is
 * ALWAYS visible while scrolling or typing: patient identity, allergies, surgical site/side,
 * and the current perioperative stage.
 *
 * Safety-honest, never fabricated: allergies and surgical site/side are surfaced when the
 * record carries them; when they are NOT confirmed the banner shows an explicit amber
 * "unconfirmed" flag rather than an empty/green field — an unconfirmed allergy or an
 * unmarked surgical site IS a wrong-site / anaphylaxis safety signal (the WHO checklist ethos).
 *
 * Horizontally efficient: a single wrapping flex row that stays compact on desktop, tablet and
 * phone, and keyboard/touch friendly (semantic <header>, aria labels, no hover-only affordances).
 */

import { UserRound, AlertTriangle, Scissors, Activity } from "lucide-react";

export interface TheatreCaseBannerProps {
  patientId?: string;
  patientName?: string;
  procedureName?: string;
  status?: string;
  /** Structured allergy labels when the record carries them; empty/undefined ⇒ unconfirmed flag. */
  allergies?: string[];
  /** Explicit "no known allergies" confirmation (distinct from simply unconfirmed). */
  noKnownAllergies?: boolean;
  surgicalSite?: string;
  surgicalSide?: string;
}

const STAGE_LABEL: Record<string, string> = {
  BOOKED: "Booked",
  PREOP: "Pre-op",
  READY_FOR_THEATRE: "Ready for theatre",
  IN_PROGRESS: "In theatre",
  PACU: "Recovery (PACU)",
  RECOVERED: "Recovered",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
  DECEASED: "Deceased",
};

const STAGE_TONE: Record<string, string> = {
  BOOKED: "bg-slate-200 text-slate-700",
  PREOP: "bg-blue-100 text-blue-800",
  READY_FOR_THEATRE: "bg-emerald-100 text-emerald-800",
  IN_PROGRESS: "bg-amber-100 text-amber-900",
  PACU: "bg-violet-100 text-violet-800",
  RECOVERED: "bg-emerald-100 text-emerald-800",
  COMPLETED: "bg-emerald-100 text-emerald-800",
  CANCELLED: "bg-red-100 text-red-800",
  DECEASED: "bg-slate-800 text-white",
};

export function TheatreCaseBanner({
  patientId,
  patientName,
  procedureName,
  status,
  allergies,
  noKnownAllergies,
  surgicalSite,
  surgicalSide,
}: TheatreCaseBannerProps) {
  const stage = status ?? "BOOKED";
  const hasAllergies = Array.isArray(allergies) && allergies.length > 0;
  const allergyConfirmed = hasAllergies || noKnownAllergies === true;
  const siteSide = [surgicalSite, surgicalSide].filter(Boolean).join(" · ");

  return (
    <header
      data-testid="theatre-case-banner"
      aria-label="Theatre case context"
      className="sticky top-0 z-30 -mx-1 mb-4 rounded-xl border border-border bg-card/95 px-4 py-2.5 shadow-sm backdrop-blur supports-[backdrop-filter]:bg-card/80"
    >
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
        {/* Patient identity */}
        <span className="inline-flex items-center gap-1.5 font-semibold">
          <UserRound className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
          <span data-testid="banner-patient">{patientName || patientId || "Unknown patient"}</span>
          {patientName && patientId && <span className="font-normal text-muted-foreground">({patientId})</span>}
        </span>

        {procedureName && (
          <span className="text-muted-foreground">{procedureName}</span>
        )}

        {/* Allergies — safety critical */}
        {allergyConfirmed ? (
          hasAllergies ? (
            <span
              data-testid="banner-allergies"
              className="inline-flex items-center gap-1.5 rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-800"
            >
              <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
              Allergies: {allergies!.join(", ")}
            </span>
          ) : (
            <span
              data-testid="banner-allergies"
              className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-800"
            >
              NKDA (no known allergies)
            </span>
          )
        ) : (
          <span
            data-testid="banner-allergies"
            className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-900"
          >
            <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
            Allergies unconfirmed
          </span>
        )}

        {/* Surgical site / side — wrong-site prevention */}
        {siteSide ? (
          <span
            data-testid="banner-site"
            className="inline-flex items-center gap-1.5 rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-semibold text-blue-900"
          >
            <Scissors className="h-3.5 w-3.5" aria-hidden="true" />
            {siteSide}
          </span>
        ) : (
          <span
            data-testid="banner-site"
            className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-900"
          >
            <Scissors className="h-3.5 w-3.5" aria-hidden="true" />
            Site/side not marked
          </span>
        )}

        {/* Current stage — pinned to the right on wide screens */}
        <span
          data-testid="banner-stage"
          className={`ml-auto inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${STAGE_TONE[stage] ?? "bg-slate-200 text-slate-700"}`}
        >
          <Activity className="h-3.5 w-3.5" aria-hidden="true" />
          {STAGE_LABEL[stage] ?? stage}
        </span>
      </div>
    </header>
  );
}

export default TheatreCaseBanner;
