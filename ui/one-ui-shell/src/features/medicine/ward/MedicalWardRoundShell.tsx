"use client";

import { useState } from "react";
import Link from "next/link";
import { AlertTriangle, BedDouble, Loader2, Stethoscope } from "lucide-react";
import { useMedicalWardContext } from "./useMedicalWardContext";
import { useStartWardRound, useAddWardRoundEntry } from "@/hooks/queries/useInpatient";
import { programmeLabel } from "../workspace/medicine-summary";

/**
 * The medical ward round, for one admitted patient.
 *
 * Complements rather than duplicates `/clinical/inpatient/*`: those surfaces are ward-centred (a
 * board of everyone), this one is patient-centred and lives in the chart. The reason it exists is
 * that a medical round is a review of the patient's problems, and no existing round surface shows
 * the problem list at all.
 *
 * Known limitation, stated rather than papered over: a ward-round entry is free text on the server
 * (`assessment` / `plan` / `new_orders`). This screen puts the problem list beside the entry so the
 * clinician writes with it in view, but it does **not** claim the entry is structurally linked to a
 * problem, because it is not. Linking them needs a column inpatient-service does not have.
 */
export interface MedicalWardRoundShellProps {
  patientId: string;
}

export function MedicalWardRoundShell({ patientId }: MedicalWardRoundShellProps) {
  const context = useMedicalWardContext(patientId);
  const startRound = useStartWardRound();
  const addEntry = useAddWardRoundEntry();
  const [assessment, setAssessment] = useState("");
  const [plan, setPlan] = useState("");

  if (context.isLoading) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="ward-round-loading">
        Loading the admission…
      </p>
    );
  }

  return (
    <div className="space-y-4" data-testid="medical-ward-round">
      {context.unavailable.length > 0 && (
        <div className="flex items-start gap-3 rounded-lg border border-danger/30 bg-red-50 p-4" data-testid="ward-unavailable">
          <AlertTriangle className="w-5 h-5 text-danger mt-0.5" />
          <div>
            <p className="font-medium text-danger">Could not load: {context.unavailable.join(", ")}</p>
            <p className="text-sm text-muted-foreground">
              This is not the same as the patient having none. Do not round against the missing
              sections.
            </p>
          </div>
        </div>
      )}

      <AdmissionBanner context={context} />

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-lg border border-border bg-card p-5 space-y-2" data-testid="ward-problems">
          <div className="flex items-center gap-2">
            <Stethoscope className="w-4 h-4 text-primary" />
            <h3 className="font-semibold text-sm">Active problems</h3>
          </div>
          {context.unavailable.includes("problem list") ? (
            <p className="text-sm text-danger" data-testid="ward-problems-unavailable">
              The problem list could not be read — this is not a statement that there are none.
            </p>
          ) : context.problems.active.length === 0 ? (
            <p className="text-sm text-muted-foreground" data-testid="ward-problems-empty">
              No active problems are recorded.
            </p>
          ) : (
            <ul className="space-y-1 text-sm">
              {context.problems.active.map((c) => (
                <li key={c.id} className="flex items-center justify-between">
                  <span>{c.attributes.conditionName}</span>
                  <span className="text-xs text-muted-foreground">{c.attributes.icdCode ?? "—"}</span>
                </li>
              ))}
            </ul>
          )}
          {context.openProgrammes.length > 0 && (
            <p className="text-xs text-muted-foreground" data-testid="ward-programmes">
              On: {context.openProgrammes.map((e) => programmeLabel(e.programme)).join(", ")}
            </p>
          )}
          <Link href={`/ehr/${patientId}/medicine`} className="inline-block text-sm text-primary hover:underline">
            Medicine workspace →
          </Link>
        </div>

        <div className="rounded-lg border border-border bg-card p-5 space-y-2" data-testid="ward-round-entry">
          <h3 className="font-semibold text-sm">Round entry</h3>
          {context.admissionState !== "ADMITTED" ? (
            <p className="text-sm text-muted-foreground" data-testid="entry-requires-admission">
              A round entry needs an active admission.
            </p>
          ) : (
            <>
              <textarea
                className="w-full rounded border border-border px-2 py-1 text-sm" rows={3}
                placeholder="Assessment" value={assessment}
                onChange={(e) => setAssessment(e.target.value)} data-testid="field-assessment"
              />
              <textarea
                className="w-full rounded border border-border px-2 py-1 text-sm" rows={3}
                placeholder="Plan" value={plan}
                onChange={(e) => setPlan(e.target.value)} data-testid="field-plan"
              />
              <button
                type="button"
                disabled={!assessment.trim() || !plan.trim() || addEntry.isPending || startRound.isPending}
                onClick={() =>
                  startRound.mutate(
                    { admissionRef: context.admissionRef as string, roundType: "MORNING" },
                    {
                      onSuccess: (created) => {
                        const rec = (created as { data?: Record<string, unknown> })?.data;
                        const roundId = String(rec?.roundId ?? rec?.round_id ?? "");
                        if (!roundId) return;
                        addEntry.mutate({
                          roundId, admissionRef: context.admissionRef as string,
                          assessment: assessment.trim(), plan: plan.trim(),
                        });
                      },
                    },
                  )
                }
                className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-60"
                data-testid="save-round-entry"
              >
                {addEntry.isPending || startRound.isPending ? <Loader2 className="w-4 h-4 animate-spin inline" /> : "Record round entry"}
              </button>
              {(addEntry.isError || startRound.isError) && (
                <p className="text-sm text-danger" data-testid="entry-failed">
                  The entry was not saved. Do not treat it as recorded — write it again or escalate.
                </p>
              )}
              <p className="text-xs text-muted-foreground">
                Entries are free text on the server; they are not yet structurally linked to the
                problems above.
              </p>
            </>
          )}
        </div>
      </div>

      <div className="rounded-lg border border-border bg-card p-5" data-testid="ward-round-history">
        <h3 className="font-semibold text-sm mb-2">Previous rounds</h3>
        {context.unavailable.includes("ward rounds") ? (
          <p className="text-sm text-danger">Ward rounds could not be read — earlier rounds may exist.</p>
        ) : context.rounds.length === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="rounds-empty">
            No ward rounds recorded for this admission.
          </p>
        ) : (
          <p className="text-sm text-muted-foreground">{context.rounds.length} round(s) recorded.</p>
        )}
      </div>
    </div>
  );
}

function AdmissionBanner({ context }: { context: ReturnType<typeof useMedicalWardContext> }) {
  if (context.admissionState === "UNKNOWN") {
    return (
      <div className="rounded-lg border border-warning/40 bg-amber-50 p-4" data-testid="admission-unknown">
        <p className="text-sm text-warning-foreground">
          {context.facilityId
            ? "Whether this patient is admitted could not be determined."
            : "No facility is selected, so admission status has not been checked."}{" "}
          This is <strong>not</strong> a statement that they are not admitted.
        </p>
      </div>
    );
  }
  if (context.admissionState === "NOT_ADMITTED") {
    return (
      <div className="rounded-lg border border-border bg-muted/30 p-4" data-testid="admission-none">
        <p className="text-sm text-muted-foreground">
          This patient has no active admission at the selected facility.
        </p>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-2 rounded-lg border border-border bg-card p-4" data-testid="admission-active">
      <BedDouble className="w-4 h-4 text-primary" />
      <p className="text-sm">
        Admitted · <span className="text-muted-foreground">{String(context.admission?.wardName ?? context.admission?.wardId ?? "ward not stated")}</span>
      </p>
    </div>
  );
}
