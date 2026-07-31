"use client";

/**
 * Birth destination — can this facility be where she gives birth, for the level of care she needs?
 *
 * Backend: `GET /internal/v1/maternity/birth-destination` — see `useBirthDestination.ts` for the
 * three honesty rules this panel must preserve on render:
 *
 *   1. `NOT_OPERATIONAL` is not a confirmed destination, whether that is because it was assessed as
 *      closed or because its status could not be read — an unknown never defaults to safe here.
 *   2. `CAPABILITY_UNKNOWN` renders as "not yet assessed, call ahead" — never as "cannot", and never
 *      folded into the same styling as a resolved "meets"/"below" verdict. This is the common case
 *      across the estate, not a rare edge.
 *   3. A 502 (`status: "UNAVAILABLE"`) is its own distinct banner — never rendered inside the verdict
 *      card, and never implying either a safe or an unsafe destination.
 *
 * Shared by the clinician EHR maternity page and the citizen `/my/pregnancy` page — both call the
 * same `useBirthDestination` hook, so a facility she is told to call ahead about is exactly the
 * facility a midwife would be told the same about.
 */

import { useState } from "react";
import { AlertTriangle, HelpCircle, Loader2, PhoneCall, ShieldAlert, CircleCheck, XCircle } from "lucide-react";
import {
  isBirthDestinationUnavailable,
  useBirthDestination,
  type BirthDestinationRequiredLevel,
  type BirthDestinationVerdict,
} from "@/hooks/queries/useBirthDestination";

const FACILITY_ID_STORAGE_KEY = "exp:facility_id";

function prefillFacilityId(): string {
  if (typeof window === "undefined") return "";
  const stored = window.sessionStorage.getItem(FACILITY_ID_STORAGE_KEY);
  if (!stored) return "";
  return /^\d+$/.test(stored.trim()) ? stored.trim() : "";
}

const LEVEL_OPTIONS: { value: BirthDestinationRequiredLevel; label: string }[] = [
  { value: "UNKNOWN", label: "Not yet established" },
  { value: "BEMONC", label: "Basic emergency obstetric care (BEmONC)" },
  { value: "CEMONC", label: "Comprehensive emergency obstetric care (CEmONC)" },
];

/** Styling and heading per status — deliberately never sharing a "can"/"cannot" verb between them. */
function statusPresentation(status: BirthDestinationVerdict["status"]): {
  heading: string;
  tone: "positive" | "caution" | "unknown" | "negative";
  icon: typeof CircleCheck;
} {
  switch (status) {
    case "MEETS_REQUIRED_LEVEL":
      return { heading: "Meets the level of care needed", tone: "positive", icon: CircleCheck };
    case "BELOW_REQUIRED_LEVEL":
      return { heading: "Assessed below the level of care needed", tone: "negative", icon: XCircle };
    case "CAPABILITY_UNKNOWN":
      return { heading: "Emergency obstetric capability not yet assessed", tone: "unknown", icon: HelpCircle };
    case "NOT_OPERATIONAL":
      return { heading: "Not a confirmed operating maternity destination", tone: "negative", icon: XCircle };
    case "REQUIRED_LEVEL_UNKNOWN":
      return { heading: "Level of care needed has not been established", tone: "unknown", icon: HelpCircle };
    default:
      return { heading: status, tone: "unknown", icon: HelpCircle };
  }
}

const TONE_CLASSES: Record<string, string> = {
  positive: "border-emerald-300/70 bg-emerald-50 text-emerald-900",
  negative: "border-danger/28 bg-danger-soft text-red-900",
  unknown: "border-amber-300/70 bg-amber-50 text-amber-900",
  caution: "border-amber-300/70 bg-amber-50 text-amber-900",
};

