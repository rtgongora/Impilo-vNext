"use client";

/**
 * Theatre case detail — the full perioperative workflow on one surface. Real data via the
 * experience-BFF (/internal/v1/theatre/cases/{id}/...). Readiness is owner-routed and fails safely
 * with blockers; the WHO Sign-In gates the start; the operative note is signed by a surgeon; PACU
 * disposition can route a death to the PCT death pathway; safety events route to their owner. No fake
 * availability, readiness, checklist completion, or notes.
 * Route: /work/clinical/theatre/[id]
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { Stethoscope, Loader2, RefreshCw, ArrowLeft, ShieldAlert, ClipboardCheck, FileText, Ban, TriangleAlert, Maximize2, Minimize2, Receipt } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { TheatreCaseBanner } from "@/components/clinical/theatre/TheatreCaseBanner";
import { DocumentStateBadge, type DocumentState } from "@/components/clinical/theatre/DocumentStateBadge";
import { TheatreBloodPanel } from "@/components/clinical/theatre/TheatreBloodPanel";
import { TheatreSpecimenPanel } from "@/components/clinical/theatre/TheatreSpecimenPanel";
import { TheatreCountPanel } from "@/components/clinical/theatre/TheatreCountPanel";
import { AnaesthesiaChartPanel } from "@/components/clinical/theatre/AnaesthesiaChartPanel";
import { TheatreCommoditiesPanel } from "@/components/clinical/theatre/TheatreCommoditiesPanel";
import { TheatrePacuPanel } from "@/components/clinical/theatre/TheatrePacuPanel";
import { TheatreSurgicalDischargePanel } from "@/components/clinical/theatre/TheatreSurgicalDischargePanel";
import { TheatreReturnToTheatrePanel, type ReturnToTheatreRecord } from "@/components/clinical/theatre/TheatreReturnToTheatrePanel";
import { EmergencyConsentExceptionPanel } from "@/components/clinical/theatre/EmergencyConsentExceptionPanel";
import { ObstetricSection } from "@/components/clinical/theatre/ObstetricSection";
import { apiClient } from "@/lib/api-client";

interface Blocker { code?: string; message?: string }
interface ReadinessCheck { domain?: string; owner_service?: string; status?: string; owner_ref?: string }
interface ReadinessResult { bookable?: boolean; checks?: ReadinessCheck[]; blockers?: Blocker[] }
interface ChecklistItem { id?: string; phase?: string; item_code?: string; item_label?: string; completed?: boolean }
interface SafetyEvent { id?: string; category?: string; severity?: string; description?: string; routed_owner?: string; routed_status?: string }
interface TheatreCaseDetail {
  id?: string;
  patient_id?: string;
  encounter_id?: string;
  patient_name?: string;
  procedure_name?: string;
  status?: string;
  triage_priority?: string;
  surgeon_id?: string;
  checklist?: ChecklistItem[];
  death_case_ref?: string;
  consent_status?: string;
  emergency_override?: boolean;
  // §19 banner context (optional — surfaced when the record carries them, never fabricated)
  allergies?: string[];
  no_known_allergies?: boolean;
  surgical_site?: string;
  surgical_side?: string;
  operative_note_state?: DocumentState;
  /** V305 — prior returns on this episode; never invent an empty list on a failed case load. */
  returns_to_theatre?: ReturnToTheatreRecord[];
}

const WOUND_CLASSIFICATIONS = ["CLEAN", "CLEAN_CONTAMINATED", "CONTAMINATED", "DIRTY"] as const;

const EMPTY_NOTE = {
  performedProcedure: "",
  findings: "",
  complications: "",
  postopPlan: "",
  signedProviderId: "",
  patientPosition: "",
  skinPreparation: "",
  incision: "",
  operativeSteps: "",
  operativeTechnique: "",
  intraoperativeFluids: "",
  drainsPlaced: "",
  stomasFormed: "",
  closureMethod: "",
  woundClassification: "",
  postoperativeInstructions: "",
  operativeTemplateRef: "",
  operativeTemplateCode: "",
};

