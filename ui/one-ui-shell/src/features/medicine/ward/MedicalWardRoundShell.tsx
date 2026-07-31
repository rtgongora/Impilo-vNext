"use client";

import { useState } from "react";
import Link from "next/link";
import { AlertTriangle, BedDouble, Loader2, Stethoscope } from "lucide-react";
import { useMedicalWardContext } from "./useMedicalWardContext";
import { WardPanel } from "./WardPanel";
import { useStartWardRound, useAddWardRoundEntry } from "@/hooks/queries/useInpatient";
import { programmeLabel } from "../workspace/medicine-summary";

/**
 * The medical ward round, for one admitted patient.
 *
 * Wave 5.3 composes brief.md §13's eighteen ward concerns from existing reads where they exist,
 * and states honestly where no SoR/read is wired on this surface yet.
 *
 * Complements rather than duplicates `/clinical/inpatient/*`: those surfaces are ward-centred (a
 * board of everyone), this one is patient-centred and lives in the chart.
 */

export const WARD_ROUND_TYPES = [
  "MORNING",
  "POST_TAKE",
  "CONSULTANT",
  "HANDOVER",
  "DETERIORATION",
  "TRANSFER",
  "DISCHARGE",
] as const;

export type WardRoundType = (typeof WARD_ROUND_TYPES)[number];

export interface MedicalWardRoundShellProps {
  patientId: string;
}

function prescriptionLabel(
  items: Array<{ medication?: string; dosage?: string }> | undefined,
): string {
  if (!items || items.length === 0) return "Prescription";
  return items
    .map((i) => [i.medication, i.dosage].filter(Boolean).join(" "))
    .filter(Boolean)
    .join(", ");
}

