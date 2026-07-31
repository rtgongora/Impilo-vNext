"use client";

import Link from "next/link";
import { AlertTriangle, Activity, Lock, ShieldAlert, Stethoscope } from "lucide-react";
import { useMedicineContext } from "./useMedicineContext";
import { isConfidential, programmeLabel } from "./medicine-summary";
import { MEDICINE_SPECIALTIES } from "@/features/medicine/specialties/specialty-config";
import { AmbulatoryOrderSetsPanel } from "@/features/medicine/clerking/AmbulatoryOrderSetsPanel";
import { RenalDosingPanel } from "@/features/medicine/pharm/RenalDosingPanel";
import { JourneyPositionIndicator } from "@/features/medicine/workspace/JourneyPositionIndicator";

/**
 * The adult medicine workspace.
 *
 * The clinical decision support behind this screen has been live for some time — HIV/TB programmes,
 * chronic-disease CDS, specialty guidance — reachable only by API. This is the surface that lets a
 * clinician see it. Every panel is backed by an endpoint that exists; nothing here is a tile that
 * looks available and does nothing.
 *
 * The governing rule throughout: a block that could not be read says so. It never renders as an
 * empty list, because "no programmes" and "the programme list did not load" are different
 * statements and only the first is a clinical claim this screen is entitled to make.
 */
export interface MedicineWorkspaceShellProps {
  patientId: string;
}

export function MedicineWorkspaceShell({ patientId }: MedicineWorkspaceShellProps) {
  const context = useMedicineContext(patientId);

  if (context.isLoading) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="medicine-workspace-loading">
        Loading the medical record…
      </p>
    );
  }

  return (
    <div className="space-y-4" data-testid="medicine-workspace">
      {context.unavailable.length > 0 && (
        <div
          className="flex items-start gap-3 rounded-lg border border-danger/30 bg-red-50 p-4"
          data-testid="medicine-workspace-unavailable"
        >
          <AlertTriangle className="w-5 h-5 text-danger mt-0.5" />
          <div>
            <p className="font-medium text-danger">
              Could not load: {context.unavailable.join(", ")}
            </p>
            <p className="text-sm text-muted-foreground">
              This is not the same as the patient having none. Do not record clinical decisions
              against the missing sections — retry, and escalate if it persists.
            </p>
          </div>
        </div>
      )}

      <JourneyPositionIndicator patientId={patientId} />

      <div className="grid gap-4 lg:grid-cols-2">
        <ProgrammesPanel context={context} />
        <ProblemsPanel context={context} />
      </div>

      <AllergiesStrip context={context} />

      <AmbulatoryOrderSetsPanel patientId={patientId} />

      <RenalDosingPanel patientId={patientId} />

      {/* brief.md §8's thirteen specialties. Each is a view onto this same record, which is the
          point — thirteen separate screens would be the folder of specialist forms the brief
          forbids. Every one states what §8 asked for that is not built. */}
      <div className="rounded-lg border border-border bg-card p-5" data-testid="panel-specialties">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground mb-2">
          Specialty views
        </p>
        <div className="flex flex-wrap gap-2">
          {MEDICINE_SPECIALTIES.map((s) => (
            <Link
              key={s.key}
              href={`/ehr/${patientId}/medicine/specialty/${s.key}`}
              className="rounded border border-border px-2 py-1 text-sm hover:bg-muted"
              data-testid={`link-specialty-${s.key}`}
            >
              {s.label}
            </Link>
          ))}
        </div>
      </div>

      <div className="rounded-lg border border-border bg-card p-5" data-testid="panel-cds">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground mb-2">
          Decision support
        </p>
        <p className="text-sm text-muted-foreground">
          Governed guidance for this patient — cardiovascular risk, medication review, geriatrics,
          mental health, antimicrobial stewardship, palliative care, cancer early diagnosis and
          bedside-procedure appropriateness.
        </p>
        <Link
          href={`/ehr/${patientId}/medicine/cds`}
          className="mt-2 inline-block text-sm text-primary hover:underline"
          data-testid="link-medicine-cds"
        >
          Open decision support →
        </Link>
        <Link
          href={`/ehr/${patientId}/examination`}
          className="mt-2 ml-4 inline-block text-sm text-primary hover:underline"
          data-testid="link-examination"
        >
          Record an examination →
        </Link>
        <Link
          href={`/ehr/${patientId}/consultations`}
          className="mt-2 ml-4 inline-block text-sm text-primary hover:underline"
          data-testid="link-consultations"
        >
          Consultations and MDT →
        </Link>
        <Link
          href={`/ehr/${patientId}/multimorbidity`}
          className="mt-2 ml-4 inline-block text-sm text-primary hover:underline"
          data-testid="link-multimorbidity"
        >
          Open the multimorbidity view →
        </Link>
      </div>
    </div>
  );
}