type NoteForm = typeof EMPTY_NOTE;

/** Map inpatient noteRow (snake_case) into the form; absent/NONE leaves an empty draft. */
function noteFromResponse(raw: unknown): NoteForm {
  const n = ((raw as { data?: Record<string, unknown> }).data ?? raw) as Record<string, unknown>;
  if (!n || n.status === "NONE") return { ...EMPTY_NOTE };
  const s = (k: string, camel: string) => String(n[k] ?? n[camel] ?? "");
  return {
    ...EMPTY_NOTE,
    performedProcedure: s("performed_procedure", "performedProcedure"),
    findings: s("findings", "findings"),
    complications: s("complications", "complications"),
    postopPlan: s("postop_plan", "postopPlan"),
    signedProviderId: s("signed_provider_id", "signedProviderId"),
    patientPosition: s("patient_position", "patientPosition"),
    skinPreparation: s("skin_preparation", "skinPreparation"),
    incision: s("incision", "incision"),
    operativeSteps: s("operative_steps", "operativeSteps"),
    operativeTechnique: s("operative_technique", "operativeTechnique"),
    intraoperativeFluids: s("intraoperative_fluids", "intraoperativeFluids"),
    drainsPlaced: s("drains_placed", "drainsPlaced"),
    stomasFormed: s("stomas_formed", "stomasFormed"),
    closureMethod: s("closure_method", "closureMethod"),
    woundClassification: s("wound_classification", "woundClassification"),
    postoperativeInstructions: s("postoperative_instructions", "postoperativeInstructions"),
    operativeTemplateRef: s("operative_template_ref", "operativeTemplateRef"),
    operativeTemplateCode: s("operative_template_code", "operativeTemplateCode"),
  };
}

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; blockers?: Blocker[]; status?: number };
    if (obj.blockers && obj.blockers.length) return obj.blockers.map((b) => b.message ?? b.code).join("; ");
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Action failed. Please try again.";
}