function VerdictCard({ verdict }: { verdict: BirthDestinationVerdict }) {
  const { heading, tone, icon: Icon } = statusPresentation(verdict.status);
  return (
    <div
      className={`mt-3 rounded-lg border px-3 py-3 text-sm ${TONE_CLASSES[tone]}`}
      data-testid="birth-destination-verdict"
      data-status={verdict.status}
    >
      <div className="flex items-start gap-2">
        <Icon className="mt-0.5 h-5 w-5 shrink-0" />
        <div className="flex-1">
          <p className="font-semibold">{heading}</p>
          <p className="mt-1 text-xs">{verdict.message}</p>
          <p className="mt-1 text-xs">{verdict.guidance}</p>
        </div>
      </div>
      {/* Shown whenever true, verbatim — this is the recommended next action, not a footnote. */}
      {verdict.call_ahead && (
        <div className="mt-2 flex items-center gap-1.5 text-xs font-semibold" data-testid="birth-destination-call-ahead">
          <PhoneCall className="h-3.5 w-3.5" />
          Call ahead
        </div>
      )}
      {verdict.emonc_verdict && (
        <p className="mt-1 text-[11px] opacity-80">EmONC readiness on record: {verdict.emonc_verdict}</p>
      )}
    </div>
  );
}

function UnavailableBanner({ verdict }: { verdict?: BirthDestinationVerdict }) {
  return (
    <div
      className="mt-3 flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-3 text-sm text-red-900"
      data-testid="birth-destination-unavailable"
      role="alert"
    >
      <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" />
      <div>
        <p className="font-semibold">The facility&apos;s status could not be retrieved</p>
        <p className="mt-1 text-xs">
          {verdict?.message ?? "The facility's status could not be retrieved."}
        </p>
        <p className="mt-1 text-xs">
          {verdict?.guidance ??
            "Do not treat this as either a safe or an unsafe destination — it is unknown. Confirm by phone before directing her here."}
        </p>
      </div>
    </div>
  );
}

export function BirthDestinationPanel() {
  const [facilityId, setFacilityId] = useState<string>(() => prefillFacilityId());
  const [requiredLevel, setRequiredLevel] = useState<BirthDestinationRequiredLevel>("UNKNOWN");
  const assess = useBirthDestination();

  const canAssess = /^\d+$/.test(facilityId.trim());

  function handleAssess() {
    if (!canAssess) return;
    assess.mutate({ facilityId: Number(facilityId.trim()), requiredLevel });
  }

  const unavailable = assess.isError && isBirthDestinationUnavailable(assess.error);
  const errorVerdict =
    unavailable && typeof assess.error === "object" && assess.error !== null && "data" in assess.error
      ? (assess.error as { data?: BirthDestinationVerdict }).data
      : undefined;

  return (
    <div className="rounded-xl border border-border bg-card p-4" data-testid="birth-destination-panel">
      <h3 className="text-sm font-semibold text-foreground">Birth destination check</h3>
      <p className="mt-1 text-xs text-muted-foreground">
        Checks whether a facility is a confirmed maternity destination for the level of emergency
        obstetric care needed. An unassessed capability is shown as unknown, never as a refusal.
      </p>

      <div className="mt-3 grid gap-3 sm:grid-cols-[1fr,2fr]">
        <label className="text-xs font-medium text-muted-foreground">
          Facility ID
          <input
            type="text"
            inputMode="numeric"
            value={facilityId}
            onChange={(e) => setFacilityId(e.target.value)}
            placeholder="e.g. 1042"
            data-testid="birth-destination-facility-id"
            className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
          />
        </label>
        <label className="text-xs font-medium text-muted-foreground">
          Level of care needed
          <select
            value={requiredLevel}
            onChange={(e) => setRequiredLevel(e.target.value as BirthDestinationRequiredLevel)}
            data-testid="birth-destination-required-level"
            className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
          >
            {LEVEL_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <button
        type="button"
        onClick={handleAssess}
        disabled={!canAssess || assess.isPending}
        data-testid="birth-destination-assess"
        className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
      >
        {assess.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
        Assess
      </button>

      {assess.isError && !unavailable && (
        <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <p className="text-xs">Could not assess this facility. Try again.</p>
        </div>
      )}

      {unavailable && <UnavailableBanner verdict={errorVerdict} />}

      {assess.data && !unavailable && <VerdictCard verdict={assess.data.data} />}
    </div>
  );
}
