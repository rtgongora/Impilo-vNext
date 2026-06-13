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

export default function NewTeleconsultPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const user = useAuthStore((s) => s.user);
  const facility = useFacilityStore((s) => s.facility);
  const patientId = searchParams.get("patientId") || "";
  const encounterId = searchParams.get("encounterId") || "";

  const [activeStep, setActiveStep] = useState<Step>("letter");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Form state
  const [urgency, setUrgency] = useState("ROUTINE");
  const [specialty, setSpecialty] = useState("");
  const [referralLetter, setReferralLetter] = useState("");
  const [presentingProblems, setPresentingProblems] = useState("");
  const [clinicalQuestion, setClinicalQuestion] = useState("");
  const [attachmentRefs, setAttachmentRefs] = useState("");
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
    const res = await apiClient.post<{ data: { id: string } }>("/internal/v1/teleconsult/sessions", {
      patientId,
      encounterId,
      facilityId: facility?.id,
      urgency,
      specialty,
      purposeOfUse,
      sessionProvider: "EXTERNAL_MANAGED",
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
        attachments: attachmentRefs.split("\n").filter(Boolean),
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
                <p className="text-sm text-muted-foreground">Auto-generated from the patient record. The consultant will see this.</p>
                <div className="bg-background rounded-lg p-4 text-sm text-muted-foreground space-y-2">
                  <p><strong>Patient ID:</strong> {patientId || "Not specified"}</p>
                  <p><strong>Encounter:</strong> {encounterId || "Active encounter"}</p>
                  <p><strong>Referring facility:</strong> {facility?.name || "Not selected"}</p>
                  <p><strong>Referring clinician:</strong> {user?.displayName || user?.email}</p>
                  <p className="text-xs text-muted-foreground mt-3">Active conditions, medications, allergies, and recent results will be auto-attached when the session is created.</p>
                </div>
              </div>
            )}

            {/* ③ Visit Summary */}
            {activeStep === "visit" && (
              <div className="bg-card rounded-xl border border-border p-6 space-y-4">
                <h3 className="text-base font-semibold text-foreground">③ Visit Summary</h3>
                <p className="text-sm text-muted-foreground">Current encounter context — auto-generated from the active visit.</p>
                <div className="bg-background rounded-lg p-4 text-sm text-muted-foreground space-y-2">
                  <p><strong>Encounter type:</strong> Outpatient</p>
                  <p><strong>Chief complaint:</strong> {presentingProblems.split("\n")[0] || "—"}</p>
                  <p><strong>Vitals:</strong> Will be auto-attached</p>
                  <p><strong>Investigations:</strong> Will be auto-attached</p>
                  <p className="text-xs text-muted-foreground mt-3">This panel is read-only and auto-populated from the encounter record.</p>
                </div>
              </div>
            )}

            {/* ④ Attachments */}
            {activeStep === "attachments" && (
              <div className="bg-card rounded-xl border border-border p-6 space-y-4">
                <h3 className="text-base font-semibold text-foreground">④ Attachments</h3>
                <p className="text-sm text-muted-foreground">Upload or reference supporting documents, images, or files.</p>
                <label className="block">
                  <span className="text-sm font-medium text-foreground">Attachment references (one per line)</span>
                  <textarea value={attachmentRefs} onChange={(e) => setAttachmentRefs(e.target.value)} rows={4}
                    className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                    placeholder="e.g. X-ray chest PA — 2026-04-14&#10;Blood results — FBC, U&E&#10;ECG tracing" />
                </label>
                <div className="border-2 border-dashed border-border rounded-lg p-8 text-center text-muted-foreground">
                  <Upload className="w-8 h-8 mx-auto mb-2" />
                  <p className="text-sm">Drag & drop files here or click to browse</p>
                  <p className="text-xs mt-1">PDF, JPEG, PNG, DICOM up to 25MB each</p>
                </div>
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
