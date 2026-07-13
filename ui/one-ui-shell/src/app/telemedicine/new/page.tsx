"use client";

/**
 * New Teleconsultation — Stages 1-2: Create session + Build referral package.
 *
 * Six-component referral builder:
 * ① Referral Letter   ② Patient Summary   ③ Visit Summary
 * ④ Attachments        ⑤ Routing           ⑥ Consent
 */

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  AlertTriangle, CheckCircle2, ClipboardList, FileText,
  Loader2, Lock, MapPin, Send, Shield, Stethoscope, Upload, Users, Video,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useCreateTelemedicineSession } from "@/hooks/queries/useTelemedicine";
import { useUploadDocumentFile } from "@/hooks/queries/useClinicalDocuments";
import { usePatientSummary } from "@/hooks/queries/useSummary";
import { useEncounterVitals } from "@/hooks/queries/useVitals";

function toLocalDateTimeValue(date: Date) {
  const offset = date.getTimezoneOffset();
  const adjusted = new Date(date.getTime() - offset * 60_000);
  return adjusted.toISOString().slice(0, 16);
}

const URGENCY_LEVELS = [
  { id: "ROUTINE", label: "Routine", color: "bg-green-100 text-green-700" },
  { id: "URGENT", label: "Urgent", color: "bg-amber-100 text-warning-foreground" },
  { id: "EMERGENCY", label: "Emergency", color: "bg-red-100 text-danger" },
] as const;

const SPECIALTIES = [
  "Internal Medicine", "Surgery", "Paediatrics", "Obstetrics & Gynaecology",
  "Psychiatry", "Orthopaedics", "ENT", "Ophthalmology", "Dermatology",
  "Cardiology", "Neurology", "Oncology", "Radiology", "Pathology",
  "Anaesthesia", "Emergency Medicine", "Family Medicine", "Public Health",
] as const;

const ROUTING_TYPES = [
  { id: "PRACTITIONER", label: "Specific Practitioner", icon: Stethoscope },
  { id: "WORKSPACE", label: "Workspace / Ward", icon: MapPin },
  { id: "ON_CALL", label: "On-Call Team", icon: Users },
  { id: "FACILITY_SERVICE", label: "Facility Clinical Service", icon: ClipboardList },
  { id: "SPECIALTY_POOL", label: "General / Specialty Pool", icon: Video },
] as const;

const CONSENT_TYPES = [
  { id: "DIGITAL", label: "Digital (patient present)" },
  { id: "VERBAL", label: "Verbal (documented)" },
  { id: "PROXY", label: "Proxy / Guardian" },
  { id: "EMERGENCY", label: "Emergency (implied consent)" },
] as const;

type Step = "letter" | "summary" | "visit" | "attachments" | "routing" | "consent";

const STEPS: { id: Step; label: string; num: string; icon: React.ElementType; required?: boolean }[] = [
  { id: "letter", label: "Referral Letter", num: "①", icon: FileText, required: true },
  { id: "summary", label: "Patient Summary", num: "②", icon: ClipboardList },
  { id: "visit", label: "Visit Summary", num: "③", icon: Stethoscope },
  { id: "attachments", label: "Attachments", num: "④", icon: Upload },
  { id: "routing", label: "Routing", num: "⑤", icon: MapPin, required: true },
  { id: "consent", label: "Consent", num: "⑥", icon: Lock, required: true },
];

function SummaryList({ title, items, emphasise }: { title: string; items: string[]; emphasise?: boolean }) {
  return (
    <div>
      <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">{title}</p>
      {items.length === 0 ? (
        <p className="text-xs text-muted-foreground">None recorded</p>
      ) : (
        <ul className={`mt-0.5 space-y-0.5 text-xs ${emphasise ? "text-red-600" : "text-foreground"}`}>
          {items.slice(0, 5).map((it, i) => <li key={i}>{it}</li>)}
          {items.length > 5 && <li className="text-muted-foreground">+{items.length - 5} more</li>}
        </ul>
      )}
    </div>
  );
}

