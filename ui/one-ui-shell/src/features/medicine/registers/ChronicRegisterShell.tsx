"use client";

import { useState } from "react";
import { AlertTriangle, Loader2 } from "lucide-react";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  CHRONIC_REGISTERS,
  useChronicRegister,
  type ControlStatus,
  type RegisterEntry,
} from "@/hooks/queries/useChronicRegister";

/**
 * A chronic-disease register (brief.md §8) as a recall worklist.
 *
 * The one rendering decision that matters: a null `control_status` is shown as **not assessed**, in
 * its own visual state, never as a blank cell and never folded in with the assessed. A blank cell in
 * a control column is read as "fine" by everyone who has ever looked at a spreadsheet, and the people
 * in that state are precisely the ones this list exists to surface.
 */

const CONTROL_LABEL: Record<ControlStatus, string> = {
  CONTROLLED: "At target",
  PARTIALLY_CONTROLLED: "Improving",
  NOT_CONTROLLED: "Above target",
  TARGET_NOT_SET: "No target agreed",
};

const CONTROL_CLASS: Record<ControlStatus, string> = {
  CONTROLLED: "text-success",
  PARTIALLY_CONTROLLED: "text-warning-foreground",
  NOT_CONTROLLED: "text-danger font-medium",
  TARGET_NOT_SET: "text-muted-foreground",
};

function ControlCell({ entry }: { entry: RegisterEntry }) {
  if (entry.control_status === null) {
    return (
      <span className="text-warning-foreground font-medium" data-testid={`control-unassessed-${entry.enrolment_id}`}>
        Not assessed
      </span>
    );
  }
  return (
    <span className={CONTROL_CLASS[entry.control_status]} data-testid={`control-${entry.enrolment_id}`}>
      {CONTROL_LABEL[entry.control_status]}
      {entry.control_assessed_on ? ` · ${entry.control_assessed_on}` : ""}
    </span>
  );
}

export function ChronicRegisterShell() {
  const [programme, setProgramme] = useState<string>(CHRONIC_REGISTERS[0].programme);
  const facility = useFacilityStore((s) => s.facility);
  const query = useChronicRegister(programme, facility?.id ?? null);
  const register = query.data?.data;

  return (
    <div className="space-y-4" data-testid="chronic-register">
      <div className="flex flex-wrap gap-2">
        {CHRONIC_REGISTERS.map((r) => (
          <button
            key={r.programme}
            type="button"
            onClick={() => setProgramme(r.programme)}
            className={`rounded border px-3 py-1.5 text-sm ${
              programme === r.programme
                ? "border-primary bg-primary text-primary-foreground"
                : "border-border hover:bg-muted"
            }`}
            data-testid={`register-tab-${r.programme}`}
          >
            {r.label}
          </button>
        ))}
      </div>

      {!facility && (
        <p className="text-sm text-muted-foreground" data-testid="register-no-facility">
          No facility in context — showing every facility in the tenant.
        </p>
      )}

      {query.isLoading && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground" data-testid="register-loading">
          <Loader2 className="w-4 h-4 animate-spin" /> Reading the register…
        </div>
      )}

      {query.isError && (
        <div className="flex items-start gap-2 rounded-lg border border-danger/30 bg-red-50 p-4" data-testid="register-failed">
          <AlertTriangle className="w-4 h-4 text-danger mt-0.5" />
          <p className="text-sm text-danger">
            The register could not be read. This is <strong>not</strong> a statement that nobody is on
            it.
          </p>
        </div>
      )}

      {register && !query.isError && (
        <>
          <div className="rounded-lg border border-border bg-muted/30 p-4 text-sm text-muted-foreground">
            {register.control_status_null_means_never_assessed}
          </div>

          {register.entries.length === 0 ? (
            <p className="text-sm text-muted-foreground" data-testid="register-empty">
              Nobody is currently on this register at this facility. The register was read.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm" data-testid="register-table">
                <thead>
                  <tr className="border-b border-border text-left text-muted-foreground">
                    <th className="py-2 pr-4 font-medium">Patient</th>
                    <th className="py-2 pr-4 font-medium">Number</th>
                    <th className="py-2 pr-4 font-medium">Status</th>
                    <th className="py-2 pr-4 font-medium">Control</th>
                    <th className="py-2 pr-4 font-medium">Agreed target</th>
                  </tr>
                </thead>
                <tbody>
                  {register.entries.map((e) => (
                    <tr key={e.enrolment_id} className="border-b border-border/60" data-testid={`register-row-${e.enrolment_id}`}>
                      <td className="py-2 pr-4">{e.subject_cpid}</td>
                      <td className="py-2 pr-4 text-muted-foreground">{e.programme_number ?? "—"}</td>
                      <td className="py-2 pr-4 text-muted-foreground">{e.status}</td>
                      <td className="py-2 pr-4">
                        <ControlCell entry={e} />
                      </td>
                      <td className="py-2 pr-4 text-muted-foreground">
                        {/* Where a target was individualised, showing it stops the next clinician
                            re-escalating one the last one deliberately relaxed (brief §9). */}
                        {e.individualised_target ?? "Guideline default"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}