function ProgrammesPanel({ context }: { context: ReturnType<typeof useMedicineContext> }) {
  const unavailable = context.unavailable.includes("care programmes");
  return (
    <div className="rounded-lg border border-border bg-card p-5 space-y-3" data-testid="panel-programmes">
      <div className="flex items-center gap-2">
        <Activity className="w-4 h-4 text-primary" />
        <h3 className="font-semibold text-sm">Care programmes</h3>
      </div>

      {unavailable ? (
        <p className="text-sm text-danger" data-testid="programmes-unavailable">
          Care programmes could not be read — this is not a statement that the patient is in none.
        </p>
      ) : context.openProgrammes.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="programmes-empty">
          No open HIV or TB programme enrolment is recorded for this patient.
        </p>
      ) : (
        <ul className="space-y-2">
          {context.openProgrammes.map((e) => (
            <li key={e.enrolment_id} className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-2">
                {programmeLabel(e.programme)}
                {isConfidential(e) && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium text-warning-foreground">
                    <Lock className="w-3 h-3" /> Confidential
                  </span>
                )}
              </span>
              <span className="text-muted-foreground">{e.status.replace(/_/g, " ")}</span>
            </li>
          ))}
        </ul>
      )}

      {/* An ended enrolment is a clinical fact — lost to follow-up especially — so it is shown
          rather than filtered away into the same blank space as "never enrolled". */}
      {!unavailable && context.exitedProgrammes.length > 0 && (
        <p className="text-xs text-muted-foreground" data-testid="programmes-exited">
          {context.exitedProgrammes.length} ended enrolment
          {context.exitedProgrammes.length === 1 ? "" : "s"} (
          {context.exitedProgrammes
            .map((e) => `${programmeLabel(e.programme)}: ${(e.exit_reason ?? "exited").replace(/_/g, " ").toLowerCase()}`)
            .join("; ")}
          )
        </p>
      )}

      <Link
        href={`/ehr/${context.patientId}/programmes`}
        className="inline-block text-sm text-primary hover:underline"
        data-testid="link-programmes"
      >
        Manage programmes →
      </Link>
    </div>
  );
}

function ProblemsPanel({ context }: { context: ReturnType<typeof useMedicineContext> }) {
  const unavailable = context.unavailable.includes("problem list");
  const { active, remission, inactive } = context.problems;
  return (
    <div className="rounded-lg border border-border bg-card p-5 space-y-3" data-testid="panel-problems">
      <div className="flex items-center gap-2">
        <Stethoscope className="w-4 h-4 text-primary" />
        <h3 className="font-semibold text-sm">Problem list</h3>
      </div>

      {unavailable ? (
        <p className="text-sm text-danger" data-testid="problems-unavailable">
          The problem list could not be read — this is not a statement that the patient has no
          active conditions.
        </p>
      ) : active.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="problems-empty">
          No active problems are recorded for this patient.
        </p>
      ) : (
        <ul className="space-y-1 text-sm">
          {active.map((c) => (
            <li key={c.id} className="flex items-center justify-between">
              <span>{c.attributes.conditionName}</span>
              <span className="text-xs text-muted-foreground">
                {c.attributes.icdCode ?? "—"} · {c.attributes.clinicalStatus.toLowerCase()}
              </span>
            </li>
          ))}
        </ul>
      )}

      {/* Remission is neither active nor resolved, and saying so is the point. */}
      {!unavailable && (remission.length > 0 || inactive.length > 0) && (
        <p className="text-xs text-muted-foreground" data-testid="problems-other">
          {remission.length > 0 && `${remission.length} in remission`}
          {remission.length > 0 && inactive.length > 0 && " · "}
          {inactive.length > 0 && `${inactive.length} inactive or resolved`}
        </p>
      )}

      <Link
        href={`/ehr/${context.patientId}/conditions`}
        className="inline-block text-sm text-primary hover:underline"
        data-testid="link-conditions"
      >
        Open problem list →
      </Link>
    </div>
  );
}

function AllergiesStrip({ context }: { context: ReturnType<typeof useMedicineContext> }) {
  const unavailable = context.unavailable.includes("allergies");
  return (
    <div className="rounded-lg border border-border bg-card p-4" data-testid="panel-allergies">
      <div className="flex items-center gap-2 mb-1">
        <ShieldAlert className="w-4 h-4 text-warning-foreground" />
        <h3 className="font-semibold text-sm">Allergies</h3>
      </div>
      {unavailable ? (
        <p className="text-sm text-danger" data-testid="allergies-unavailable">
          Allergies could not be read — do not prescribe against this as if it were &quot;no known
          allergies&quot;.
        </p>
      ) : context.allergyLabels.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="allergies-empty">
          No allergies recorded.
        </p>
      ) : (
        <p className="text-sm">{context.allergyLabels.join(", ")}</p>
      )}
    </div>
  );
}
