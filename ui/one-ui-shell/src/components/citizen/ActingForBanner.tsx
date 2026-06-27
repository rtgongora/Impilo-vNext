"use client";

import { ShieldAlert } from "lucide-react";
import { useDelegationStore } from "@/hooks/useDelegationStore";

const RELATIONSHIP_LABELS: Record<string, string> = {
  GUARDIAN: "guardian",
  CAREGIVER: "caregiver",
  CHW: "community health worker",
  FACILITY_STAFF: "facility staff",
};

/**
 * Persistent "You are acting for X" banner (L5, G-CZO-03). Shown on every page while a delegation
 * context is active, so a delegate can never forget whose record they are working in — and every
 * action they take carries X-Subject-ID and is authorised + audited as delegated. Exiting returns
 * them to their own context.
 */
export function ActingForBanner() {
  const actingFor = useDelegationStore((s) => s.actingFor);
  const exitContext = useDelegationStore((s) => s.exitContext);

  if (!actingFor) return null;
  const rel = RELATIONSHIP_LABELS[actingFor.relationshipType?.toUpperCase?.()] ?? actingFor.relationshipType;

  return (
    <div
      role="status"
      className="flex items-center justify-between gap-3 border-b border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-900"
    >
      <span className="flex items-center gap-2">
        <ShieldAlert className="h-4 w-4 shrink-0" />
        You are acting for <strong>{actingFor.subjectLabel}</strong>
        {rel ? <span className="text-amber-700">({rel})</span> : null}
        <span className="hidden text-amber-700 sm:inline">· every action is recorded</span>
      </span>
      <button
        type="button"
        onClick={exitContext}
        className="shrink-0 rounded-md border border-amber-400 bg-white px-3 py-1 text-xs font-semibold text-amber-800 hover:bg-amber-100"
      >
        Exit
      </button>
    </div>
  );
}
