"use client";

/**
 * Care programmes — HIV and TB longitudinal programme enrolment, regimen and monitoring (W3).
 * Route: /ehr/[patientId]/programmes | pageTitle: "Care programmes"
 *
 * HIV enrolments are confidential (the confidential-lane badge); TB is not. The SPECIALLY_PROTECTED
 * enforcement that will filter HIV content ships in SHADOW — this surface presents the flag and the
 * governed guidance, and does not itself enforce disclosure.
 */

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { AlertTriangle, Loader2, Lock, ShieldAlert, Stethoscope } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useProgrammeEnrolments,
  useProgrammeRegimens,
  useProgrammeGuidance,
  type ProgrammeEnrolment } from "@/hooks/queries/usePrograms";

const PROGRAMME_LABEL: Record<string, string> = {
  HIV_CARE: "HIV care",
  TB_TREATMENT: "TB treatment",
  HIV_PREVENTION: "HIV prevention (PrEP)",
  TB_PREVENTION: "TB preventive therapy" };

const STATUS_BADGE: Record<string, string> = {
  SCREENING: "bg-yellow-100 text-yellow-700",
  ENROLLED: "bg-blue-100 text-blue-700",
  ON_TREATMENT: "bg-green-100 text-green-700",
  TREATMENT_INTERRUPTED: "bg-orange-100 text-orange-700",
  EXITED: "bg-neutral-100 text-muted-foreground" };

export default function ProgrammesPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading, isError: programmesUnavailable } = useProgrammeEnrolments(patientId);
  const enrolments: ProgrammeEnrolment[] = data?.data ?? [];

  return (
    <EHRLayout>
      <PageShell title="Care programmes" subtitle="HIV and TB longitudinal programme enrolment and monitoring">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading programmes…</span>
          </div>
        ) : programmesUnavailable ? (
          // A failed read must never render as "not in any programme" — that is an affirmative
          // clinical claim this call did not earn the right to make.
          <div className="flex items-start gap-3 rounded-lg border border-danger/30 bg-red-50 p-4">
            <AlertTriangle className="w-5 h-5 text-danger mt-0.5" />
            <div>
              <p className="font-medium text-danger">Programmes could not be loaded</p>
              <p className="text-sm text-muted-foreground">
                This is not the same as the patient being in no programme. Retry, and escalate if it
                persists — do not record clinical decisions against an assumed-empty programme list.
              </p>
            </div>
          </div>
        ) : enrolments.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
            No HIV or TB programme enrolment is recorded for this patient.
          </div>
        ) : (
          <div className="space-y-4">
            {enrolments.map((e) => (
              <EnrolmentCard key={e.enrolment_id} enrolment={e} />
            ))}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}

