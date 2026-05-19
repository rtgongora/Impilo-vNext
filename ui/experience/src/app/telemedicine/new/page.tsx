"use client";

/**
 * New Teleconsultation — Stages 1-2: Create session + Build referral package.
 *
 * Seven-stage referral builder:
 * ① Referral Letter   ② Patient Summary   ③ Visit Summary
 * ④ Attachments        ⑤ Routing           ⑥ Consultation Mode   ⑦ Consent
 */

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  AlertTriangle, ArrowRight, CheckCircle2, ClipboardList, FileText,
  Loader2, Lock, MapPin, Send, Shield, Stethoscope, Upload, Users, Video,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const URGENCY_LEVELS = [
  { id: "ROUTINE", label: "Routine", color: "bg-green-100 text-green-700" },
  { id: "URGENT", label: "Urgent", color: "bg-amber-100 text-amber-700" },
  { id: "EMERGENCY", label: "Emergency", color: "bg-red-100 text-red-700" },
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
  { id: "UNIT", label: "Facility Unit", icon: MapPin },
  { id: "ON_CALL", label: "On-Call Team", icon: Users },
  { id: "FACILITY_SERVICE", label: "Facility Clinical Service", icon: ClipboardList },
  { id: "SPECIALTY_POOL", label: "Specialty Pool", icon: Video },
] as const;

const TELEMEDICINE_MODES = [
  { id: "async", label: "Asynchronous (store and forward)" },
  { id: "chat", label: "Chat" },
  { id: "audio", label: "Audio" },
  { id: "video", label: "Video" },
  { id: "scheduled", label: "Scheduled" },
  { id: "board", label: "Board / MDT review" },
] as const;

const CONSENT_TYPES = [
  { id: "DIGITAL", label: "Digital (patient present)" },
  { id: "VERBAL", label: "Verbal (documented)" },
  { id: "PROXY", label: "Proxy / Guardian" },
  { id: "EMERGENCY", label: "Emergency (implied consent)" },
] as const;

type Step = "letter" | "summary" | "visit" | "attachments" | "routing" | "mode" | "consent";

