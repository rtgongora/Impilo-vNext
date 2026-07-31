"use client";

/**
 * Theatre surgical discharge-summary panel. Surfaces the Wave-4 inpatient SurgicalDischargeService:
 * draft a structured discharge summary, see the pending-histopathology snapshot, then complete it —
 * which composes a FHIR Composition to Butano/SHR and best-effort books the follow-up. Draft vs
 * COMPLETED status, pending-pathology and the Butano document reference are the service's truth.
 * No mocks; an unstarted case shows an honest empty state ({status:"NONE"}).
 * Binds: /internal/v1/theatre/cases/{id}/discharge, /discharge/complete
 */

import { useCallback, useEffect, useState } from "react";
import { FileText, Loader2, RefreshCw, CheckCircle2, FlaskConical, AlertTriangle } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface Discharge {
  status?: string;
  final_diagnosis?: string | null;
  procedure_summary?: string | null;
  complications?: string | null;
  wound_status?: string | null;
  drain_status?: string | null;
  warning_signs?: string | null;
  rehab_plan?: string | null;
  follow_up_service?: string | null;
  follow_up_date?: string | null;
  follow_up_booking_ref?: string | null;
  follow_up_is_teleconsult?: boolean;
  patient_instructions?: string | null;
  provider_summary?: string | null;
  sick_leave_days?: number | null;
  pending_pathology?: unknown[];
  butano_document_ref?: string | null;
  completed_at?: string | null;
}

function unwrap<T>(res: unknown, fallback: T): T {
  const o = res as { data?: T };
  return o && o.data !== undefined ? (o.data as T) : (res as T) ?? fallback;
}
function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string }; status?: number };
  if (o?.error?.message) return o.error.message;
  if (o?.status === 409) return "This discharge summary is already completed.";
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Action failed. Please try again.";
}