export default function NewTeleconsultPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const user = useAuthStore((s) => s.user);
  const facility = useFacilityStore((s) => s.facility);
  const patientId = searchParams.get("patientId") || "";
  const encounterId = searchParams.get("encounterId") || "";

  const createSession = useCreateTelemedicineSession();
  const [activeStep, setActiveStep] = useState<Step>("letter");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Form state
  const [urgency, setUrgency] = useState("ROUTINE");
  const [specialty, setSpecialty] = useState("");
  const [scheduledAt, setScheduledAt] = useState(() => toLocalDateTimeValue(new Date(Date.now() + 60 * 60 * 1000)));
  const [referralLetter, setReferralLetter] = useState("");
  const [presentingProblems, setPresentingProblems] = useState("");
  const [clinicalQuestion, setClinicalQuestion] = useState("");
  // Real uploaded documents (document-service UUIDs) — the referral attachment validator
  // requires document ids, so free-text refs never validated (G33). Uploads produce them.
  const [attachments, setAttachments] = useState<{ id: string; title: string }[]>([]);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const uploadDoc = useUploadDocumentFile();
  // Live patient + visit context for the Stage-2/3 summary panels (G33) — no longer hardcoded.
  const patientSummaryQ = usePatientSummary(patientId || undefined);
  const summary = patientSummaryQ.data?.data;
  const vitalsQ = useEncounterVitals(encounterId || "");
  const latestVitals = (vitalsQ.data?.data ?? [])[0]?.attributes;
  const [routingType, setRoutingType] = useState("");
  const [routingTarget, setRoutingTarget] = useState("");
  const [purposeOfUse, setPurposeOfUse] = useState("TREATMENT");
  const [consentType, setConsentType] = useState("");
  const [consentReference, setConsentReference] = useState<string | null>(null);

  const canSubmit = referralLetter.trim() && routingType && consentReference;

  useEffect(() => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:purpose_of_use", purposeOfUse);
    }
  }, [purposeOfUse]);

  async function ensureSessionId(): Promise<string> {
    if (sessionId) return sessionId;
    const res = await createSession.mutateAsync({
      patient_id: patientId,
      encounter_id: encounterId || undefined,
      facility_id: facility?.id,
      urgency,
      specialty: specialty || undefined,
      purpose_of_use: purposeOfUse,
      session_provider: "EXTERNAL_MANAGED",
      scheduled_at: scheduledAt ? new Date(scheduledAt).toISOString() : undefined,
    });
    const sid = res.data.id;
    setSessionId(sid);
    return sid;
  }

  async function handleRecordConsent() {
    if (!consentType) return;
    try {
      const sid = await ensureSessionId();
      const res = await apiClient.post<{ data: { consentReference?: string; consentToken?: string } }>(
        `/internal/v1/teleconsult/sessions/${sid}/consent`,
        { type: consentType, purposeOfUse }
      );
      const reference = res.data.consentReference ?? res.data.consentToken;
      if (!reference) {
        throw new Error("Consent reference was not returned by governance services");
      }
      setConsentReference(reference);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record telemedicine consent.");
    }
  }

  async function handleUploadFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    if (!patientId) {
      setUploadError("A patient must be in context to upload attachments.");
      return;
    }
    setUploadError(null);
    for (const file of Array.from(files)) {
      try {
        const res = await uploadDoc.mutateAsync({
          patientId,
          file,
          documentType: "REFERRAL_ATTACHMENT",
          title: file.name,
          encounter_id: encounterId || undefined,
          uploaded_by: user?.id,
        });
        const id = res.data.id;
        if (id) setAttachments((prev) => [...prev, { id, title: file.name }]);
      } catch {
        setUploadError(`Failed to upload ${file.name}. Verify document-service availability.`);
      }
    }
  }

  async function handleSubmit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      // Stage 1: Create session
      const sid = await ensureSessionId();

      // Stage 2: Update referral package
      await apiClient.put(`/internal/v1/teleconsult/sessions/${sid}/referral`, {
        referralLetter,
        presentingProblems: presentingProblems.split("\n").filter(Boolean),
        clinicalQuestion,
        attachments: attachments.map((a) => a.id),
        routingType,
        routingTarget,
        urgency,
        specialty,
        purposeOfUse,
        consentReference,
      });

      // Stage 2→3: Submit
      await apiClient.post(`/internal/v1/teleconsult/sessions/${sid}/submit`);

      setSubmitted(true);
      setTimeout(() => router.push(`/telemedicine/session/${sid}`), 2000);
    } catch {
      setError("Failed to submit referral. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (submitted) {
    return (
      <AppLayout>
        <PageShell title="Referral Submitted">
          <div className="max-w-lg mx-auto text-center py-16">
            <CheckCircle2 className="w-16 h-16 text-primary mx-auto mb-4" />
            <h2 className="text-xl font-semibold text-foreground mb-2">Referral Submitted Successfully</h2>
            <p className="text-sm text-muted-foreground">Your teleconsultation referral has been submitted and routed. You will be notified when a consultant accepts.</p>
            <p className="text-xs text-muted-foreground mt-4">Redirecting to session workspace...</p>
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell title="New Teleconsultation" subtitle="Build and submit a clinical referral package">
        <div className="flex gap-6 max-w-6xl">
          {/* Left navigation — steps */}
          <nav className="w-48 shrink-0 space-y-1">
            {STEPS.map((step) => {
              const isActive = activeStep === step.id;
              const isDone = step.id === "letter" ? !!referralLetter.trim()
                : step.id === "routing" ? !!routingType
                : step.id === "consent" ? !!consentReference
                : false;
              return (
                <button
                  key={step.id}
                  onClick={() => setActiveStep(step.id)}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left text-sm transition-colors ${
                    isActive ? "bg-primary-soft text-primary-hover font-medium" : "text-muted-foreground hover:bg-background"
                  }`}
                >
                  <span className="text-base">{step.num}</span>
                  <span className="flex-1 truncate">{step.label}</span>
                  {isDone && <CheckCircle2 className="w-3.5 h-3.5 text-primary shrink-0" />}
                  {step.required && !isDone && <span className="text-red-400 text-xs">*</span>}
                </button>
              );
            })}

            <hr className="my-3" />

            {/* Urgency */}
            <div className="px-3">
              <p className="text-[10px] font-semibold text-muted-foreground uppercase mb-1.5">Urgency</p>
              <div className="space-y-1">
                {URGENCY_LEVELS.map((u) => (
                  <button key={u.id} onClick={() => setUrgency(u.id)}
                    className={`w-full text-left px-2 py-1 text-xs rounded-md border ${
                      urgency === u.id ? `${u.color} ring-1 ring-offset-1` : "border-border text-muted-foreground"
                    }`}>
                    {u.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="px-3 pt-2">
              <p className="text-[10px] font-semibold text-muted-foreground uppercase mb-1.5">Specialty</p>
              <select value={specialty} onChange={(e) => setSpecialty(e.target.value)}
                className="w-full text-xs border border-border rounded-md px-2 py-1.5">
                <option value="">Select...</option>
                {SPECIALTIES.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>

            <div className="px-3 pt-2">
              <p className="text-[10px] font-semibold text-muted-foreground uppercase mb-1.5">Scheduled time</p>
              <input
                type="datetime-local"
                aria-label="Scheduled time"
                value={scheduledAt}
                onChange={(e) => setScheduledAt(e.target.value)}
                className="w-full text-xs border border-border rounded-md px-2 py-1.5"
              />
            </div>

            <div className="px-3 pt-2">
              <p className="text-[10px] font-semibold text-muted-foreground uppercase mb-1.5">Purpose of Use</p>
              <select value={purposeOfUse} onChange={(e) => setPurposeOfUse(e.target.value)}
                className="w-full text-xs border border-border rounded-md px-2 py-1.5">
                <option value="TREATMENT">Treatment</option>
                <option value="EMERGENCY">Emergency</option>
                <option value="OPERATIONS">Operations</option>
                <option value="PUBLIC_HEALTH">Public health</option>
                <option value="PAYMENT">Payment</option>
              </select>
            </div>
          </nav>

          {/* Main content area */}
          <div className="flex-1 min-w-0">
            {error && (
              <div className="mb-4 p-3 bg-danger-soft border border-danger/28 rounded-lg text-sm text-danger flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" /> {error}
              </div>
            )}

            {/* ① Referral Letter */}
            {activeStep === "letter" && (
              <div className="bg-card rounded-xl border border-border p-6 space-y-4">
                <h3 className="text-base font-semibold text-foreground">① Referral Letter</h3>
                <p className="text-sm text-muted-foreground">Describe the clinical situation and what you need from the consultant.</p>
                <label className="block">
                  <span className="text-sm font-medium text-foreground">Presenting problems *</span>
                  <textarea value={presentingProblems} onChange={(e) => setPresentingProblems(e.target.value)} rows={3}
                    className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="One problem per line..." />
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-foreground">Clinical question</span>
                  <input value={clinicalQuestion} onChange={(e) => setClinicalQuestion(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="What specific question do you need answered?" />
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-foreground">Referral letter / clinical narrative *</span>
                  <textarea value={referralLetter} onChange={(e) => setReferralLetter(e.target.value)} rows={8}
                    className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                    placeholder="Dear Colleague,&#10;&#10;I am referring this patient for your opinion regarding...&#10;&#10;History: ...&#10;Examination: ...&#10;Investigations: ...&#10;Current management: ...&#10;&#10;I would be grateful for your advice on..." />
                </label>
              </div>
            )}

            {/* ② Patient Summary */}
            {activeStep === "summary" && (
              <div className="bg-card rounded-xl border border-border p-6 space-y-4">
                <h3 className="text-base font-semibold text-foreground">② Patient Summary</h3>
                <p className="text-sm text-muted-foreground">Live from the patient record. The consultant will see this.</p>
                <div className="bg-background rounded-lg p-4 text-sm text-muted-foreground space-y-3">
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1">
                    <p><strong>Patient ID:</strong> {patientId || "Not specified"}</p>
                    <p><strong>Encounter:</strong> {encounterId || "Active encounter"}</p>
                    <p><strong>Referring facility:</strong> {facility?.name || "Not selected"}</p>
                    <p><strong>Referring clinician:</strong> {user?.displayName || user?.email}</p>
                  </div>
                  {!patientId ? (
                    <p className="text-xs">No patient in context — open this from a patient chart to attach a live summary.</p>
                  ) : patientSummaryQ.isLoading ? (
                    <p className="flex items-center gap-2 text-xs"><Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading patient summary…</p>
                  ) : summary ? (
                    <div className="grid gap-3 sm:grid-cols-3 border-t border-border pt-3">
                      <SummaryList title="Active conditions" items={(summary.conditions ?? []).filter((c) => c.clinicalStatus !== "resolved").map((c) => c.display)} />
                      <SummaryList title="Medications" items={(summary.medications ?? []).map((m) => m.dosage ? `${m.display} (${m.dosage})` : m.display)} />
                      <SummaryList title="Allergies" items={(summary.allergies ?? []).map((a) => `${a.substance} — ${a.reaction}`)} emphasise />
                    </div>
                  ) : (
                    <p className="text-xs">Patient summary unavailable.</p>
                  )}
                </div>
              </div>
            )}

            {/* ③ Visit Summary */}
            {activeStep === "visit" && (
              <div className="bg-card rounded-xl border border-border p-6 space-y-4">
                <h3 className="text-base font-semibold text-foreground">③ Visit Summary</h3>
                <p className="text-sm text-muted-foreground">Current encounter context — live from the active visit.</p>
                <div className="bg-background rounded-lg p-4 text-sm text-muted-foreground space-y-2">
                  <p><strong>Chief complaint:</strong> {presentingProblems.split("\n")[0] || "—"}</p>
                  {!encounterId ? (
                    <p className="text-xs">No encounter in context.</p>
                  ) : vitalsQ.isLoading ? (
                    <p className="flex items-center gap-2 text-xs"><Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading vitals…</p>
                  ) : latestVitals ? (
                    <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs">
                      {latestVitals.systolic != null && latestVitals.diastolic != null && <span><strong>BP</strong> {latestVitals.systolic}/{latestVitals.diastolic}</span>}
                      {latestVitals.heartRate != null && <span><strong>HR</strong> {latestVitals.heartRate}</span>}
                      {latestVitals.temperature != null && <span><strong>Temp</strong> {latestVitals.temperature}°C</span>}
                      {latestVitals.respiratoryRate != null && <span><strong>RR</strong> {latestVitals.respiratoryRate}</span>}
                      {latestVitals.oxygenSaturation != null && <span><strong>SpO₂</strong> {latestVitals.oxygenSaturation}%</span>}
                      {latestVitals.recordedAt && <span className="text-muted-foreground">· {new Date(latestVitals.recordedAt).toLocaleString()}</span>}
                    </div>
                  ) : (
                    <p className="text-xs">No vitals recorded for this encounter yet.</p>
                  )}
                </div>
              </div>
            )}

            {/* ④ Attachments */}
            {activeStep === "attachments" && (
              <div className="bg-card rounded-xl border border-border p-6 space-y-4">
                <h3 className="text-base font-semibold text-foreground">④ Attachments</h3>
                <p className="text-sm text-muted-foreground">
                  Upload supporting documents, images, or files. Each is stored in the clinical document
                  service and attached to the referral by its document id.
                </p>
                {!patientId && (
                  <p className="rounded-lg border border-dashed border-border px-3 py-2 text-xs text-muted-foreground">
                    A patient must be in context to upload attachments.
                  </p>
                )}
                <label
                  className={`flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-border p-8 text-center text-muted-foreground ${patientId ? "cursor-pointer hover:bg-background" : "opacity-50"}`}
                >
                  {uploadDoc.isPending ? (
                    <Loader2 className="w-8 h-8 mx-auto mb-2 animate-spin" />
                  ) : (
                    <Upload className="w-8 h-8 mx-auto mb-2" />
                  )}
                  <p className="text-sm">{uploadDoc.isPending ? "Uploading…" : "Click to browse and upload"}</p>
                  <p className="text-xs mt-1">PDF, JPEG, PNG, DICOM up to 25MB each</p>
                  <input
                    type="file"
                    multiple
                    disabled={!patientId || uploadDoc.isPending}
                    onChange={(e) => void handleUploadFiles(e.target.files)}
                    className="hidden"
                  />
                </label>
                {uploadError && <p className="text-xs text-red-600">{uploadError}</p>}
                {attachments.length > 0 && (
                  <ul className="space-y-1.5">
                    {attachments.map((a) => (
                      <li key={a.id} className="flex items-center justify-between gap-2 rounded-lg bg-background px-3 py-2 text-sm">
                        <span className="flex items-center gap-2 min-w-0">
                          <FileText className="w-4 h-4 shrink-0 text-primary" />
                          <span className="truncate">{a.title}</span>
                        </span>
                        <button
                          type="button"
                          onClick={() => setAttachments((prev) => prev.filter((x) => x.id !== a.id))}
                          className="text-xs text-red-600 hover:underline shrink-0"
                        >
                          Remove
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}

            {/* ⑤ Routing */}
            {activeStep === "routing" && (
              <div className="bg-card rounded-xl border border-border p-6 space-y-4">
                <h3 className="text-base font-semibold text-foreground">⑤ Routing & Targeting *</h3>
                <p className="text-sm text-muted-foreground">Choose where this referral should be routed.</p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {ROUTING_TYPES.map((rt) => {
                    const Icon = rt.icon;
                    return (
                      <button key={rt.id} onClick={() => setRoutingType(rt.id)}
                        className={`text-left flex items-center gap-3 p-3 rounded-lg border transition-all ${
                          routingType === rt.id ? "border-impilo-400 bg-primary-soft ring-1 ring-impilo-300" : "border-border hover:border-border"
                        }`}>
                        <Icon className={`w-5 h-5 ${routingType === rt.id ? "text-primary" : "text-muted-foreground"}`} />
                        <span className={`text-sm ${routingType === rt.id ? "font-medium text-primary-hover" : "text-foreground"}`}>{rt.label}</span>
                      </button>
                    );
                  })}
                </div>
                {routingType && (
                  <label className="block">
                    <span className="text-sm font-medium text-foreground">Target (name, ID, or facility)</span>
                    <input value={routingTarget} onChange={(e) => setRoutingTarget(e.target.value)}
                      className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                      placeholder={routingType === "PRACTITIONER" ? "Dr. name or Provider ID..." : "Workspace, ward, or facility name..."} />
                  </label>
                )}
              </div>
            )}

            {/* ⑥ Consent */}
            {activeStep === "consent" && (
              <div className="bg-card rounded-xl border border-border p-6 space-y-4">
                <h3 className="text-base font-semibold text-foreground">⑥ Digital Consent *</h3>
                <p className="text-sm text-muted-foreground">Patient consent is required before the referral can be submitted. The consent token verifies the identities of the patient, referrer, and intended receiver.</p>
                <div className="grid grid-cols-2 gap-2">
                  {CONSENT_TYPES.map((ct) => (
                    <button key={ct.id} onClick={() => setConsentType(ct.id)}
                      className={`text-left px-3 py-2 rounded-lg border text-sm transition-all ${
                        consentType === ct.id ? "border-impilo-400 bg-primary-soft font-medium" : "border-border hover:border-border"
                      }`}>
                      {ct.label}
                    </button>
                  ))}
                </div>
                {consentType && !consentReference && (
                  <button onClick={handleRecordConsent}
                    className="flex items-center gap-2 px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors">
                    <Shield className="w-4 h-4" /> Record Consent & Issue Token
                  </button>
                )}
                {consentReference && (
                  <div className="flex items-center gap-2 p-3 bg-green-50 border border-green-200 rounded-lg">
                    <CheckCircle2 className="w-5 h-5 text-green-600 shrink-0" />
                    <div>
                      <p className="text-sm font-medium text-green-800">Consent recorded</p>
                      <p className="text-xs text-green-600">Reference: {consentReference}</p>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Submit bar */}
            <div className="mt-6 flex items-center justify-between bg-card rounded-xl border border-border px-6 py-4">
              <div className="text-xs text-muted-foreground">
                {referralLetter.trim() ? "✓ Letter" : "○ Letter"} ·{" "}
                {routingType ? "✓ Routing" : "○ Routing"} ·{" "}
                {consentReference ? "✓ Consent" : "○ Consent"}
              </div>
              <button
                onClick={handleSubmit}
                disabled={!canSubmit || submitting}
                className="flex items-center gap-2 px-6 py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                {submitting ? "Submitting..." : "Send Referral"}
              </button>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