const STEPS: { id: Step; label: string; num: string; icon: React.ElementType; required?: boolean }[] = [
  { id: "letter", label: "Referral Letter", num: "①", icon: FileText, required: true },
  { id: "summary", label: "Patient Summary", num: "②", icon: ClipboardList },
  { id: "visit", label: "Visit Summary", num: "③", icon: Stethoscope },
  { id: "attachments", label: "Attachments", num: "④", icon: Upload },
  { id: "routing", label: "Routing", num: "⑤", icon: MapPin, required: true },
  { id: "mode", label: "Consultation Mode", num: "⑥", icon: Video, required: true },
  { id: "consent", label: "Consent", num: "⑦", icon: Lock, required: true },
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
  const [preferredMode, setPreferredMode] = useState("");
  const [allowedModes, setAllowedModes] = useState<string[]>([]);
  const [consentType, setConsentType] = useState("");
  const [consentToken, setConsentToken] = useState<string | null>(null);

  const canSubmit = referralLetter.trim() && routingType && preferredMode && consentToken;

  function toggleAllowedMode(mode: string) {
    setAllowedModes((prev) =>
      prev.includes(mode) ? prev.filter((m) => m !== mode) : [...prev, mode],
    );
  }

  async function handleRecordConsent() {
    if (!consentType) return;
    try {
      let sid = sessionId;
      if (!sid) {
        const created = await apiClient.post<{ data: { id: string } }>("/internal/v1/teleconsult/sessions", {
          patientId,
          encounterId,
          facilityId: facility?.id,
          urgency,
          specialty,
        });
        sid = created.data.id;
        setSessionId(sid);
      }
      const res = await apiClient.post<{ data: { consentToken: string } }>(`/internal/v1/teleconsult/sessions/${sid}/consent`, { type: consentType });
      setConsentToken(res.data.consentToken);
      setError(null);
    } catch {
      setError("Consent capture backend is unavailable for teleconsult in this environment.");
    }
  }

  async function handleSubmit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      // Stage 1: Create session
      let sid = sessionId;
      if (!sid) {
        const res = await apiClient.post<{ data: { id: string } }>("/internal/v1/teleconsult/sessions", {
          patientId, encounterId, facilityId: facility?.id, urgency, specialty,
        });
        sid = res.data.id;
        setSessionId(sid);
      }

      // Stage 2: Update referral package
      await apiClient.put(`/internal/v1/teleconsult/sessions/${sid}/referral`, {
        referralLetter,
        presentingProblems: presentingProblems.split("\n").filter(Boolean),
        clinicalQuestion,
        attachments: attachmentRefs.split("\n").filter(Boolean),
        routingType,
        routingTarget,
        preferredMode,
        acceptableModes: allowedModes,
        urgency,
        specialty,
      });

      // Stage 2→3: Submit
      await apiClient.post(`/internal/v1/teleconsult/sessions/${sid}/submit`);

      setSubmitted(true);
      setTimeout(() => router.push(`/telemedicine/session/${sid}`), 2000);
    } catch (e: unknown) {
      const message =
        typeof e === "object" &&
        e !== null &&
        "error" in e &&
        typeof (e as { error?: { message?: unknown } }).error?.message === "string"
          ? (e as { error: { message: string } }).error.message
          : "Teleconsult backend is unavailable. No local fallback was used.";
      setError(message);
    } finally {
      setSubmitting(false);
    }
  }

  if (submitted) {
    return (
      <AppLayout>
        <PageShell title="Referral Submitted">
          <div className="max-w-lg mx-auto text-center py-16">
            <CheckCircle2 className="w-16 h-16 text-impilo-500 mx-auto mb-4" />
            <h2 className="text-xl font-semibold text-gray-900 mb-2">Referral Submitted Successfully</h2>
            <p className="text-sm text-gray-500">Your teleconsultation referral has been submitted and routed. You will be notified when a consultant accepts.</p>
            <p className="text-xs text-gray-400 mt-4">Redirecting to session workspace...</p>
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
              const Icon = step.icon;
              const isActive = activeStep === step.id;
              const isDone = step.id === "letter" ? !!referralLetter.trim()
                : step.id === "routing" ? !!routingType
                : step.id === "consent" ? !!consentToken
                : false;
              return (
                <button
                  key={step.id}
                  onClick={() => setActiveStep(step.id)}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left text-sm transition-colors ${
                    isActive ? "bg-impilo-50 text-impilo-700 font-medium" : "text-gray-600 hover:bg-gray-50"
                  }`}
                >
                  <span className="text-base">{step.num}</span>
                  <span className="flex-1 truncate">{step.label}</span>
                  {isDone && <CheckCircle2 className="w-3.5 h-3.5 text-impilo-500 shrink-0" />}
                  {step.required && !isDone && <span className="text-red-400 text-xs">*</span>}
                </button>
              );
            })}

            <hr className="my-3" />

            {/* Urgency */}
            <div className="px-3">
              <p className="text-[10px] font-semibold text-gray-400 uppercase mb-1.5">Urgency</p>
              <div className="space-y-1">
                {URGENCY_LEVELS.map((u) => (
                  <button key={u.id} onClick={() => setUrgency(u.id)}
                    className={`w-full text-left px-2 py-1 text-xs rounded-md border ${
                      urgency === u.id ? `${u.color} ring-1 ring-offset-1` : "border-gray-200 text-gray-500"
                    }`}>
                    {u.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="px-3 pt-2">
              <p className="text-[10px] font-semibold text-gray-400 uppercase mb-1.5">Specialty</p>
              <select value={specialty} onChange={(e) => setSpecialty(e.target.value)}
                className="w-full text-xs border border-gray-300 rounded-md px-2 py-1.5">
                <option value="">Select...</option>
                {SPECIALTIES.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
          </nav>

          {/* Main content area */}
          <div className="flex-1 min-w-0">
            {error && (
              <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" /> {error}
              </div>
            )}

            {/* ① Referral Letter */}
            {activeStep === "letter" && (
              <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-4">
                <h3 className="text-base font-semibold text-gray-900">① Referral Letter</h3>
                <p className="text-sm text-gray-500">Describe the clinical situation and what you need from the consultant.</p>
                <label className="block">
                  <span className="text-sm font-medium text-gray-700">Presenting problems *</span>
                  <textarea value={presentingProblems} onChange={(e) => setPresentingProblems(e.target.value)} rows={3}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="One problem per line..." />
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-gray-700">Clinical question</span>
                  <input value={clinicalQuestion} onChange={(e) => setClinicalQuestion(e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="What specific question do you need answered?" />
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-gray-700">Referral letter / clinical narrative *</span>
                  <textarea value={referralLetter} onChange={(e) => setReferralLetter(e.target.value)} rows={8}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                    placeholder="Dear Colleague,&#10;&#10;I am referring this patient for your opinion regarding...&#10;&#10;History: ...&#10;Examination: ...&#10;Investigations: ...&#10;Current management: ...&#10;&#10;I would be grateful for your advice on..." />
                </label>
              </div>
            )}

            {/* ② Patient Summary */}
            {activeStep === "summary" && (
              <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-4">
                <h3 className="text-base font-semibold text-gray-900">② Patient Summary</h3>
                <p className="text-sm text-gray-500">Auto-generated from the patient record. The consultant will see this.</p>
                <div className="bg-gray-50 rounded-lg p-4 text-sm text-gray-600 space-y-2">
                  <p><strong>Patient ID:</strong> {patientId || "Not available"}</p>
                  <p><strong>Encounter:</strong> {encounterId || "Not available"}</p>
                  <p><strong>Referring facility:</strong> {facility?.name || "Not selected"}</p>
                  <p><strong>Referring clinician:</strong> {user?.displayName || user?.email}</p>
                  <p className="text-xs text-gray-400 mt-3">Auto-attached patient chart extraction is pending canonical backend linkage. Current submission uses explicit referral fields only.</p>
                </div>
              </div>
            )}

            {/* ③ Visit Summary */}
            {activeStep === "visit" && (
              <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-4">
                <h3 className="text-base font-semibold text-gray-900">③ Visit Summary</h3>
                <p className="text-sm text-gray-500">Current encounter context — auto-generated from the active visit.</p>
                <div className="bg-gray-50 rounded-lg p-4 text-sm text-gray-600 space-y-2">
                  <p><strong>Encounter type:</strong> {encounterId ? "From active encounter context" : "Unavailable (no encounter context)"}</p>
                  <p><strong>Chief complaint:</strong> {presentingProblems.split("\n")[0] || "Not captured in this form yet"}</p>
                  <p><strong>Vitals:</strong> Pending backend summary integration</p>
                  <p><strong>Investigations:</strong> Pending backend summary integration</p>
                  <p className="text-xs text-gray-400 mt-3">This panel is intentionally non-authoritative until PCT/BUTANO/OROS summary aggregation is wired.</p>
                </div>
              </div>
            )}

            {/* ④ Attachments */}
            {activeStep === "attachments" && (
              <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-4">
                <h3 className="text-base font-semibold text-gray-900">④ Attachments</h3>
                <p className="text-sm text-gray-500">
                  Reference previously uploaded document-service IDs (UUIDs), one per line.
                </p>
                <label className="block">
                  <span className="text-sm font-medium text-gray-700">Attachment document IDs (one UUID per line)</span>
                  <textarea value={attachmentRefs} onChange={(e) => setAttachmentRefs(e.target.value)} rows={4}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                    placeholder="e.g. 9f813346-2d1c-4b3e-8d44-c96d9d0ce6f1" />
                </label>
                <div className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center text-gray-400">
                  <Upload className="w-8 h-8 mx-auto mb-2" />
                  <p className="text-sm">File upload from this screen is not yet enabled.</p>
                  <p className="text-xs mt-1">Use the clinical documents flow first, then paste document IDs here.</p>
                </div>
              </div>
            )}

            {/* ⑤ Routing */}
            {activeStep === "routing" && (
              <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-4">
                <h3 className="text-base font-semibold text-gray-900">⑤ Routing & Targeting *</h3>
                <p className="text-sm text-gray-500">Choose where this referral should be routed.</p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {ROUTING_TYPES.map((rt) => {
                    const Icon = rt.icon;
                    return (
                      <button key={rt.id} onClick={() => setRoutingType(rt.id)}
                        className={`text-left flex items-center gap-3 p-3 rounded-lg border transition-all ${
                          routingType === rt.id ? "border-impilo-400 bg-impilo-50 ring-1 ring-impilo-300" : "border-gray-200 hover:border-gray-300"
                        }`}>
                        <Icon className={`w-5 h-5 ${routingType === rt.id ? "text-impilo-600" : "text-gray-400"}`} />
                        <span className={`text-sm ${routingType === rt.id ? "font-medium text-impilo-700" : "text-gray-700"}`}>{rt.label}</span>
                      </button>
                    );
                  })}
                </div>
                {routingType && (
                  <label className="block">
                    <span className="text-sm font-medium text-gray-700">Target (name, ID, or facility)</span>
                    <input value={routingTarget} onChange={(e) => setRoutingTarget(e.target.value)}
                      className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                      placeholder={routingType === "PRACTITIONER" ? "Dr. name or Provider ID..." : "Workspace, ward, or facility name..."} />
                  </label>
                )}
                {(routingType === "ON_CALL" || routingType === "SPECIALTY_POOL") && (
                  <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                    This routing type is visible for workflow parity but remains unavailable until canonical on-call/team/pool directory support is implemented.
                  </p>
                )}
              </div>
            )}

            {/* ⑥ Consultation Mode */}
            {activeStep === "mode" && (
              <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-4">
                <h3 className="text-base font-semibold text-gray-900">⑥ Consultation Mode *</h3>
                <p className="text-sm text-gray-500">Select the preferred mode and any acceptable alternatives for this referral package.</p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {TELEMEDICINE_MODES.map((mode) => (
                    <button
                      key={mode.id}
                      onClick={() => setPreferredMode(mode.id)}
                      className={`text-left p-3 rounded-lg border transition-all ${
                        preferredMode === mode.id
                          ? "border-impilo-400 bg-impilo-50 ring-1 ring-impilo-300"
                          : "border-gray-200 hover:border-gray-300"
                      }`}
                    >
                      <p className="text-sm font-medium text-gray-900">{mode.label}</p>
                      <p className="mt-1 text-xs text-gray-500">{mode.id}</p>
                    </button>
                  ))}
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-700 mb-2">Allowed fallback modes</p>
                  <div className="flex flex-wrap gap-2">
                    {TELEMEDICINE_MODES.map((mode) => {
                      const selected = allowedModes.includes(mode.id);
                      return (
                        <button
                          key={`${mode.id}-allowed`}
                          type="button"
                          onClick={() => toggleAllowedMode(mode.id)}
                          className={`rounded-full border px-3 py-1 text-xs ${
                            selected
                              ? "border-impilo-400 bg-impilo-50 text-impilo-700"
                              : "border-gray-300 text-gray-600"
                          }`}
                        >
                          {mode.label}
                        </button>
                      );
                    })}
                  </div>
                </div>
                <p className="text-xs text-gray-400">
                  Selecting a mode does not imply live WebRTC/session support in this environment; execution depends on canonical teleconsult backend readiness.
                </p>
              </div>
            )}

            {/* ⑥ Consent */}
            {activeStep === "consent" && (
              <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-4">
                <h3 className="text-base font-semibold text-gray-900">⑦ Digital Consent *</h3>
                <p className="text-sm text-gray-500">Patient consent is required before the referral can be submitted. The consent token verifies the identities of the patient, referrer, and intended receiver.</p>
                <div className="grid grid-cols-2 gap-2">
                  {CONSENT_TYPES.map((ct) => (
                    <button key={ct.id} onClick={() => setConsentType(ct.id)}
                      className={`text-left px-3 py-2 rounded-lg border text-sm transition-all ${
                        consentType === ct.id ? "border-impilo-400 bg-impilo-50 font-medium" : "border-gray-200 hover:border-gray-300"
                      }`}>
                      {ct.label}
                    </button>
                  ))}
                </div>
                {consentType && !consentToken && (
                  <button onClick={handleRecordConsent}
                    className="flex items-center gap-2 px-4 py-2 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 transition-colors">
                    <Shield className="w-4 h-4" /> Record Consent & Issue Token
                  </button>
                )}
                {consentToken && (
                  <div className="flex items-center gap-2 p-3 bg-green-50 border border-green-200 rounded-lg">
                    <CheckCircle2 className="w-5 h-5 text-green-600 shrink-0" />
                    <div>
                      <p className="text-sm font-medium text-green-800">Consent recorded</p>
                      <p className="text-xs text-green-600">Token: {consentToken}</p>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Submit bar */}
            <div className="mt-6 flex items-center justify-between bg-white rounded-xl border border-gray-200 px-6 py-4">
              <div className="text-xs text-gray-400">
                {referralLetter.trim() ? "✓ Letter" : "○ Letter"} ·{" "}
                {routingType ? "✓ Routing" : "○ Routing"} ·{" "}
                {preferredMode ? "✓ Mode" : "○ Mode"} ·{" "}
                {consentToken ? "✓ Consent" : "○ Consent"}
              </div>
              <button
                onClick={handleSubmit}
                disabled={!canSubmit || submitting}
                className="flex items-center gap-2 px-6 py-2.5 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
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
