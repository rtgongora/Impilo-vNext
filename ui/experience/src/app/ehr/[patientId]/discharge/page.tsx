"use client";

/**
 * Discharge Summary — Create and submit a discharge summary for an active encounter.
 * Route: /ehr/[patientId]/discharge | pageTitle: "Discharge Summary"
 */

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  FileSignature,
  CheckCircle2,
  ClipboardList,
  AlertCircle,
  User,
  Clock,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { usePatient } from "@/hooks/queries/usePatients";
import type { EncounterResource } from "@/hooks/queries/useEncounters";

const DISCHARGE_TYPES = [
  { value: "REGULAR", label: "Regular Discharge" },
  { value: "TRANSFER", label: "Transfer to Another Facility" },
  { value: "AGAINST_ADVICE", label: "Discharge Against Medical Advice" },
  { value: "DECEASED", label: "Deceased" },
] as const;

export default function DischargePage() {
  const params = useParams<{ patientId: string }>();
  const router = useRouter();
  const { patientId } = params;

  const { data: encountersData, isLoading: isLoadingEncounters } = useEncounters(patientId);
  const { data: patientData } = usePatient(patientId);

  const patient = patientData?.data;

  // Find the active encounter for this patient
  const activeEncounter = encountersData?.data?.find(
    (e: EncounterResource) =>
      e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS"
  );

  // Form state
  const [dischargeType, setDischargeType] = useState("REGULAR");
  const [dischargeDiagnosis, setDischargeDiagnosis] = useState("");
  const [treatmentSummary, setTreatmentSummary] = useState("");
  const [followUpInstructions, setFollowUpInstructions] = useState("");
  const [medicationsAtDischarge, setMedicationsAtDischarge] = useState("");
  const [patientInstructions, setPatientInstructions] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmitDischarge() {
    if (!activeEncounter) return;

    setSubmitting(true);
    setError(null);
    setSubmitted(false);

    try {
      await apiClient.post(
        `/internal/v1/encounters/${activeEncounter.id}/discharge`,
        {
          discharge_type: dischargeType,
          discharge_diagnosis: dischargeDiagnosis || null,
          treatment_summary: treatmentSummary || null,
          follow_up_instructions: followUpInstructions || null,
          medications_at_discharge: medicationsAtDischarge || null,
          patient_instructions: patientInstructions || null,
        }
      );
      setSubmitted(true);
    } catch {
      setError("Failed to submit discharge summary. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <EHRLayout>
      <PageShell title="Discharge Summary">
        <div className="mb-4">
          <Link
            href={`/ehr/${patientId}`}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to patient chart
          </Link>
        </div>

        {isLoadingEncounters ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading encounter...</span>
          </div>
        ) : !activeEncounter ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No active encounter found for this patient</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Encounter & Patient Header */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-center gap-3">
                <ClipboardList className="w-5 h-5 text-blue-500" />
                <h2 className="text-lg font-semibold text-gray-900">
                  {activeEncounter.attributes.encounterType} Encounter
                </h2>
                <span className="px-2.5 py-0.5 text-xs font-medium rounded-full bg-green-100 text-green-700">
                  {activeEncounter.attributes.status}
                </span>
              </div>
              {patient && (
                <div className="flex items-center gap-2 mt-2 text-sm text-gray-500">
                  <User className="w-4 h-4" />
                  <span>{patient.attributes.displayName}</span>
                  <span className="text-gray-300">|</span>
                  <span>CPID: {patient.attributes.cpid}</span>
                </div>
              )}
              <div className="flex items-center gap-2 mt-1 text-sm text-gray-500">
                <Clock className="w-4 h-4" />
                <span>
                  Started: {new Date(activeEncounter.attributes.startedAt).toLocaleString()}
                </span>
              </div>
            </div>

            {/* Success State */}
            {submitted ? (
              <div className="bg-white rounded-lg border border-green-200 p-8 text-center">
                <CheckCircle2 className="w-12 h-12 text-green-500 mx-auto mb-3" />
                <h3 className="text-lg font-semibold text-gray-900 mb-1">
                  Discharge Summary Submitted
                </h3>
                <p className="text-sm text-gray-500 mb-4">
                  The discharge summary has been recorded successfully.
                </p>
                <button
                  onClick={() => router.push(`/ehr/${patientId}`)}
                  className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
                >
                  Return to Patient Chart
                </button>
              </div>
            ) : (
              /* Discharge Form */
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-4">
                  <FileSignature className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-medium text-gray-900">Discharge Summary Form</h3>
                </div>

                <div className="space-y-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Discharge Type
                    </label>
                    <select
                      value={dischargeType}
                      onChange={(e) => setDischargeType(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      {DISCHARGE_TYPES.map((dt) => (
                        <option key={dt.value} value={dt.value}>
                          {dt.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Discharge Diagnosis
                    </label>
                    <textarea
                      value={dischargeDiagnosis}
                      onChange={(e) => setDischargeDiagnosis(e.target.value)}
                      rows={3}
                      placeholder="Primary and secondary diagnoses at discharge..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Treatment Summary
                    </label>
                    <textarea
                      value={treatmentSummary}
                      onChange={(e) => setTreatmentSummary(e.target.value)}
                      rows={3}
                      placeholder="Summary of treatments and procedures performed..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Follow-up Instructions
                    </label>
                    <textarea
                      value={followUpInstructions}
                      onChange={(e) => setFollowUpInstructions(e.target.value)}
                      rows={3}
                      placeholder="Follow-up appointments, referrals, and return criteria..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Medications at Discharge
                    </label>
                    <textarea
                      value={medicationsAtDischarge}
                      onChange={(e) => setMedicationsAtDischarge(e.target.value)}
                      rows={3}
                      placeholder="List of medications prescribed at discharge with dosages..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Patient Instructions
                    </label>
                    <textarea
                      value={patientInstructions}
                      onChange={(e) => setPatientInstructions(e.target.value)}
                      rows={3}
                      placeholder="Instructions for the patient regarding care after discharge..."
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                    />
                  </div>
                </div>

                {error && <p className="mt-3 text-xs text-red-600">{error}</p>}

                <button
                  onClick={handleSubmitDischarge}
                  disabled={submitting}
                  className="mt-4 w-full py-2.5 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
                >
                  {submitting ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" /> Submitting...
                    </>
                  ) : (
                    <>
                      <FileSignature className="w-4 h-4" /> Submit Discharge Summary
                    </>
                  )}
                </button>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
