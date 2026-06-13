"use client";

/**
 * Facility Work subcontext ribbon for the shift + scheduling cluster.
 * Persists focus via operational context + optional `?subcontext=` deep link.
 */

import { useEffect } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Building2, Layers, Clock } from "lucide-react";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import {
  FACILITY_WORK_CLUSTER_SUBCONTEXTS,
  FACILITY_WORK_SUBCONTEXT_NAV,
  isFacilityWorkClusterSubcontext,
  type FacilityWorkClusterSubcontext,
} from "@/lib/facility-work-cluster";
import type { FacilityWorkSubcontext } from "@/lib/operational-context";

interface FacilityWorkClusterRibbonProps {
  /** Shift pages set true — surfaces sequencing expectations in copy. */
  shiftExpected: boolean;
  /** Live loop signal when the parent already loaded queue data (e.g. in-progress encounters). */
  activeEncounterCount?: number;
}

export function FacilityWorkClusterRibbon({
  shiftExpected,
  activeEncounterCount,
}: FacilityWorkClusterRibbonProps) {
  const searchParams = useSearchParams();
  const fromOrgPlane = searchParams.get("from") === "organization-admin";
  const rawSub = searchParams.get("subcontext");

  const facility = useFacilityStore((s) => s.facility);
  const workspace = useWorkspaceStore((s) => s.workspace);
  const shift = useShiftStore((s) => s.shift);

  const subcontext = useOperationalContextStore((s) => s.facilityWorkSubcontext);
  const setSub = useOperationalContextStore((s) => s.setFacilityWorkSubcontext);
  const setMode = useOperationalContextStore((s) => s.setOperationalMode);

  useEffect(() => {
    if (fromOrgPlane) return;
    setMode("facility_work");
  }, [fromOrgPlane, setMode]);

  useEffect(() => {
    if (!rawSub || !isFacilityWorkClusterSubcontext(rawSub)) return;
    setSub(rawSub as FacilityWorkSubcontext);
  }, [rawSub, setSub]);

  if (fromOrgPlane) return null;

  const resolved: FacilityWorkClusterSubcontext | null =
    subcontext && isFacilityWorkClusterSubcontext(subcontext) ? subcontext : null;

  const nav = resolved ? FACILITY_WORK_SUBCONTEXT_NAV[resolved] : FACILITY_WORK_SUBCONTEXT_NAV.supervisor;

  function select(s: FacilityWorkClusterSubcontext) {
    setSub(s);
  }

  return (
    <div className="mb-6 rounded-xl border border-sky-200/90 bg-gradient-to-r from-sky-50 to-slate-50 px-4 py-3 text-sm text-foreground shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-2">
          <Layers className="mt-0.5 h-4 w-4 shrink-0 text-sky-700" aria-hidden />
          <div>
            <p className="font-semibold text-foreground">Facility Work focus</p>
            <p className="text-xs text-muted-foreground">
              Same experience layer — sequencing stays{" "}
              <span className="font-medium">facility → workspace{shiftExpected ? " → shift" : ""}</span>
              . Queue and chart links require an active shift unless you are on an org-admin scheduling path.
            </p>
            <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1">
                <Building2 className="h-3.5 w-3.5 text-muted-foreground" aria-hidden />
                Facility: <span className="font-medium text-foreground">{facility?.name ?? "Not selected"}</span>
              </span>
              <span className="inline-flex items-center gap-1">
                <Layers className="h-3.5 w-3.5 text-muted-foreground" aria-hidden />
                Workspace:{" "}
                <span className="font-medium text-foreground">{workspace?.name ?? "Not selected"}</span>
              </span>
              {shiftExpected && (
                <span className="inline-flex items-center gap-1">
                  <Clock className="h-3.5 w-3.5 text-muted-foreground" aria-hidden />
                  Shift:{" "}
                  <span className="font-medium text-foreground">{shift ? "Active" : "Not started"}</span>
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        {FACILITY_WORK_CLUSTER_SUBCONTEXTS.map((key) => {
          const active = resolved === key;
          const label = FACILITY_WORK_SUBCONTEXT_NAV[key].shortLabel;
          return (
            <button
              key={key}
              type="button"
              onClick={() => select(key)}
              className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                active
                  ? "border-sky-600 bg-sky-600 text-white"
                  : "border-border bg-card text-foreground hover:border-sky-300"
              }`}
            >
              {label}
            </button>
          );
        })}
      </div>

      <div className="mt-4 rounded-lg border border-white/80 bg-card/70 px-3 py-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Next operational surface</p>
        <div className="mt-1 flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm text-foreground">
            {resolved ? (
              <>
                <span className="font-medium">{nav.shortLabel}:</span> {nav.nextCue}
              </>
            ) : (
              <span className="text-muted-foreground">Pick a focus above — defaulting to supervisor queue view.</span>
            )}
          </p>
          <Link
            href={nav.href}
            className="shrink-0 rounded-lg bg-sky-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-sky-700"
          >
            Open {nav.shortLabel}
          </Link>
        </div>
        {typeof activeEncounterCount === "number" && (
          <p className="mt-2 text-xs text-warning-foreground">
            Loop status: <span className="font-medium">{activeEncounterCount}</span> encounter(s) in progress on the
            queue — resolve or hand over before ending shift when possible.
          </p>
        )}
      </div>
    </div>
  );
}
