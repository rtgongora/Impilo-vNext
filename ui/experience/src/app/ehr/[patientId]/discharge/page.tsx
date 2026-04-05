"use client";

/**
 * Visit Outcome — Encounter disposition with Lovable-aligned workflow.
 * Route: /ehr/[patientId]/discharge | pageTitle: "Visit Outcome"
 *
 * Disposition options (aligned to Lovable OutcomeSection):
 *   Discharge, Admit, Transfer, Refer, Death, LAMA
 *
 * Each disposition renders a type-specific form. The real API path
 * remains POST /encounters/{id}/discharge with discharge_type mapping.
 */

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  Loader2,
  CheckCircle2,
  AlertCircle,
  Home,
  Building,
  Ambulance,
  Send,
  Heart,
  UserX,
  ClipboardList,
  FileSignature,
  Calendar,
  AlertTriangle,
  Receipt } from "lucide-react";
import Link from "next/link";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { useEncounters, type EncounterResource } from "@/hooks/queries/useEncounters";
import { usePatient } from "@/hooks/queries/usePatients";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";

type DispositionType = "" | "DISCHARGE" | "ADMIT" | "TRANSFER" | "REFER" | "DEATH" | "LAMA";

const DISPOSITION_OPTIONS = [
  { id: "DISCHARGE" as const, label: "Discharge", icon: Home, description: "Patient ready for discharge home", color: "text-green-600", bg: "bg-green-50 border-green-200" },
  { id: "ADMIT" as const, label: "Admit", icon: Building, description: "Admit to inpatient ward", color: "text-blue-600", bg: "bg-blue-50 border-blue-200" },
  { id: "TRANSFER" as const, label: "Transfer", icon: Ambulance, description: "Transfer to another facility", color: "text-amber-600", bg: "bg-amber-50 border-amber-200" },
  { id: "REFER" as const, label: "Refer", icon: Send, description: "Refer for specialist care", color: "text-indigo-600", bg: "bg-indigo-50 border-indigo-200" },
  { id: "DEATH" as const, label: "Death", icon: Heart, description: "Record patient death", color: "text-red-600", bg: "bg-red-50 border-red-200" },
  { id: "LAMA" as const, label: "LAMA", icon: UserX, description: "Left against medical advice", color: "text-gray-600", bg: "bg-gray-50 border-gray-200" },
];

// Map UI disposition to the BFF discharge_type API values
const DISPOSITION_TO_DISCHARGE_TYPE: Record<string, string> = {
  DISCHARGE: "CLINICAL",
  ADMIT: "ADMIT",
  TRANSFER: "TRANSFER",
  REFER: "REFER",
  DEATH: "DEATH",
  LAMA: "AMA" };