export default function TheatreCaseDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";
  const [data, setData] = useState<TheatreCaseDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [readiness, setReadiness] = useState<ReadinessResult | null>(null);
  const [safety, setSafety] = useState<SafetyEvent[]>([]);
  const [overrideReason, setOverrideReason] = useState("");
  const [note, setNote] = useState<NoteForm>({ ...EMPTY_NOTE });
  const [noteUnavailable, setNoteUnavailable] = useState(false);
  const [cancelReasonCode, setCancelReasonCode] = useState("");
  const [cancelReason, setCancelReason] = useState("");
  const [safetyForm, setSafetyForm] = useState({ category: "NEAR_MISS", severity: "MODERATE", description: "" });
  // §19 ergonomics: track the operative-note document state locally after a sign, and a focus
  // mode that auto-hides the surrounding sections during active data entry to minimise scrolling.
  const [noteSigned, setNoteSigned] = useState(false);
  const [focusMode, setFocusMode] = useState(false);

  const templatePairIncomplete =
    (note.operativeTemplateRef.trim() === "") !== (note.operativeTemplateCode.trim() === "");

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    setNoteUnavailable(false);
    try {
      const res = await apiClient.get<TheatreCaseDetail>(`/internal/v1/theatre/cases/${id}`);
      setData((res as { data?: TheatreCaseDetail }).data ?? (res as TheatreCaseDetail));
      const s = await apiClient.get<SafetyEvent[]>(`/internal/v1/theatre/cases/${id}/safety-events`);
      setSafety(Array.isArray(s) ? s : (s as { data?: SafetyEvent[] }).data ?? []);
      try {
        const noteRes = await apiClient.get<Record<string, unknown>>(`/internal/v1/theatre/cases/${id}/note`);
        const hydrated = noteFromResponse(noteRes);
        setNote(hydrated);
        const raw = ((noteRes as { data?: Record<string, unknown> }).data ?? noteRes) as Record<string, unknown>;
        if (raw?.status === "SIGNED") setNoteSigned(true);
      } catch {
        // A failed note read must not look like "no note yet" — leave the form alone and flag it.
        setNoteUnavailable(true);
      }
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, [id]);

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
        void load();
      } catch (e) {
        setMsg(errMessage(e));
      } finally {
        setBusy(false);
      }
    },
    [load],
  );

  const evaluateReadiness = () =>
    act(async () => {
      const r = await apiClient.post<ReadinessResult>(`/internal/v1/theatre/cases/${id}/readiness`, {});
      setReadiness((r as { data?: ReadinessResult }).data ?? (r as ReadinessResult));
    }, "Readiness evaluated. Blockers (if any) are listed below.");

  const book = (override: boolean) =>
    act(
      () =>
        apiClient.post(`/internal/v1/theatre/cases/${id}/book`, override ? { emergencyOverride: true, emergencyOverrideReason: overrideReason } : {}),
      override ? "Booked under an audited emergency override." : "Booked.",
    );

  const start = (override: boolean) =>
    act(
      () =>
        apiClient.post(`/internal/v1/theatre/cases/${id}/start`, override ? { emergencyOverride: true, emergencyOverrideReason: overrideReason } : {}),
      "Theatre started.",
    );

  const draftAndSign = () =>
    act(async () => {
      const payload: Record<string, string> = {
        performedProcedure: note.performedProcedure,
        findings: note.findings,
        complications: note.complications,
        postopPlan: note.postopPlan,
      };
      // SB-5 depth — send only non-empty values so a blank refinement does not erase a stored field.
      const depthKeys: Array<keyof NoteForm> = [
        "patientPosition",
        "skinPreparation",
        "incision",
        "operativeSteps",
        "operativeTechnique",
        "intraoperativeFluids",
        "drainsPlaced",
        "stomasFormed",
        "closureMethod",
        "woundClassification",
        "postoperativeInstructions",
        "operativeTemplateRef",
        "operativeTemplateCode",
      ];
      for (const k of depthKeys) {
        const v = note[k].trim();
        if (v) payload[k] = v;
      }
      await apiClient.post(`/internal/v1/theatre/cases/${id}/note`, payload);
      if (note.signedProviderId) {
        await apiClient.post(`/internal/v1/theatre/cases/${id}/note/sign`, { signedProviderId: note.signedProviderId });
        setNoteSigned(true);
      }
    }, "Operative note saved" + (note.signedProviderId ? " and signed." : " as a draft."));

  const cancel = () =>
    act(
      () =>
        apiClient.post(`/internal/v1/theatre/cases/${id}/cancel`, {
          ...(cancelReasonCode ? { reasonCode: cancelReasonCode } : {}),
          reason: cancelReason,
        }),
      "Case cancelled; owner reservations released.",
    );

  const reportSafety = () =>
    act(() => apiClient.post(`/internal/v1/theatre/cases/${id}/safety-events`, safetyForm), "Safety event routed to its owner.");

  const checklist = data?.checklist ?? [];
  const phases = ["SIGN_IN", "TIME_OUT", "SIGN_OUT"] as const;

  // §19: derive the operative-note document state — FINAL once the case is completed/recovered,
  // otherwise SIGNED (persisted or just signed here), AMENDED when the record says so, else DRAFT.
  const noteState: DocumentState = ["COMPLETED", "RECOVERED"].includes(data?.status ?? "")
    ? "FINAL"
    : noteSigned || data?.operative_note_state === "SIGNED"
      ? "SIGNED"
      : data?.operative_note_state === "AMENDED"
        ? "AMENDED"
        : "DRAFT";

  return (
    <AppLayout>
      <PageShell title="Theatre case" subtitle={data?.procedure_name ?? "Perioperative workflow"} icon={<Stethoscope className="h-5 w-5" />}>
        <div className="mb-4 flex items-center justify-between">
          <Link href="/work/clinical/theatre" className="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline">
            <ArrowLeft className="h-3.5 w-3.5" /> Back to the theatre list
          </Link>
          <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>

        {msg && <p className="mb-4 rounded-lg border border-border bg-card p-3 text-sm">{msg}</p>}

        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading the case…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            <p className="mb-3">{error}</p>
            <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100">
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </button>
          </div>
        ) : !data ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">Case not found.</div>
        ) : (
          <div className="space-y-5">
            {/* §19 PERSISTENT context banner — sticky, always visible through the data-entry journey */}
            <TheatreCaseBanner
              patientId={data.patient_id}
              patientName={data.patient_name}
              procedureName={data.procedure_name}
              status={data.status}
              allergies={data.allergies}
              noKnownAllergies={data.no_known_allergies}
              surgicalSite={data.surgical_site}
              surgicalSide={data.surgical_side}
            />

            {/* Secondary identity chips + focus-mode toggle */}
            <div className="flex flex-wrap items-center gap-3">
              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">{data.triage_priority ?? "ELECTIVE"}</span>
              {data.surgeon_id && <span className="text-xs text-muted-foreground">Surgeon: {data.surgeon_id}</span>}
              {data.death_case_ref && <span className="rounded-full bg-slate-800 px-2 py-0.5 text-xs font-medium text-white">Death case: {data.death_case_ref.slice(0, 8)}</span>}
              {data.encounter_id && (
                <Link
                  href={`/finance/costa/encounter/${data.encounter_id}`}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 py-1 text-xs font-medium text-foreground hover:bg-background"
                  data-testid="view-case-billing"
                >
                  <Receipt className="h-3.5 w-3.5" /> View case billing
                </Link>
              )}
              <button
                type="button"
                aria-pressed={focusMode}
                onClick={() => setFocusMode((v) => !v)}
                className="ml-auto inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 py-1 text-xs font-medium hover:bg-background"
                data-testid="focus-mode-toggle"
              >
                {focusMode ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
                {focusMode ? "Show all sections" : "Focus mode"}
              </button>
            </div>

            {/* Readiness + booking */}
            <section className={`rounded-xl border border-border bg-card p-4${focusMode ? " hidden" : ""}`}>
              <h3 className="mb-3 flex items-center gap-1.5 text-sm font-semibold"><ShieldAlert className="h-4 w-4" /> Readiness & booking</h3>
              <button type="button" disabled={busy} onClick={() => void evaluateReadiness()} className="rounded-lg border border-border bg-background px-3 py-1.5 text-sm font-medium hover:bg-card disabled:opacity-50">
                Evaluate readiness
              </button>
              {readiness && (
                <div className="mt-3 space-y-2">
                  <p className={`text-sm font-medium ${readiness.bookable ? "text-emerald-700" : "text-amber-700"}`}>
                    {readiness.bookable ? "All checks ready — bookable." : "Not bookable yet — owner blockers below."}
                  </p>
                  <div className="overflow-x-auto rounded-lg border border-border">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-background text-muted-foreground"><tr><th className="px-3 py-2">Domain</th><th className="px-3 py-2">Owner</th><th className="px-3 py-2">Status</th></tr></thead>
                      <tbody className="divide-y divide-slate-100">
                        {(readiness.checks ?? []).map((c, i) => (
                          <tr key={i}>
                            <td className="px-3 py-2 font-medium">{c.domain}</td>
                            <td className="px-3 py-2 text-muted-foreground">{c.owner_service}</td>
                            <td className="px-3 py-2">
                              <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${c.status === "READY" ? "bg-emerald-100 text-emerald-700" : c.status === "BLOCKED" ? "bg-amber-100 text-amber-700" : "bg-red-100 text-red-700"}`}>{c.status}</span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {!readiness.bookable && (readiness.blockers ?? []).length > 0 && (
                    <ul className="list-disc pl-5 text-xs text-amber-700">{(readiness.blockers ?? []).map((b, i) => <li key={i}>{b.message ?? b.code}</li>)}</ul>
                  )}
                </div>
              )}
              <div className="mt-3 flex flex-wrap items-end gap-2">
                <button type="button" disabled={busy} onClick={() => void book(false)} className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Book</button>
                <label className="block text-sm">
                  <span className="mb-1 block text-xs font-medium text-muted-foreground">Emergency override reason</span>
                  <input type="text" value={overrideReason} onChange={(e) => setOverrideReason(e.target.value)} className="w-64 rounded-lg border border-border bg-background px-3 py-1.5" />
                </label>
                <button type="button" disabled={busy || !overrideReason} onClick={() => void book(true)} className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-800 hover:bg-amber-100 disabled:opacity-50">Book with override</button>
              </div>
            </section>

            {/* WHO checklist */}
            <section className={`rounded-xl border border-border bg-card p-4${focusMode ? " hidden" : ""}`}>
              <h3 className="mb-3 flex items-center gap-1.5 text-sm font-semibold"><ClipboardCheck className="h-4 w-4" /> WHO Surgical Safety Checklist</h3>
              {checklist.length === 0 ? (
                <p className="text-sm text-muted-foreground">No checklist seeded for this case.</p>
              ) : (
                <div className="grid gap-3 sm:grid-cols-3">
                  {phases.map((ph) => (
                    <div key={ph} className="rounded-lg border border-border p-3">
                      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{ph.replace("_", " ")}</p>
                      <ul className="space-y-1">
                        {checklist.filter((it) => it.phase === ph).map((it) => (
                          <li key={it.id} className="flex items-center gap-1.5 text-xs">
                            <span className={`inline-block h-2 w-2 rounded-full ${it.completed ? "bg-emerald-500" : "bg-slate-300"}`} />
                            {it.item_label}
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))}
                </div>
              )}
              <div className="mt-3 flex flex-wrap items-end gap-2">
                <button type="button" disabled={busy} onClick={() => void start(false)} className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Start theatre</button>
                <button type="button" disabled={busy || !overrideReason} onClick={() => void start(true)} className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-800 hover:bg-amber-100 disabled:opacity-50">Start with override</button>
              </div>
            </section>

            {/* Emergency consent exception — shown for emergency/immediate/override cases */}
            {(["EMERGENCY", "IMMEDIATE"].includes(data.triage_priority ?? "") || data.emergency_override || data.consent_status === "EMERGENCY_EXCEPTION") && (
              <div className={focusMode ? "hidden" : undefined}>
                <EmergencyConsentExceptionPanel caseId={id} consentStatus={data.consent_status} onRecorded={() => void load()} />
              </div>
            )}

            {/* Obstetric emergency caesarean — shown for obstetric procedures */}
            {/(caesar|c-section|section|obstetr|lscs)/i.test(data.procedure_name ?? "") && (
              <div className={focusMode ? "hidden" : undefined}>
                <ObstetricSection caseId={id} />
              </div>
            )}

            {/* Clinical-safety management (Lane 3): blood, specimens, counts, anaesthesia chart */}
            <div className={`grid gap-5${focusMode ? " hidden" : ""}`} data-testid="clinical-management">
              <TheatreBloodPanel caseId={id} />
              <TheatreSpecimenPanel caseId={id} />
              <TheatreCountPanel caseId={id} />
              <AnaesthesiaChartPanel caseId={id} />
            </div>

            {/* Commodities & traceability (Lane 2): implants, instrument sets, controlled drugs */}
            <div className={focusMode ? "hidden" : undefined}>
              <TheatreCommoditiesPanel caseId={id} />
            </div>

            {/* Operative note — data-entry surface; focusing a field auto-enables focus mode */}
            <section
              className="rounded-xl border border-border bg-card p-4"
              onFocusCapture={() => setFocusMode(true)}
              data-testid="operative-note-section"
            >
              <div className="mb-3 flex items-center justify-between gap-2">
                <h3 className="flex items-center gap-1.5 text-sm font-semibold"><FileText className="h-4 w-4" /> Operative note</h3>
                <DocumentStateBadge state={noteState} />
              </div>
              {noteUnavailable && (
                <p className="mb-3 text-sm text-danger" role="alert" data-testid="operative-note-unavailable">
                  Could not read the operative note. This is not the same as no note being recorded.
                </p>
              )}
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Procedure performed</span><input type="text" value={note.performedProcedure} onChange={(e) => setNote({ ...note, performedProcedure: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" data-testid="note-performed-procedure" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Findings</span><input type="text" value={note.findings} onChange={(e) => setNote({ ...note, findings: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Complications</span><input type="text" value={note.complications} onChange={(e) => setNote({ ...note, complications: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Post-op plan</span><input type="text" value={note.postopPlan} onChange={(e) => setNote({ ...note, postopPlan: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
              </div>
              {/* SB-5 operative depth — typed fields the V304 migration added to procedure_note */}
              <p className="mb-2 mt-4 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Operative record depth</p>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Patient position</span><input type="text" value={note.patientPosition} onChange={(e) => setNote({ ...note, patientPosition: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" data-testid="note-patient-position" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Skin preparation</span><input type="text" value={note.skinPreparation} onChange={(e) => setNote({ ...note, skinPreparation: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Incision</span><input type="text" value={note.incision} onChange={(e) => setNote({ ...note, incision: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Operative steps</span><textarea rows={2} value={note.operativeSteps} onChange={(e) => setNote({ ...note, operativeSteps: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Operative technique</span><textarea rows={2} value={note.operativeTechnique} onChange={(e) => setNote({ ...note, operativeTechnique: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Intraoperative fluids</span><input type="text" value={note.intraoperativeFluids} onChange={(e) => setNote({ ...note, intraoperativeFluids: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Drains placed</span><input type="text" value={note.drainsPlaced} onChange={(e) => setNote({ ...note, drainsPlaced: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Stomas formed</span><input type="text" value={note.stomasFormed} onChange={(e) => setNote({ ...note, stomasFormed: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Closure method</span><input type="text" value={note.closureMethod} onChange={(e) => setNote({ ...note, closureMethod: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Wound classification</span>
                  <select value={note.woundClassification} onChange={(e) => setNote({ ...note, woundClassification: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" data-testid="note-wound-classification">
                    <option value="">Not recorded</option>
                    {WOUND_CLASSIFICATIONS.map((w) => <option key={w} value={w}>{w}</option>)}
                  </select>
                </label>
                <label className="block text-sm sm:col-span-2"><span className="mb-1 block text-xs font-medium text-muted-foreground">Postoperative instructions</span><textarea rows={2} value={note.postoperativeInstructions} onChange={(e) => setNote({ ...note, postoperativeInstructions: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Operative template ref (UUID)</span><input type="text" value={note.operativeTemplateRef} onChange={(e) => setNote({ ...note, operativeTemplateRef: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" data-testid="note-template-ref" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Operative template code</span><input type="text" value={note.operativeTemplateCode} onChange={(e) => setNote({ ...note, operativeTemplateCode: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" data-testid="note-template-code" /></label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Sign as (surgeon provider id)</span><input type="text" value={note.signedProviderId} onChange={(e) => setNote({ ...note, signedProviderId: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
              </div>
              {templatePairIncomplete && (
                <p className="mt-2 text-xs text-muted-foreground" data-testid="note-template-pair-required">
                  Template ref and code must be supplied together, or both left blank.
                </p>
              )}
              <button
                type="button"
                disabled={busy || !note.performedProcedure || templatePairIncomplete}
                onClick={() => void draftAndSign()}
                className="mt-3 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
                data-testid="note-save"
              >
                {note.signedProviderId ? "Save & sign note" : "Save draft note"}
              </button>
            </section>

            {/* PACU recovery depth (Aldrete-scored) + gated discharge + disposition */}
            <div className={focusMode ? "hidden" : undefined}>
              <TheatrePacuPanel caseId={id} />
            </div>

            {/* V305 return to theatre — reason + closed complication category */}
            <div className={focusMode ? "hidden" : undefined}>
              <TheatreReturnToTheatrePanel
                caseId={id}
                status={data.status}
                returns={data.returns_to_theatre ?? []}
                onRecorded={() => void load()}
              />
            </div>

            {/* Surgical discharge summary (draft → complete → SHR) */}
            <div className={focusMode ? "hidden" : undefined}>
              <TheatreSurgicalDischargePanel caseId={id} />
            </div>

            {/* Safety + cancel */}
            <section className={`rounded-xl border border-border bg-card p-4${focusMode ? " hidden" : ""}`}>
              <h3 className="mb-3 flex items-center gap-1.5 text-sm font-semibold"><TriangleAlert className="h-4 w-4" /> Safety event</h3>
              <div className="grid gap-3 sm:grid-cols-3">
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Category</span>
                  <select value={safetyForm.category} onChange={(e) => setSafetyForm({ ...safetyForm, category: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5">
                    {["ADVERSE_EVENT", "BLOOD_REACTION", "DEVICE_INCIDENT", "MEDICATION", "NEAR_MISS", "RETAINED_ITEM"].map((c) => <option key={c}>{c}</option>)}
                  </select>
                </label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Severity</span>
                  <select value={safetyForm.severity} onChange={(e) => setSafetyForm({ ...safetyForm, severity: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5">
                    {["LOW", "MODERATE", "SEVERE", "SENTINEL"].map((s) => <option key={s}>{s}</option>)}
                  </select>
                </label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Description</span><input type="text" value={safetyForm.description} onChange={(e) => setSafetyForm({ ...safetyForm, description: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" /></label>
              </div>
              <button type="button" disabled={busy || !safetyForm.description} onClick={() => void reportSafety()} className="mt-3 rounded-lg border border-amber-300 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-800 hover:bg-amber-100 disabled:opacity-50">Report safety event</button>
              {safety.length > 0 && (
                <ul className="mt-3 space-y-1 text-xs">
                  {safety.map((s) => (
                    <li key={s.id} className="flex items-center gap-2">
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 font-medium text-slate-600">{s.category}</span>
                      <span className="text-muted-foreground">→ {s.routed_owner} ({s.routed_status})</span>
                      <span className="text-muted-foreground">{s.description}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {/* Cancel */}
            <section className={`rounded-xl border border-red-200 bg-red-50/40 p-4${focusMode ? " hidden" : ""}`}>
              <h3 className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-red-700"><Ban className="h-4 w-4" /> Cancel case</h3>
              <div className="flex flex-wrap items-end gap-2">
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Reason code</span>
                  <select value={cancelReasonCode} onChange={(e) => setCancelReasonCode(e.target.value)} className="w-56 rounded-lg border border-border bg-background px-3 py-1.5">
                    <option value="">Other (free text)</option>
                    <option value="PATIENT_NOT_FIT">Patient not fit</option>
                    <option value="NO_CONSENT">No consent</option>
                    <option value="NO_BLOOD">No blood</option>
                    <option value="NO_IMPLANT">No implant</option>
                    <option value="EQUIPMENT_FAILURE">Equipment failure</option>
                    <option value="STAFF_UNAVAILABILITY">Staff unavailability</option>
                    <option value="NO_BED">No bed</option>
                    <option value="EMERGENCY_DISPLACEMENT">Emergency displacement</option>
                    <option value="PATIENT_NON_ATTENDANCE">Patient non-attendance</option>
                  </select>
                </label>
                <label className="block text-sm"><span className="mb-1 block text-xs font-medium text-muted-foreground">Notes</span><input type="text" value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} className="w-64 rounded-lg border border-border bg-background px-3 py-1.5" /></label>
                <button type="button" disabled={busy || (!cancelReasonCode && !cancelReason)} onClick={() => void cancel()} className="rounded-lg border border-red-300 bg-white px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-100 disabled:opacity-50">Cancel &amp; release</button>
              </div>
              <p className="mt-1.5 text-xs text-muted-foreground">A structured reason drives rescheduling — most reasons return the case to the surgical waitlist; a patient non-attendance takes it off the list.</p>
            </section>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/clinical/theatre/[id]" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
