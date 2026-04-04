"use client";

/**
 * Encounter — Active encounter page with vitals capture, notes, orders, referrals, and close.
 * Route: /ehr/[patientId]/encounter/[encounterId] | pageTitle: "Encounter"
 */

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  Loader2,
  Activity,
  FileText,
  XCircle,
  CheckCircle2,
  AlertCircle,
  AlertTriangle,
  ClipboardList,
  Pill,
  ArrowUpRight,
  Save,
  Stethoscope,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounter, useCloseEncounter } from "@/hooks/queries/useEncounters";
import { usePatient } from "@/hooks/queries/usePatients";
import { useReferrals, type ReferralResource } from "@/hooks/queries/useReferrals";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function EncounterPage() {
  const params = useParams<{ patientId: string; encounterId: string }>();
  const router = useRouter();
  const { patientId, encounterId } = params;
  const { user } = useAuthStore();

  const { data: encounterData, isLoading: isLoadingEncounter } = useEncounter(encounterId);
  const { data: patientData } = usePatient(patientId);
  const { data: referralsData } = useReferrals(patientId);
  const closeEncounter = useCloseEncounter();

  // Fetch existing triage for this encounter
  const { data: triageData } = useQuery<ApiResponse<Array<{ id: string; attributes: Record<string, unknown> }>>>({
    queryKey: ["triage", { encounterId }],
    queryFn: () => apiClient.get(`/internal/v1/triage?encounter_id=${encounterId}`),
    enabled: !!encounterId,
  });
  const existingTriage = (triageData?.data ?? [])[0] ?? null;

  // Filter referrals linked to this encounter or with responses
  const linkedReferrals = (referralsData?.data ?? []).filter(
    (r: ReferralResource) =>
      r.attributes.encounterId === encounterId ||
      r.attributes.encounter_id === encounterId
  );
  const respondedReferrals = linkedReferrals.filter(
    (r: ReferralResource) => r.attributes.status === "RESPONDED" || r.attributes.status === "COMPLETED"
  );

  const currentUserId = user?.id ?? "system";
  const currentUserName = user?.displayName ?? user?.email ?? "Provider";

  const encounter = encounterData?.data;
  const patient = patientData?.data;

  // Vitals form state
  const [systolic, setSystolic] = useState("");
  const [diastolic, setDiastolic] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [temperature, setTemperature] = useState("");
  const [respiratoryRate, setRespiratoryRate] = useState("");
  const [oxygenSat, setOxygenSat] = useState("");
  const [weight, setWeight] = useState("");
  const [height, setHeight] = useState("");
  const [painScore, setPainScore] = useState("");
  const [vitalsSaving, setVitalsSaving] = useState(false);
  const [vitalsSaved, setVitalsSaved] = useState(false);
  const [vitalsError, setVitalsError] = useState<string | null>(null);

  // Notes state
  const [noteType, setNoteType] = useState("PROGRESS");
  const [subjective, setSubjective] = useState("");
  const [objective, setObjective] = useState("");
  const [assessment, setAssessment] = useState("");
  const [plan, setPlan] = useState("");
  const [noteBody, setNoteBody] = useState("");
  const [noteSaving, setNoteSaving] = useState(false);
  const [noteSaved, setNoteSaved] = useState(false);
  const [noteError, setNoteError] = useState<string | null>(null);

  const [showCloseConfirm, setShowCloseConfirm] = useState(false);

  // Examination findings state (Lovable-aligned system-by-system capture)
  const [examGeneral, setExamGeneral] = useState("");
  const [examCVS, setExamCVS] = useState("");
  const [examResp, setExamResp] = useState("");
  const [examAbdo, setExamAbdo] = useState("");
  const [examNeuro, setExamNeuro] = useState("");
  const [examSaving, setExamSaving] = useState(false);
  const [examSaved, setExamSaved] = useState(false);

  // Triage state (Lovable-aligned acuity + danger signs)
  const [triageAcuity, setTriageAcuity] = useState<number | null>(null);
  const [triageNotes, setTriageNotes] = useState("");
  const [triageSaving, setTriageSaving] = useState(false);
  const [triageSaved, setTriageSaved] = useState(false);
  const DANGER_SIGNS = [
    "Airway compromise", "Breathing difficulty", "Circulation failure",
    "Altered consciousness", "Severe pain", "Active bleeding",
    "Convulsions", "Dehydration (severe)", "High fever (>39°C)", "Shock",
  ];
  const [dangerSigns, setDangerSigns] = useState<Record<string, boolean>>({});

  const isActive =
    encounter?.attributes.status === "ACTIVE" ||
    encounter?.attributes.status === "IN_PROGRESS";

  async function handleSaveVitals() {
    setVitalsSaving(true);
    setVitalsError(null);
    setVitalsSaved(false);
    try {
      await apiClient.post("/internal/v1/vitals", {
        patient_id: patientId,
        encounter_id: encounterId,
        recorded_by: currentUserId,
        systolic: systolic ? Number(systolic) : null,
        diastolic: diastolic ? Number(diastolic) : null,
        heart_rate: heartRate ? Number(heartRate) : null,
        temperature: temperature ? Number(temperature) : null,
        respiratory_rate: respiratoryRate ? Number(respiratoryRate) : null,
        oxygen_saturation: oxygenSat ? Number(oxygenSat) : null,
        weight: weight ? Number(weight) : null,
        height: height ? Number(height) : null,
        pain_score: painScore ? Number(painScore) : null,
      });
      setVitalsSaved(true);
    } catch {
      setVitalsError("Failed to save vitals. Please try again.");
    } finally {
      setVitalsSaving(false);
    }
  }

  async function handleSaveNote() {
    setNoteSaving(true);
    setNoteError(null);
    setNoteSaved(false);
    try {
      await apiClient.post("/internal/v1/clinical-notes", {
        patient_id: patientId,
        encounter_id: encounterId,
        note_type: noteType,
        subjective: subjective || null,
        objective: objective || null,
        assessment: assessment || null,
        plan: plan || null,
        body: noteBody || null,
        author_id: currentUserId,
        author_name: currentUserName,
      });
      setNoteSaved(true);
      setSubjective("");
      setObjective("");
      setAssessment("");
      setPlan("");
      setNoteBody("");
    } catch {
      setNoteError("Failed to save note. Please try again.");
    } finally {
      setNoteSaving(false);
    }
  }

  async function handleSaveExamination() {
    setExamSaving(true);
    setExamSaved(false);
    try {
      const examBody = [
        examGeneral && `General: ${examGeneral}`,
        examCVS && `CVS: ${examCVS}`,
        examResp && `Respiratory: ${examResp}`,
        examAbdo && `Abdominal: ${examAbdo}`,
        examNeuro && `Neurological: ${examNeuro}`,
      ].filter(Boolean).join("\n\n");

      if (examBody) {
        await apiClient.post("/internal/v1/clinical-notes", {
          patient_id: patientId,
          encounter_id: encounterId,
          note_type: "EXAMINATION",
          body: examBody,
          author_id: currentUserId,
          author_name: currentUserName,
        });
        setExamSaved(true);
      }
    } catch {
      // Error handled by UI feedback
    } finally {
      setExamSaving(false);
    }
  }

  async function handleSaveTriage() {
    if (triageAcuity == null) return;
    setTriageSaving(true);
    setTriageSaved(false);
    try {
      const activeDangerSigns = Object.entries(dangerSigns)
        .map(([name, present]) => ({ name, present }));
      await apiClient.post("/internal/v1/triage", {
        patient_id: patientId,
        encounter_id: encounterId,
        acuity: triageAcuity,
        chief_complaint: (encounter?.attributes as Record<string, unknown>)?.chief_complaint ?? null,
        danger_signs: activeDangerSigns,
        notes: triageNotes || null,
        triaged_by: currentUserId,
        triaged_by_name: currentUserName,
      });
      setTriageSaved(true);
    } catch {
      // Error handled by UI
    } finally {
      setTriageSaving(false);
    }
  }

  function handleClose() {
    closeEncounter.mutate(
      { id: encounterId },
      {
        onSuccess: () => {
          router.push(`/ehr/${patientId}`);
        },
      },
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Encounter">
        <div className="mb-4">
          <Link
            href={`/ehr/${patientId}`}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to patient chart
          </Link>
        </div>

        {isLoadingEncounter ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading encounter...</span>
          </div>
        ) : !encounter ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Encounter not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Encounter Context Bar */}
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <h2 className="text-base font-semibold text-gray-900">
                    {encounter.attributes.encounterType} Encounter
                  </h2>
                  <span
                    className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                      isActive
                        ? "bg-green-100 text-green-700"
                        : "bg-gray-100 text-gray-600"
                    }`}
                  >
                    {encounter.attributes.status}
                  </span>
                  <span className="text-xs text-gray-500">
                    Started: {new Date(encounter.attributes.startedAt).toLocaleString()}
                  </span>
                  {encounter.attributes.closedAt && (
                    <span className="text-xs text-gray-500">
                      Closed: {new Date(encounter.attributes.closedAt).toLocaleString()}
                    </span>
                  )}
                </div>
                {/* Quick action links */}
                {isActive && (
                  <div className="flex gap-2">
                    <Link
                      href={`/ehr/${patientId}/orders`}
                      className="px-3 py-1.5 bg-purple-50 text-purple-700 text-xs font-medium rounded-lg hover:bg-purple-100 transition-colors flex items-center gap-1"
                    >
                      <ClipboardList className="w-3 h-3" /> Orders
                    </Link>
                    <Link
                      href={`/ehr/${patientId}/medications`}
                      className="px-3 py-1.5 bg-green-50 text-green-700 text-xs font-medium rounded-lg hover:bg-green-100 transition-colors flex items-center gap-1"
                    >
                      <Pill className="w-3 h-3" /> Rx
                    </Link>
                    <Link
                      href={`/ehr/${patientId}/consults`}
                      className="px-3 py-1.5 bg-orange-50 text-orange-700 text-xs font-medium rounded-lg hover:bg-orange-100 transition-colors flex items-center gap-1"
                    >
                      <ArrowUpRight className="w-3 h-3" /> Consults
                    </Link>
                    <Link
                      href={`/ehr/${patientId}/discharge?encounterId=${encounterId}`}
                      className="px-3 py-1.5 bg-red-50 text-red-700 text-xs font-medium rounded-lg hover:bg-red-100 transition-colors flex items-center gap-1"
                    >
                      <XCircle className="w-3 h-3" /> Discharge
                    </Link>
                  </div>
                )}
              </div>
            </div>

            {/* Existing Triage Display — show if already triaged */}
            {existingTriage && !isActive && (
              <div className={`rounded-lg border-2 p-4 ${
                existingTriage.attributes.acuity === 1 ? "border-red-400 bg-red-50" :
                existingTriage.attributes.acuity === 2 ? "border-orange-400 bg-orange-50" :
                existingTriage.attributes.acuity === 3 ? "border-yellow-400 bg-yellow-50" :
                existingTriage.attributes.acuity === 4 ? "border-green-400 bg-green-50" :
                "border-blue-400 bg-blue-50"
              }`}>
                <div className="flex items-center gap-3">
                  <AlertTriangle className="w-5 h-5" />
                  <div>
                    <span className="text-sm font-semibold">
                      Triage: P{String(existingTriage.attributes.acuity)} — {
                        existingTriage.attributes.acuity === 1 ? "Resuscitation" :
                        existingTriage.attributes.acuity === 2 ? "Emergency" :
                        existingTriage.attributes.acuity === 3 ? "Urgent" :
                        existingTriage.attributes.acuity === 4 ? "Standard" : "Non-urgent"
                      }
                    </span>
                    {existingTriage.attributes.triaged_by_name && (
                      <span className="text-xs text-gray-500 ml-2">
                        by {String(existingTriage.attributes.triaged_by_name)}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            )}

            {/* Triage / Acuity — Lovable TriagePanel alignment */}
            {isActive && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <AlertTriangle className="w-5 h-5 text-amber-500" />
                    <h3 className="font-medium text-gray-900">Triage Assessment</h3>
                  </div>
                  {triageSaved && (
                    <span className="text-xs text-green-600 flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" /> Saved
                    </span>
                  )}
                </div>

                {/* Acuity Selection */}
                <div className="mb-4">
                  <label className="block text-xs font-medium text-gray-600 mb-2">Triage Category</label>
                  <div className="grid grid-cols-5 gap-2">
                    {[
                      { level: 1, label: "Red", desc: "Resuscitation", color: "border-red-400 bg-red-50 text-red-700", active: "border-red-500 bg-red-100 ring-2 ring-red-300" },
                      { level: 2, label: "Orange", desc: "Emergency", color: "border-orange-400 bg-orange-50 text-orange-700", active: "border-orange-500 bg-orange-100 ring-2 ring-orange-300" },
                      { level: 3, label: "Yellow", desc: "Urgent", color: "border-yellow-400 bg-yellow-50 text-yellow-700", active: "border-yellow-500 bg-yellow-100 ring-2 ring-yellow-300" },
                      { level: 4, label: "Green", desc: "Standard", color: "border-green-400 bg-green-50 text-green-700", active: "border-green-500 bg-green-100 ring-2 ring-green-300" },
                      { level: 5, label: "Blue", desc: "Non-urgent", color: "border-blue-400 bg-blue-50 text-blue-700", active: "border-blue-500 bg-blue-100 ring-2 ring-blue-300" },
                    ].map((t) => (
                      <button
                        key={t.level}
                        onClick={() => setTriageAcuity(t.level)}
                        className={`p-3 rounded-lg border-2 text-center transition-all ${
                          triageAcuity === t.level ? t.active : t.color + " hover:opacity-80"
                        }`}
                      >
                        <div className="text-lg font-bold">P{t.level}</div>
                        <div className="text-xs font-medium">{t.label}</div>
                        <div className="text-[10px] opacity-75">{t.desc}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Danger Signs */}
                <div className="mb-4">
                  <label className="block text-xs font-medium text-gray-600 mb-2">Danger Signs Screening</label>
                  <div className="grid grid-cols-2 gap-2">
                    {DANGER_SIGNS.map((sign) => {
                      const present = dangerSigns[sign] ?? false;
                      return (
                        <button
                          key={sign}
                          onClick={() => setDangerSigns((prev) => ({ ...prev, [sign]: !prev[sign] }))}
                          className={`flex items-center gap-2 p-2 rounded-lg text-sm text-left transition-colors ${
                            present
                              ? "bg-red-50 border border-red-200 text-red-700"
                              : "bg-gray-50 border border-gray-200 text-gray-600 hover:bg-gray-100"
                          }`}
                        >
                          {present ? (
                            <AlertTriangle className="w-3.5 h-3.5 text-red-500 shrink-0" />
                          ) : (
                            <CheckCircle2 className="w-3.5 h-3.5 text-green-500 shrink-0" />
                          )}
                          <span className="text-xs">{sign}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Triage Notes */}
                <div className="mb-4">
                  <label className="block text-xs font-medium text-gray-600 mb-1">Triage Notes</label>
                  <textarea
                    value={triageNotes}
                    onChange={(e) => setTriageNotes(e.target.value)}
                    rows={2}
                    placeholder="Clinical observations, mechanism of injury, relevant context..."
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-amber-500 resize-none"
                  />
                </div>

                <button
                  onClick={handleSaveTriage}
                  disabled={triageSaving || triageAcuity == null}
                  className="w-full py-2 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
                >
                  {triageSaving ? (
                    <><Loader2 className="w-4 h-4 animate-spin" /> Saving...</>
                  ) : (
                    <><Save className="w-4 h-4" /> Save Triage Assessment</>
                  )}
                </button>
              </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Vitals Entry */}
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <Activity className="w-5 h-5 text-red-500" />
                    <h3 className="font-medium text-gray-900">Vitals</h3>
                  </div>
                  {vitalsSaved && (
                    <span className="text-xs text-green-600 flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" /> Saved
                    </span>
                  )}
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Systolic (mmHg)</label>
                    <input type="number" value={systolic} onChange={(e) => setSystolic(e.target.value)} placeholder="120" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Diastolic (mmHg)</label>
                    <input type="number" value={diastolic} onChange={(e) => setDiastolic(e.target.value)} placeholder="80" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Heart Rate (bpm)</label>
                    <input type="number" value={heartRate} onChange={(e) => setHeartRate(e.target.value)} placeholder="72" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Temperature (°C)</label>
                    <input type="number" step="0.1" value={temperature} onChange={(e) => setTemperature(e.target.value)} placeholder="36.5" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Resp. Rate (/min)</label>
                    <input type="number" value={respiratoryRate} onChange={(e) => setRespiratoryRate(e.target.value)} placeholder="16" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">SpO2 (%)</label>
                    <input type="number" value={oxygenSat} onChange={(e) => setOxygenSat(e.target.value)} placeholder="98" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Weight (kg)</label>
                    <input type="number" step="0.1" value={weight} onChange={(e) => setWeight(e.target.value)} placeholder="70" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Height (cm)</label>
                    <input type="number" step="0.1" value={height} onChange={(e) => setHeight(e.target.value)} placeholder="170" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div className="col-span-2">
                    <label className="block text-xs font-medium text-gray-600 mb-1">Pain Score (0-10)</label>
                    <input type="number" min="0" max="10" value={painScore} onChange={(e) => setPainScore(e.target.value)} placeholder="0" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                </div>
                {vitalsError && <p className="mt-2 text-xs text-red-600">{vitalsError}</p>}
                {isActive && (
                  <button onClick={handleSaveVitals} disabled={vitalsSaving}
                    className="mt-4 w-full py-2 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors">
                    {vitalsSaving ? <><Loader2 className="w-4 h-4 animate-spin" /> Saving...</> : <><Save className="w-4 h-4" /> Save Vitals</>}
                  </button>
                )}
              </div>

              {/* Clinical Notes */}
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <FileText className="w-5 h-5 text-indigo-500" />
                    <h3 className="font-medium text-gray-900">Clinical Notes</h3>
                  </div>
                  {noteSaved && (
                    <span className="text-xs text-green-600 flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" /> Saved
                    </span>
                  )}
                </div>
                <div className="space-y-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Note Type</label>
                    <select value={noteType} onChange={(e) => setNoteType(e.target.value)} disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50">
                      <option value="PROGRESS">Progress Note</option>
                      <option value="ASSESSMENT">Assessment</option>
                      <option value="DISCHARGE">Discharge Summary</option>
                      <option value="CONSULTATION">Consultation</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Subjective</label>
                    <textarea value={subjective} onChange={(e) => setSubjective(e.target.value)} disabled={!isActive} rows={2} placeholder="Patient's reported symptoms..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none disabled:bg-gray-50" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Objective</label>
                    <textarea value={objective} onChange={(e) => setObjective(e.target.value)} disabled={!isActive} rows={2} placeholder="Clinical findings..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none disabled:bg-gray-50" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Assessment</label>
                    <textarea value={assessment} onChange={(e) => setAssessment(e.target.value)} disabled={!isActive} rows={2} placeholder="Clinical assessment..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none disabled:bg-gray-50" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Plan</label>
                    <textarea value={plan} onChange={(e) => setPlan(e.target.value)} disabled={!isActive} rows={2} placeholder="Treatment plan..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none disabled:bg-gray-50" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Additional Notes</label>
                    <textarea value={noteBody} onChange={(e) => setNoteBody(e.target.value)} disabled={!isActive} rows={3} placeholder="Any additional notes..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none disabled:bg-gray-50" />
                  </div>
                </div>
                {noteError && <p className="mt-2 text-xs text-red-600">{noteError}</p>}
                {isActive && (
                  <button onClick={handleSaveNote} disabled={noteSaving}
                    className="mt-4 w-full py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors">
                    {noteSaving ? <><Loader2 className="w-4 h-4 animate-spin" /> Saving...</> : <><Save className="w-4 h-4" /> Save Note</>}
                  </button>
                )}
              </div>
            </div>

            {/* Examination Findings — Lovable-aligned system-by-system capture */}
            {isActive && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <Stethoscope className="w-5 h-5 text-teal-500" />
                    <h3 className="font-medium text-gray-900">Examination Findings</h3>
                  </div>
                  {examSaved && (
                    <span className="text-xs text-green-600 flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" /> Saved
                    </span>
                  )}
                </div>
                <div className="space-y-3">
                  {[
                    { key: "general", label: "General Appearance", value: examGeneral, setter: setExamGeneral, placeholder: "Alert, comfortable, not in distress..." },
                    { key: "cvs", label: "Cardiovascular", value: examCVS, setter: setExamCVS, placeholder: "S1 S2 normal, no murmurs..." },
                    { key: "resp", label: "Respiratory", value: examResp, setter: setExamResp, placeholder: "Clear bilaterally, good air entry..." },
                    { key: "abdo", label: "Abdominal", value: examAbdo, setter: setExamAbdo, placeholder: "Soft, non-tender, bowel sounds present..." },
                    { key: "neuro", label: "Neurological", value: examNeuro, setter: setExamNeuro, placeholder: "GCS 15/15, pupils equal and reactive..." },
                  ].map((sys) => (
                    <div key={sys.key}>
                      <label className="block text-xs font-medium text-gray-600 mb-1">{sys.label}</label>
                      <textarea
                        value={sys.value}
                        onChange={(e) => sys.setter(e.target.value)}
                        rows={2}
                        placeholder={sys.placeholder}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 resize-none"
                      />
                    </div>
                  ))}
                </div>
                <button
                  onClick={handleSaveExamination}
                  disabled={examSaving}
                  className="mt-4 w-full py-2 bg-teal-600 text-white text-sm font-medium rounded-lg hover:bg-teal-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
                >
                  {examSaving ? (
                    <><Loader2 className="w-4 h-4 animate-spin" /> Saving...</>
                  ) : (
                    <><Save className="w-4 h-4" /> Save Examination</>
                  )}
                </button>
              </div>
            )}

            {/* Linked Referrals & Outcomes */}
            {linkedReferrals.length > 0 && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-4">
                  <ArrowUpRight className="w-5 h-5 text-orange-500" />
                  <h3 className="font-medium text-gray-900">
                    Linked Referrals ({linkedReferrals.length})
                  </h3>
                  {respondedReferrals.length > 0 && (
                    <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-purple-100 text-purple-700">
                      {respondedReferrals.length} response{respondedReferrals.length !== 1 ? "s" : ""} received
                    </span>
                  )}
                </div>
                <div className="space-y-3">
                  {linkedReferrals.map((ref: ReferralResource) => {
                    const a = ref.attributes;
                    const hasResponse = a.response_notes || a.responseNotes;
                    const responseText = a.response_notes ?? a.responseNotes ?? "";
                    const outcomeText = a.outcome ?? "";
                    const respondedAt = a.responded_at ?? a.respondedAt;

                    return (
                      <div
                        key={ref.id}
                        className={`p-3 rounded-lg border ${
                          hasResponse ? "border-purple-200 bg-purple-50" : "border-gray-200 bg-gray-50"
                        }`}
                      >
                        <div className="flex items-start justify-between">
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-medium text-gray-900">
                                {ref.attributes.specialty || ref.attributes.referralType}
                              </span>
                              <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${
                                ref.attributes.status === "RESPONDED" ? "bg-purple-100 text-purple-700" :
                                ref.attributes.status === "COMPLETED" ? "bg-green-100 text-green-700" :
                                ref.attributes.status === "ACCEPTED" ? "bg-blue-100 text-blue-700" :
                                "bg-yellow-100 text-yellow-700"
                              }`}>
                                {ref.attributes.status}
                              </span>
                            </div>
                            <p className="text-xs text-gray-500 mt-0.5">
                              To: {ref.attributes.referredTo}
                              {ref.attributes.referredToFacility && ` at ${ref.attributes.referredToFacility}`}
                            </p>
                          </div>
                          <Link
                            href={`/ehr/${patientId}/referrals`}
                            className="text-xs text-blue-600 hover:text-blue-800"
                          >
                            View all
                          </Link>
                        </div>
                        {hasResponse && (
                          <div className="mt-2 pt-2 border-t border-purple-200">
                            <p className="text-xs font-semibold text-purple-700">Specialist Response:</p>
                            <p className="text-sm text-purple-900 mt-0.5">{responseText}</p>
                            {outcomeText && (
                              <p className="text-xs text-purple-700 mt-1">
                                <span className="font-medium">Outcome:</span> {outcomeText}
                              </p>
                            )}
                            {respondedAt && (
                              <p className="text-xs text-purple-500 mt-0.5">
                                {new Date(String(respondedAt)).toLocaleString()}
                              </p>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Close Encounter */}
            {isActive && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                {!showCloseConfirm ? (
                  <button
                    onClick={() => setShowCloseConfirm(true)}
                    className="w-full py-2.5 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 transition-colors flex items-center justify-center gap-2"
                  >
                    <XCircle className="w-4 h-4" />
                    Close Encounter
                  </button>
                ) : (
                  <div className="space-y-3">
                    <p className="text-sm text-gray-700 text-center">
                      Are you sure you want to close this encounter? This action cannot be undone.
                    </p>
                    <div className="flex gap-3">
                      <button onClick={() => setShowCloseConfirm(false)}
                        className="flex-1 py-2.5 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors">
                        Cancel
                      </button>
                      <button onClick={handleClose} disabled={closeEncounter.isPending}
                        className="flex-1 py-2.5 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors">
                        {closeEncounter.isPending ? (
                          <><Loader2 className="w-4 h-4 animate-spin" /> Closing...</>
                        ) : (
                          <><CheckCircle2 className="w-4 h-4" /> Confirm Close</>
                        )}
                      </button>
                    </div>
                    {closeEncounter.isError && (
                      <p className="text-sm text-red-600 text-center">Failed to close encounter. Please try again.</p>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