export function MedicalWardRoundShell({ patientId }: MedicalWardRoundShellProps) {
  const context = useMedicalWardContext(patientId);
  const startRound = useStartWardRound();
  const addEntry = useAddWardRoundEntry();
  const [assessment, setAssessment] = useState("");
  const [plan, setPlan] = useState("");
  const [roundType, setRoundType] = useState<WardRoundType>("MORNING");

  if (context.isLoading) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="ward-round-loading">
        Loading the admission…
      </p>
    );
  }

  const problemsUnavailable = context.unavailable.includes("problem list");
  const severityUnavailable = problemsUnavailable;
  const allergiesUnavailable = context.unavailable.includes("allergies");
  const medicinesUnavailable = context.unavailable.includes("current medicines");
  const consultationsUnavailable = context.unavailable.includes("consultations");
  const fluidUnavailable = context.unavailable.includes("fluid balance");

  const openConsultations = context.consultations.filter(
    (c) => c.status === "REQUESTED" || c.status === "ACCEPTED",
  );

  return (
    <div className="space-y-4" data-testid="medical-ward-round">
      {context.unavailable.length > 0 && (
        <div
          className="flex items-start gap-3 rounded-lg border border-danger/30 bg-red-50 p-4"
          data-testid="ward-unavailable"
        >
          <AlertTriangle className="w-5 h-5 text-danger mt-0.5" />
          <div>
            <p className="font-medium text-danger">
              Could not load: {context.unavailable.join(", ")}
            </p>
            <p className="text-sm text-muted-foreground">
              This is not the same as the patient having none. Do not round against the missing
              sections.
            </p>
          </div>
        </div>
      )}

      <AdmissionBanner context={context} />

      <div
        className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"
        data-testid="ward-concerns-grid"
      >
        <WardPanel
          title="Active problems"
          testId="ward-problems"
          status={
            problemsUnavailable ? "unavailable" : context.problems.active.length === 0 ? "empty" : "loaded"
          }
          emptyMessage="No active problems are recorded."
        >
          <ul className="space-y-1 text-sm">
            {context.problems.active.map((c) => (
              <li key={c.id} className="flex items-center justify-between">
                <span>{c.attributes.conditionName}</span>
                <span className="text-xs text-muted-foreground">{c.attributes.icdCode ?? "—"}</span>
              </li>
            ))}
          </ul>
          {context.openProgrammes.length > 0 && (
            <p className="text-xs text-muted-foreground pt-2" data-testid="ward-programmes">
              On: {context.openProgrammes.map((e) => programmeLabel(e.programme)).join(", ")}
            </p>
          )}
          <Link
            href={`/ehr/${patientId}/medicine`}
            className="inline-block pt-1 text-sm text-primary hover:underline"
          >
            Medicine workspace →
          </Link>
        </WardPanel>

        <WardPanel
          title="Severity"
          testId="ward-severity"
          status={
            severityUnavailable
              ? "unavailable"
              : context.activeSeverity.length === 0
                ? "empty"
                : "loaded"
          }
          emptyMessage="No active problems with recorded severity."
        >
          <ul className="space-y-1 text-sm">
            {context.activeSeverity.map((p) => (
              <li key={p.id} className="flex items-center justify-between">
                <span>{p.conditionName}</span>
                <span className="text-xs font-medium uppercase text-muted-foreground">{p.severity}</span>
              </li>
            ))}
          </ul>
        </WardPanel>

        <WardPanel
          title="Allergies"
          testId="ward-allergies"
          status={
            allergiesUnavailable
              ? "unavailable"
              : context.allergyLabels.length === 0
                ? "empty"
                : "loaded"
          }
          emptyMessage="No allergies recorded."
        >
          <ul className="space-y-1 text-sm">
            {context.allergyLabels.map((label) => (
              <li key={label}>{label}</li>
            ))}
          </ul>
          <Link
            href={`/ehr/${patientId}/allergies`}
            className="inline-block pt-1 text-xs text-primary hover:underline"
          >
            Allergy record →
          </Link>
        </WardPanel>

        <WardPanel
          title="Current medicines"
          testId="ward-medicines"
          status={
            medicinesUnavailable
              ? "unavailable"
              : context.prescriptions.length === 0
                ? "empty"
                : "loaded"
          }
          emptyMessage="No prescriptions on record."
        >
          <ul className="space-y-1 text-sm">
            {context.prescriptions.map((rx) => (
              <li key={rx.id} className="flex justify-between gap-2">
                <span>{prescriptionLabel(rx.attributes?.items)}</span>
                <span className="text-xs uppercase text-muted-foreground">
                  {String(rx.attributes?.status ?? "UNKNOWN")}
                </span>
              </li>
            ))}
          </ul>
          <Link
            href={`/pharmacy/prescriptions?patientId=${encodeURIComponent(patientId)}`}
            className="inline-block pt-1 text-xs text-primary hover:underline"
          >
            Pharmacy workspace →
          </Link>
        </WardPanel>

        <WardPanel title="Oxygen" testId="ward-oxygen" status="notComposed" />
        <WardPanel title="Devices" testId="ward-devices" status="notComposed" />

        <WardPanel
          title="Fluid balance"
          testId="ward-fluid-balance"
          status={
            fluidUnavailable
              ? "unavailable"
              : context.fluidBalance.entries.length === 0
                ? "empty"
                : "loaded"
          }
          emptyMessage={`No fluid balance entries for ${context.fluidBalance.date}.`}
        >
          {context.fluidBalance.summary && (
            <p className="text-sm">
              Intake {context.fluidBalance.summary.totalIntake} ml · Output{" "}
              {context.fluidBalance.summary.totalOutput} ml · Balance{" "}
              {context.fluidBalance.summary.balance} ml
            </p>
          )}
          <Link
            href={`/ehr/${patientId}/vitals`}
            className="inline-block pt-1 text-xs text-primary hover:underline"
          >
            Fluid balance record →
          </Link>
        </WardPanel>

        <WardPanel title="Nutrition" testId="ward-nutrition" status="notComposed" />
        <WardPanel title="Mobility" testId="ward-mobility" status="notComposed" />
        <WardPanel title="VTE risk" testId="ward-vte" status="notComposed" />
        <WardPanel title="Infection status" testId="ward-infection" status="notComposed" />
        <WardPanel title="Pending results" testId="ward-pending-results" status="notComposed" />
        <WardPanel title="Required observations" testId="ward-required-obs" status="notComposed" />

        <WardPanel
          title="Consultations"
          testId="ward-consultations"
          status={
            consultationsUnavailable
              ? "unavailable"
              : openConsultations.length === 0
                ? "empty"
                : "loaded"
          }
          emptyMessage="No open consultations."
        >
          <ul className="space-y-1 text-sm">
            {openConsultations.map((c) => (
              <li key={c.consultation_id}>
                <span className="font-medium">{c.asked_of_service}</span>
                <span className="text-muted-foreground"> · {c.status}</span>
                <p className="text-xs text-muted-foreground truncate">{c.question}</p>
              </li>
            ))}
          </ul>
          <Link
            href={`/ehr/${patientId}/consultations`}
            className="inline-block pt-1 text-xs text-primary hover:underline"
          >
            Consultations and MDT →
          </Link>
        </WardPanel>

        <WardPanel title="Goals for the day" testId="ward-goals" status="notComposed" />
        <WardPanel title="Discharge barriers" testId="ward-discharge-barriers" status="notComposed" />
        <WardPanel title="Escalation plan" testId="ward-escalation" status="notComposed" />
        <WardPanel title="Ceiling of care" testId="ward-ceiling-of-care" status="notComposed" />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-lg border border-border bg-card p-5 space-y-2" data-testid="ward-round-entry">
          <h3 className="font-semibold text-sm">Round entry</h3>
          {context.admissionState !== "ADMITTED" ? (
            <p className="text-sm text-muted-foreground" data-testid="entry-requires-admission">
              A round entry needs an active admission.
            </p>
          ) : (
            <>
              <label className="block text-xs text-muted-foreground" htmlFor="ward-round-type">
                Round type
              </label>
              <select
                id="ward-round-type"
                value={roundType}
                onChange={(e) => setRoundType(e.target.value as WardRoundType)}
                className="w-full rounded border border-border px-2 py-1 text-sm bg-card"
                data-testid="field-round-type"
              >
                {WARD_ROUND_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
              <textarea
                className="w-full rounded border border-border px-2 py-1 text-sm"
                rows={3}
                placeholder="Assessment"
                value={assessment}
                onChange={(e) => setAssessment(e.target.value)}
                data-testid="field-assessment"
              />
              <textarea
                className="w-full rounded border border-border px-2 py-1 text-sm"
                rows={3}
                placeholder="Plan"
                value={plan}
                onChange={(e) => setPlan(e.target.value)}
                data-testid="field-plan"
              />
              <button
                type="button"
                disabled={!assessment.trim() || !plan.trim() || addEntry.isPending || startRound.isPending}
                onClick={() =>
                  startRound.mutate(
                    { admissionRef: context.admissionRef as string, roundType },
                    {
                      onSuccess: (created) => {
                        const rec = (created as { data?: Record<string, unknown> })?.data;
                        const roundId = String(rec?.roundId ?? rec?.round_id ?? "");
                        if (!roundId) return;
                        addEntry.mutate({
                          roundId,
                          admissionRef: context.admissionRef as string,
                          assessment: assessment.trim(),
                          plan: plan.trim(),
                        });
                      },
                    },
                  )
                }
                className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-60"
                data-testid="save-round-entry"
              >
                {addEntry.isPending || startRound.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin inline" />
                ) : (
                  "Record round entry"
                )}
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
    <div
      className="flex items-center gap-2 rounded-lg border border-border bg-card p-4"
      data-testid="admission-active"
    >
      <BedDouble className="w-4 h-4 text-primary" />
      <p className="text-sm">
        Admitted ·{" "}
        <span className="text-muted-foreground">
          {String(context.admission?.wardName ?? context.admission?.wardId ?? "ward not stated")}
        </span>
      </p>
    </div>
  );
}