export function TheatreSurgicalDischargePanel({ caseId }: { caseId: string }) {
  const [record, setRecord] = useState<Discharge | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadUnavailable, setLoadUnavailable] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const [finalDiagnosis, setFinalDiagnosis] = useState("");
  const [procedureSummary, setProcedureSummary] = useState("");
  const [complications, setComplications] = useState("");
  const [woundStatus, setWoundStatus] = useState("");
  const [drainStatus, setDrainStatus] = useState("");
  const [warningSigns, setWarningSigns] = useState("");
  const [rehabPlan, setRehabPlan] = useState("");
  const [followUpService, setFollowUpService] = useState("");
  const [followUpDate, setFollowUpDate] = useState("");
  const [followUpTele, setFollowUpTele] = useState(false);
  const [patientInstructions, setPatientInstructions] = useState("");
  const [sickLeaveDays, setSickLeaveDays] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const r = await apiClient.get(`/internal/v1/theatre/cases/${caseId}/discharge`);
      const d = unwrap<Discharge>(r, { status: "NONE" });
      setRecord(d);
      setLoadUnavailable(false);
      // hydrate form from any existing draft so edits build on it
      if (d && d.status && d.status !== "NONE") {
        setFinalDiagnosis(d.final_diagnosis ?? "");
        setProcedureSummary(d.procedure_summary ?? "");
        setComplications(d.complications ?? "");
        setWoundStatus(d.wound_status ?? "");
        setDrainStatus(d.drain_status ?? "");
        setWarningSigns(d.warning_signs ?? "");
        setRehabPlan(d.rehab_plan ?? "");
        setFollowUpService(d.follow_up_service ?? "");
        setFollowUpDate(d.follow_up_date ?? "");
        setFollowUpTele(d.follow_up_is_teleconsult === true);
        setPatientInstructions(d.patient_instructions ?? "");
        setSickLeaveDays(d.sick_leave_days != null ? String(d.sick_leave_days) : "");
      }
    } catch {
      // Distinguish transport failure from genuine {status:NONE} — keep last known record.
      setLoadUnavailable(true);
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  useEffect(() => {
    void load();
  }, [load]);

  const act = useCallback(
    async (fn: () => Promise<unknown>, ok: string) => {
      setBusy(true);
      setMsg(null);
      try {
        await fn();
        setMsg(ok);
        await load();
      } catch (e) {
        setMsg(errMessage(e));
      } finally {
        setBusy(false);
      }
    },
    [load],
  );

  const draftBody = () => ({
    ...(finalDiagnosis ? { finalDiagnosis } : {}),
    ...(procedureSummary ? { procedureSummary } : {}),
    ...(complications ? { complications } : {}),
    ...(woundStatus ? { woundStatus } : {}),
    ...(drainStatus ? { drainStatus } : {}),
    ...(warningSigns ? { warningSigns } : {}),
    ...(rehabPlan ? { rehabPlan } : {}),
    ...(followUpService ? { followUpService } : {}),
    ...(followUpDate ? { followUpDate } : {}),
    followUpIsTeleconsult: followUpTele,
    ...(patientInstructions ? { patientInstructions } : {}),
    ...(sickLeaveDays.trim() !== "" ? { sickLeaveDays: Number(sickLeaveDays) } : {}),
  });

  const saveDraft = () =>
    act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/discharge`, draftBody()), "Discharge summary saved as a draft.");
  const complete = () =>
    act(() => apiClient.post(`/internal/v1/theatre/cases/${caseId}/discharge/complete`, draftBody()), "Discharge summary completed and published to the record.");

  const status = loadUnavailable && !record ? "UNKNOWN" : (record?.status ?? "NONE");
  const completed = status === "COMPLETED";
  const pendingPathology = Array.isArray(record?.pending_pathology) ? record!.pending_pathology! : [];

  return (
    <section className="rounded-xl border border-border bg-card p-4" data-testid="surgical-discharge-panel">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 text-sm font-semibold"><FileText className="h-4 w-4" /> Surgical discharge summary</h3>
        <div className="flex items-center gap-2">
          {status !== "NONE" && status !== "UNKNOWN" && <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${completed ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"}`}>{status}</span>}
          <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1 rounded-lg border border-border bg-background px-2 py-1 text-xs hover:bg-card">
            <RefreshCw className="h-3 w-3" /> Refresh
          </button>
        </div>
      </div>
      {msg && <p className="mb-2 rounded-lg border border-border bg-background p-2 text-xs">{msg}</p>}
      {loadUnavailable && (
        <p className="mb-2 flex items-center gap-1 rounded-lg border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800" data-testid="discharge-load-unavailable">
          <AlertTriangle className="h-3.5 w-3.5" /> Discharge summary unavailable — do not treat this as no summary started.
        </p>
      )}
      {loading && <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading discharge summary…</p>}
      {!loading && !loadUnavailable && status === "NONE" && <p className="mb-3 text-xs text-muted-foreground">No discharge summary started for this case.</p>}
      {!loading && loadUnavailable && !record && <p className="mb-3 text-xs text-muted-foreground">Could not load the discharge summary for this case.</p>}

      {completed ? (
        <div className="space-y-2 rounded-lg border border-emerald-200 bg-emerald-50/50 p-3 text-xs" data-testid="discharge-completed">
          <p className="flex items-center gap-1.5 font-medium text-emerald-700"><CheckCircle2 className="h-4 w-4" /> Completed and published to the longitudinal record.</p>
          {record?.butano_document_ref && <p className="text-muted-foreground">SHR document: <span className="font-medium">{record.butano_document_ref}</span></p>}
          {record?.follow_up_booking_ref && <p className="text-muted-foreground">Follow-up booking: <span className="font-medium">{record.follow_up_booking_ref}</span></p>}
          {record?.final_diagnosis && <p className="text-muted-foreground">Diagnosis: {record.final_diagnosis}</p>}
        </div>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Final diagnosis</span><input type="text" value={finalDiagnosis} onChange={(e) => setFinalDiagnosis(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" data-testid="discharge-final-diagnosis" /></label>
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Procedure summary</span><input type="text" value={procedureSummary} onChange={(e) => setProcedureSummary(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Complications</span><input type="text" value={complications} onChange={(e) => setComplications(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Wound status</span><input type="text" value={woundStatus} onChange={(e) => setWoundStatus(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Drain status</span><input type="text" value={drainStatus} onChange={(e) => setDrainStatus(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Warning signs</span><input type="text" value={warningSigns} onChange={(e) => setWarningSigns(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Rehab plan</span><input type="text" value={rehabPlan} onChange={(e) => setRehabPlan(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Follow-up service</span><input type="text" value={followUpService} onChange={(e) => setFollowUpService(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Follow-up date</span><input type="date" value={followUpDate} onChange={(e) => setFollowUpDate(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Sick-leave days</span><input type="number" min={0} value={sickLeaveDays} onChange={(e) => setSickLeaveDays(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          <label className="block text-sm sm:col-span-2"><span className="mb-1 block text-xs font-medium text-muted-foreground">Patient instructions</span><input type="text" value={patientInstructions} onChange={(e) => setPatientInstructions(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
          <label className="flex items-center gap-1.5 text-xs sm:col-span-2"><input type="checkbox" checked={followUpTele} onChange={(e) => setFollowUpTele(e.target.checked)} /> Follow-up is a teleconsult</label>
        </div>
      )}

      {pendingPathology.length > 0 && (
        <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50/40 p-3 text-xs" data-testid="discharge-pending-pathology">
          <p className="flex items-center gap-1.5 font-medium text-amber-800"><FlaskConical className="h-3.5 w-3.5" /> {pendingPathology.length} specimen(s) with pending histopathology</p>
          <p className="mt-1 text-muted-foreground">Results are still outstanding and are snapshotted onto the discharge summary for follow-up.</p>
        </div>
      )}

      {!completed && (
        <div className="mt-3 flex flex-wrap gap-2">
          <button type="button" disabled={busy} onClick={() => void saveDraft()} className="rounded-lg border border-border bg-background px-3 py-1.5 text-sm font-medium hover:bg-card disabled:opacity-50" data-testid="discharge-save-draft">Save draft</button>
          <button type="button" disabled={busy || !finalDiagnosis} onClick={() => void complete()} className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50" data-testid="discharge-complete">Complete &amp; publish</button>
        </div>
      )}
    </section>
  );
}