function EnrolmentCard({ enrolment }: { enrolment: ProgrammeEnrolment }) {
  const { data, isError: regimensUnavailable } = useProgrammeRegimens(enrolment.enrolment_id);
  const regimens = data?.data ?? [];
  const current = regimens.find((r) => r.current);

  return (
    <div className="rounded-lg border border-border bg-card p-5 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Stethoscope className="w-5 h-5 text-primary" />
          <h3 className="font-semibold">{PROGRAMME_LABEL[enrolment.programme] ?? enrolment.programme}</h3>
          {enrolment.confidential && (
            <span className="inline-flex items-center gap-1 rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium text-warning-foreground">
              <Lock className="w-3 h-3" /> Confidential
            </span>
          )}
        </div>
        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_BADGE[enrolment.status] ?? "bg-neutral-100 text-muted-foreground"}`}>
          {enrolment.status.replace(/_/g, " ")}
        </span>
      </div>

      <dl className="grid grid-cols-2 gap-x-6 gap-y-1 text-sm">
        <dt className="text-muted-foreground">Enrolled</dt>
        <dd>{enrolment.enrolled_on ?? "—"}</dd>
        {enrolment.programme_number && (
          <>
            <dt className="text-muted-foreground">Programme number</dt>
            <dd>{enrolment.programme_number}</dd>
          </>
        )}
        {enrolment.status === "EXITED" && (
          <>
            <dt className="text-muted-foreground">Exit</dt>
            <dd>{enrolment.exit_reason?.replace(/_/g, " ")} · {enrolment.exit_on ?? "—"}</dd>
          </>
        )}
      </dl>

      <div className="border-t border-border pt-3">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground mb-1">Current regimen</p>
        {regimensUnavailable ? (
          <p className="text-sm text-danger">
            Regimens could not be read — do not treat this as the patient being on no treatment.
          </p>
        ) : current ? (
          <p className="text-sm">
            <span className="font-medium">{current.regimen_display ?? current.regimen_code}</span>
            <span className="text-muted-foreground"> · {current.regimen_stage.replace(/_/g, " ")} · since {current.started_on ?? "—"}</span>
          </p>
        ) : (
          <p className="text-sm text-muted-foreground">No regimen recorded on this enrolment yet.</p>
        )}
      </div>

      {enrolment.confidential && enrolment.programme === "HIV_CARE" && <HivGuidancePanel />}
      {enrolment.programme === "TB_TREATMENT" && <TbGuidancePanel />}
    </div>
  );
}

function HivGuidancePanel() {
  const guidance = useProgrammeGuidance();
  const [vl, setVl] = useState("");
  const result = guidance.data?.data;

  return (
    <GuidancePanel
      title="Viral-load guidance"
      onEvaluate={() => guidance.mutate({ programme: "HIV_CARE", facts: vl ? { viralLoadCopies: Number(vl) } : {} })}
      pending={guidance.isPending}
      failed={guidance.isError}
      result={result}
      input={
        <input
          type="number"
          value={vl}
          onChange={(ev) => setVl(ev.target.value)}
          placeholder="Viral load (copies/mL)"
          className="rounded border border-border px-2 py-1 text-sm w-48"
        />
      }
    />
  );
}

function TbGuidancePanel() {
  const guidance = useProgrammeGuidance();
  const [month, setMonth] = useState("");
  const [smear, setSmear] = useState("");
  const result = guidance.data?.data;

  return (
    <GuidancePanel
      title="TB monitoring guidance"
      onEvaluate={() =>
        guidance.mutate({
          programme: "TB_TREATMENT",
          facts: {
            ...(month ? { treatmentMonth: Number(month) } : {}),
            ...(smear ? { smearResult: smear } : {}),
          },
        })
      }
      pending={guidance.isPending}
      failed={guidance.isError}
      result={result}
      input={
        <div className="flex gap-2">
          <input type="number" value={month} onChange={(ev) => setMonth(ev.target.value)} placeholder="Treatment month" className="rounded border border-border px-2 py-1 text-sm w-32" />
          <select value={smear} onChange={(ev) => setSmear(ev.target.value)} className="rounded border border-border px-2 py-1 text-sm">
            <option value="">Smear…</option>
            <option value="POSITIVE">Positive</option>
            <option value="NEGATIVE">Negative</option>
          </select>
        </div>
      }
    />
  );
}

function GuidancePanel({
  title,
  onEvaluate,
  pending,
  failed,
  result,
  input }: {
  title: string;
  onEvaluate: () => void;
  pending: boolean;
  failed: boolean;
  result?: { alerts: Array<{ code: string; severity: string; message: string; required_action: string }>; incomplete: boolean; not_assessed: string[] } | undefined;
}) {
  return (
    <div className="border-t border-border pt-3 space-y-2">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{title}</p>
      <div className="flex items-center gap-2">
        {input}
        <button
          type="button"
          onClick={onEvaluate}
          disabled={pending}
          className="rounded bg-primary px-3 py-1 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          {pending ? "Evaluating…" : "Evaluate"}
        </button>
      </div>
      {failed && (
        <p className="text-sm text-danger">
          Guidance could not be evaluated — the absence of alerts here is not an all-clear.
        </p>
      )}
      {result && (
        <div className="space-y-2">
          {result.alerts.length === 0 ? (
            <p className="text-sm text-muted-foreground">No alerts fired for the values entered.</p>
          ) : (
            result.alerts.map((a) => (
              <div key={a.code} className="flex items-start gap-2 rounded border border-border bg-muted/30 p-2">
                <ShieldAlert className="w-4 h-4 text-warning-foreground mt-0.5" />
                <div className="text-sm">
                  <p className="font-medium">{a.message}</p>
                  <p className="text-muted-foreground">{a.required_action}</p>
                </div>
              </div>
            ))
          )}
          {result.incomplete && (
            <p className="text-xs text-orange-700">
              Incomplete — not assessed: {result.not_assessed.join(", ")}. This is not an all-clear.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