export default function VisitOutcomePage() {
  const params = useParams<{ patientId: string }>();
  const router = useRouter();
  const { patientId } = params;
  const { user } = useAuthStore();
  const { isClinical } = useRoleGroup();

  const { data: encountersData, isLoading } = useEncounters(patientId);
  const { data: patientData } = usePatient(patientId);
  const patient = patientData?.data;

  const activeEncounter = encountersData?.data?.find(
    (e: EncounterResource) => e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS"
  );

  const [disposition, setDisposition] = useState<DispositionType>("");
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [billId, setBillId] = useState<string | null>(null);

  // Shared form fields (all dispositions use the same API)
  const [diagnosis, setDiagnosis] = useState("");
  const [treatmentSummary, setTreatmentSummary] = useState("");
  const [followUp, setFollowUp] = useState("");
  const [medications, setMedications] = useState("");
  const [instructions, setInstructions] = useState("");

  // Type-specific fields
  const [admitWard, setAdmitWard] = useState("");
  const [admitPriority, setAdmitPriority] = useState("Routine");
  const [transferFacility, setTransferFacility] = useState("");
  const [transferReason, setTransferReason] = useState("");
  const [referDestination, setReferDestination] = useState("");
  const [deathDate, setDeathDate] = useState("");
  const [deathTime, setDeathTime] = useState("");
  const [deathCause, setDeathCause] = useState("");
  const [lamaCircumstances, setLamaCircumstances] = useState("");

  async function handleComplete() {
    if (!activeEncounter || !disposition) return;
    setSubmitting(true);
    setError(null);

    // Build the discharge payload with type-specific detail in the summary fields
    const dischargeType = DISPOSITION_TO_DISCHARGE_TYPE[disposition] || disposition;
    let fullDiagnosis = diagnosis;
    let fullSummary = treatmentSummary;
    let fullInstructions = instructions;

    if (disposition === "ADMIT") {
      fullSummary = `Ward: ${admitWard}. Priority: ${admitPriority}. ${treatmentSummary}`.trim();
    } else if (disposition === "TRANSFER") {
      fullSummary = `Transfer to: ${transferFacility}. Reason: ${transferReason}. ${treatmentSummary}`.trim();
    } else if (disposition === "REFER") {
      fullSummary = `Referral to: ${referDestination}. ${treatmentSummary}`.trim();
    } else if (disposition === "DEATH") {
      fullDiagnosis = `Cause of death: ${deathCause}. Date/Time: ${deathDate} ${deathTime}. ${diagnosis}`.trim();
    } else if (disposition === "LAMA") {
      fullInstructions = `Circumstances: ${lamaCircumstances}. ${instructions}`.trim();
    }

    try {
      const result = await apiClient.post<{ data: { attributes: { costa_bill_id?: string; [key: string]: unknown } } }>(
        `/internal/v1/encounters/${activeEncounter.id}/discharge`,
        {
          discharge_type: dischargeType,
          discharge_diagnosis: fullDiagnosis || null,
          treatment_summary: fullSummary || null,
          follow_up_instructions: followUp || null,
          medications_at_discharge: medications || null,
          patient_instructions: fullInstructions || null,
          discharged_by: user?.id ?? "system" }
      );
      const resultBillId = result?.data?.attributes?.costa_bill_id;
      if (resultBillId) setBillId(resultBillId);
      setSubmitted(true);
    } catch {
      setError("Failed to complete encounter. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <EHRLayout>
      <PageShell title="Visit Outcome">

        {!isClinical ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm font-medium">Access Denied</p>
            <p className="text-gray-500 text-xs mt-1">Only clinical staff can perform discharge operations.</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : !activeEncounter ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No active encounter found</p>
          </div>
        ) : submitted ? (
          /* Post-completion */
          <div className="bg-white rounded-lg border border-green-200 p-8 text-center">
            <CheckCircle2 className="w-12 h-12 text-green-500 mx-auto mb-3" />
            <h3 className="text-lg font-semibold text-gray-900 mb-1">Encounter Completed</h3>
            <p className="text-sm text-gray-500 mb-4">
              Visit outcome ({DISPOSITION_OPTIONS.find(d => d.id === disposition)?.label}) has been recorded.
            </p>
            {billId && (
              <div className="mb-4 p-3 rounded-lg bg-emerald-50 border border-emerald-200">
                <p className="text-sm text-emerald-700 flex items-center justify-center gap-2">
                  <Receipt className="w-4 h-4" />
                  A billing draft has been created for this encounter.
                </p>
                <Link
                  href={`/finance/billing/${billId}`}
                  className="inline-flex items-center gap-1 mt-2 px-3 py-1.5 text-xs font-medium text-emerald-700 bg-emerald-100 rounded-lg hover:bg-emerald-200 transition-colors"
                >
                  <Receipt className="w-3 h-3" />
                  View Bill
                </Link>
              </div>
            )}
            <div className="flex justify-center gap-3">
              <button
                onClick={() => router.push("/queue")}
                className="px-4 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
              >
                Back to Queue
              </button>
              {disposition === "ADMIT" && (
                <button
                  onClick={() => router.push("/beds")}
                  className="px-4 py-2 bg-purple-600 text-white text-sm font-medium rounded-lg hover:bg-purple-700 transition-colors"
                >
                  Assign Bed
                </button>
              )}
              <button
                onClick={() => router.push(`/ehr/${patientId}`)}
                className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                Patient Chart
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Disposition Selection — Lovable-aligned visual grid */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-center gap-2 mb-4">
                <CheckCircle2 className="w-5 h-5 text-blue-600" />
                <h3 className="font-medium text-gray-900">Visit Disposition</h3>
              </div>
              <div className="grid grid-cols-3 gap-3">
                {DISPOSITION_OPTIONS.map((opt) => {
                  const Icon = opt.icon;
                  const selected = disposition === opt.id;
                  return (
                    <button
                      key={opt.id}
                      onClick={() => setDisposition(opt.id)}
                      className={`flex flex-col items-center gap-2 p-4 border-2 rounded-lg transition-all ${
                        selected
                          ? `border-blue-500 bg-blue-50`
                          : "border-gray-200 hover:bg-gray-50"
                      }`}
                    >
                      <div className={`w-12 h-12 rounded-full flex items-center justify-center ${
                        selected ? "bg-blue-100" : "bg-gray-100"
                      }`}>
                        <Icon className={`w-6 h-6 ${opt.color}`} />
                      </div>
                      <div className="text-center">
                        <div className="font-medium text-sm text-gray-900">{opt.label}</div>
                        <div className="text-[10px] text-gray-500">{opt.description}</div>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Disposition-specific form */}
            {disposition && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-4">
                  <FileSignature className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-medium text-gray-900">
                    {DISPOSITION_OPTIONS.find(d => d.id === disposition)?.label} Details
                  </h3>
                </div>

                <div className="space-y-4">
                  {/* Type-specific fields */}
                  {disposition === "ADMIT" && (
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <label className="block text-xs font-medium text-gray-600 mb-1">Admitting Ward</label>
                        <input type="text" value={admitWard} onChange={(e) => setAdmitWard(e.target.value)}
                          placeholder="e.g. Medical Ward 1, ICU, Maternity"
                          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <div>
                        <label className="block text-xs font-medium text-gray-600 mb-1">Priority</label>
                        <div className="flex gap-2 mt-1">
                          {["Routine", "Urgent", "Emergency"].map((p) => (
                            <button key={p} onClick={() => setAdmitPriority(p)}
                              className={`px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors ${
                                admitPriority === p ? "bg-blue-100 text-blue-700 border-blue-300" : "bg-white text-gray-600 border-gray-200 hover:bg-gray-50"
                              }`}>{p}</button>
                          ))}
                        </div>
                      </div>
                    </div>
                  )}

                  {disposition === "TRANSFER" && (
                    <>
                      <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg flex items-center gap-2">
                        <AlertTriangle className="w-4 h-4 text-amber-600" />
                        <span className="text-sm text-amber-700">Ensure receiving facility has confirmed acceptance</span>
                      </div>
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-medium text-gray-600 mb-1">Receiving Facility</label>
                          <input type="text" value={transferFacility} onChange={(e) => setTransferFacility(e.target.value)}
                            placeholder="Facility name"
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-gray-600 mb-1">Reason for Transfer</label>
                          <input type="text" value={transferReason} onChange={(e) => setTransferReason(e.target.value)}
                            placeholder="Why is the patient being transferred?"
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                        </div>
                      </div>
                    </>
                  )}

                  {disposition === "REFER" && (
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Referral Destination</label>
                      <input type="text" value={referDestination} onChange={(e) => setReferDestination(e.target.value)}
                        placeholder="e.g. Cardiology Clinic, Diabetes Clinic"
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                    </div>
                  )}

                  {disposition === "DEATH" && (
                    <>
                      <div className="p-3 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2">
                        <AlertCircle className="w-4 h-4 text-red-600" />
                        <span className="text-sm text-red-700">This action records a patient death</span>
                      </div>
                      <div className="grid grid-cols-3 gap-4">
                        <div>
                          <label className="block text-xs font-medium text-gray-600 mb-1">Date of Death</label>
                          <input type="date" value={deathDate} onChange={(e) => setDeathDate(e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-gray-600 mb-1">Time of Death</label>
                          <input type="time" value={deathTime} onChange={(e) => setDeathTime(e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-gray-600 mb-1">Primary Cause</label>
                          <input type="text" value={deathCause} onChange={(e) => setDeathCause(e.target.value)}
                            placeholder="Immediate cause of death"
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                        </div>
                      </div>
                    </>
                  )}

                  {disposition === "LAMA" && (
                    <>
                      <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg flex items-center gap-2">
                        <AlertTriangle className="w-4 h-4 text-amber-600" />
                        <span className="text-sm text-amber-700">Document all attempts to counsel the patient</span>
                      </div>
                      <div>
                        <label className="block text-xs font-medium text-gray-600 mb-1">Circumstances</label>
                        <textarea value={lamaCircumstances} onChange={(e) => setLamaCircumstances(e.target.value)}
                          rows={2} placeholder="Describe the circumstances..."
                          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
                      </div>
                    </>
                  )}

                  {/* Common fields — always shown for all dispositions */}
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Diagnosis</label>
                    <textarea value={diagnosis} onChange={(e) => setDiagnosis(e.target.value)}
                      rows={2} placeholder="Primary and secondary diagnoses..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Treatment Summary</label>
                    <textarea value={treatmentSummary} onChange={(e) => setTreatmentSummary(e.target.value)}
                      rows={2} placeholder="Summary of treatments and procedures..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
                  </div>
                  {(disposition === "DISCHARGE" || disposition === "REFER") && (
                    <>
                      <div>
                        <label className="block text-xs font-medium text-gray-600 mb-1">Follow-up Instructions</label>
                        <textarea value={followUp} onChange={(e) => setFollowUp(e.target.value)}
                          rows={2} placeholder="Follow-up appointments, return criteria..."
                          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
                      </div>
                      <div>
                        <label className="block text-xs font-medium text-gray-600 mb-1">Medications at Discharge</label>
                        <textarea value={medications} onChange={(e) => setMedications(e.target.value)}
                          rows={2} placeholder="Medications with dosages..."
                          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
                      </div>
                    </>
                  )}
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      {disposition === "LAMA" ? "Additional Notes" : "Patient Instructions"}
                    </label>
                    <textarea value={instructions} onChange={(e) => setInstructions(e.target.value)}
                      rows={2} placeholder={disposition === "LAMA" ? "Additional documentation..." : "Instructions for the patient..."}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
                  </div>
                </div>

                {error && <p className="mt-3 text-xs text-red-600">{error}</p>}

                <div className="flex justify-end gap-3 mt-5">
                  <button
                    onClick={() => setDisposition("")}
                    className="px-4 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleComplete}
                    disabled={submitting}
                    className="px-5 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2 transition-colors"
                  >
                    {submitting ? (
                      <><Loader2 className="w-4 h-4 animate-spin" /> Completing...</>
                    ) : (
                      <><CheckCircle2 className="w-4 h-4" /> Complete Encounter</>
                    )}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
