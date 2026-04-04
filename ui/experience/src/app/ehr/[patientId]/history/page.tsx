"use client";

/**
 * Clinical History — Lovable-aligned structured clinical history.
 * Route: /ehr/[patientId]/history | pageTitle: "History"
 *
 * Combines:
 * - Presenting complaint from active encounter
 * - History of Present Illness (HPI) capture via clinical notes
 * - Past medical history (active conditions)
 * - Current medications cross-reference
 * - Allergies cross-reference
 * - Past encounters and resolved conditions
 * - Immunization history
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Loader2,
  Clock,
  AlertCircle,
  Activity,
  FileText,
  Stethoscope,
  Pill,
  ShieldAlert,
  Syringe,
  Save,
  CheckCircle2,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface GenericResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

export default function ClinicalHistoryPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const { user } = useAuthStore();

  const { data: encountersData, isLoading: loadingEnc } = useEncounters(patientId);
  const { data: conditionsData, isLoading: loadingCond } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["conditions", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/conditions?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const { data: allergiesData, isLoading: loadingAllergy } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["allergies", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/allergies?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const { data: medsData, isLoading: loadingMeds } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["prescriptions", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const { data: immunData, isLoading: loadingImmun } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["immunizations", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/immunizations?patient_id=${patientId}`),
    enabled: !!patientId,
  });

  const encounters = encountersData?.data ?? [];
  const conditions = (conditionsData?.data ?? []);
  const allergies = (allergiesData?.data ?? []);
  const medications = (medsData?.data ?? []);
  const immunizations = (immunData?.data ?? []);

  const activeEncounter = encounters.find(
    (e) => e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );
  const activeConditions = conditions.filter(
    (c) => c.attributes.clinical_status === "ACTIVE" || c.attributes.clinicalStatus === "ACTIVE"
  );
  const resolvedConditions = conditions.filter(
    (c) => c.attributes.clinical_status === "RESOLVED" || c.attributes.clinicalStatus === "RESOLVED"
  );
  const activeAllergies = allergies.filter((a) => a.attributes.status === "ACTIVE");
  const activeMeds = medications.filter(
    (m) => m.attributes.status === "PENDING" || m.attributes.status === "ACTIVE"
  );
  const completedEncounters = encounters.filter(
    (e) => e.attributes.status !== "IN_PROGRESS" && e.attributes.status !== "ACTIVE"
  );

  // HPI capture state
  const [hpiText, setHpiText] = useState("");
  const [hpiSaving, setHpiSaving] = useState(false);
  const [hpiSaved, setHpiSaved] = useState(false);

  async function handleSaveHPI() {
    if (!activeEncounter || !hpiText.trim()) return;
    setHpiSaving(true);
    setHpiSaved(false);
    try {
      await apiClient.post("/internal/v1/clinical-notes", {
        patient_id: patientId,
        encounter_id: activeEncounter.id,
        note_type: "HISTORY",
        body: hpiText.trim(),
        author_id: user?.id ?? "system",
        author_name: user?.displayName ?? user?.email ?? "Provider",
      });
      setHpiSaved(true);
    } catch {
      // Error handled by UI
    } finally {
      setHpiSaving(false);
    }
  }

  const isLoading = loadingEnc || loadingCond || loadingAllergy || loadingMeds || loadingImmun;

  return (
    <EHRLayout>
      <PageShell title="History">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : (
          <div className="space-y-5">
            {/* Presenting Complaint — from active encounter */}
            {activeEncounter && (activeEncounter.attributes as Record<string, unknown>).chief_complaint && (
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-2">
                  <FileText className="w-4 h-4 text-blue-500" />
                  <h3 className="text-sm font-medium text-gray-900">Presenting Complaint</h3>
                </div>
                <p className="text-sm text-gray-700">
                  {String((activeEncounter.attributes as Record<string, unknown>).chief_complaint)}
                </p>
              </div>
            )}

            {/* History of Present Illness — capture */}
            {activeEncounter && (
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <FileText className="w-4 h-4 text-indigo-500" />
                    <h3 className="text-sm font-medium text-gray-900">History of Present Illness</h3>
                  </div>
                  {hpiSaved && (
                    <span className="text-xs text-green-600 flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" /> Saved
                    </span>
                  )}
                </div>
                <textarea
                  value={hpiText}
                  onChange={(e) => setHpiText(e.target.value)}
                  rows={4}
                  placeholder="Document the history of presenting illness — onset, duration, character, associated symptoms, aggravating/relieving factors, previous treatments..."
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                />
                <button
                  onClick={handleSaveHPI}
                  disabled={hpiSaving || !hpiText.trim()}
                  className="mt-2 px-4 py-1.5 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-50 flex items-center gap-2 transition-colors"
                >
                  {hpiSaving ? (
                    <><Loader2 className="w-3.5 h-3.5 animate-spin" /> Saving...</>
                  ) : (
                    <><Save className="w-3.5 h-3.5" /> Save HPI</>
                  )}
                </button>
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Past Medical History — active conditions */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Stethoscope className="w-4 h-4 text-orange-500" />
                  <h3 className="text-sm font-medium text-gray-900">Past Medical History ({activeConditions.length})</h3>
                </div>
                {activeConditions.length === 0 ? (
                  <p className="text-sm text-gray-400">No active conditions recorded</p>
                ) : (
                  <div className="space-y-1.5">
                    {activeConditions.map((c) => (
                      <div key={c.id} className="flex items-center justify-between p-2 rounded bg-gray-50">
                        <div>
                          <span className="text-sm font-medium text-gray-900">
                            {String(c.attributes.condition_name ?? c.attributes.conditionName ?? "")}
                          </span>
                          {c.attributes.icd_code && (
                            <span className="text-xs text-gray-500 ml-2">
                              ({String(c.attributes.icd_code ?? c.attributes.icdCode ?? "")})
                            </span>
                          )}
                        </div>
                        <span className="text-xs text-gray-500 capitalize">
                          {String(c.attributes.severity ?? "")}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/conditions`} className="mt-2 inline-block text-xs text-blue-600 hover:text-blue-800">
                  Manage conditions
                </Link>
              </div>

              {/* Current Medications */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Pill className="w-4 h-4 text-green-500" />
                  <h3 className="text-sm font-medium text-gray-900">Current Medications ({activeMeds.length})</h3>
                </div>
                {activeMeds.length === 0 ? (
                  <p className="text-sm text-gray-400">No active medications</p>
                ) : (
                  <div className="space-y-1.5">
                    {activeMeds.map((m) => (
                      <div key={m.id} className="p-2 rounded bg-gray-50">
                        <span className="text-sm font-medium text-gray-900">
                          {String(m.attributes.medication_name ?? m.attributes.medicationName ?? "")}
                        </span>
                        <span className="text-xs text-gray-500 ml-2">
                          {String(m.attributes.dosage ?? "")} {String(m.attributes.frequency ?? "")}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/medications`} className="mt-2 inline-block text-xs text-blue-600 hover:text-blue-800">
                  Manage medications
                </Link>
              </div>

              {/* Allergies */}
              <div className={`bg-white rounded-lg border p-4 ${activeAllergies.some((a) => a.attributes.severity === "SEVERE") ? "border-red-200" : "border-gray-200"}`}>
                <div className="flex items-center gap-2 mb-3">
                  <ShieldAlert className={`w-4 h-4 ${activeAllergies.length > 0 ? "text-red-500" : "text-green-500"}`} />
                  <h3 className="text-sm font-medium text-gray-900">Allergies ({activeAllergies.length})</h3>
                </div>
                {activeAllergies.length === 0 ? (
                  <p className="text-sm text-green-600">No known allergies (NKDA)</p>
                ) : (
                  <div className="space-y-1.5">
                    {activeAllergies.map((a) => (
                      <div key={a.id} className={`p-2 rounded ${
                        a.attributes.severity === "SEVERE" ? "bg-red-50" : "bg-gray-50"
                      }`}>
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium text-gray-900">{String(a.attributes.allergen)}</span>
                          <span className={`text-xs capitalize ${
                            a.attributes.severity === "SEVERE" ? "text-red-700" : "text-gray-500"
                          }`}>{String(a.attributes.severity)}</span>
                        </div>
                        {a.attributes.reaction && (
                          <p className="text-xs text-gray-500 mt-0.5">{String(a.attributes.reaction)}</p>
                        )}
                      </div>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/allergies`} className="mt-2 inline-block text-xs text-blue-600 hover:text-blue-800">
                  Manage allergies
                </Link>
              </div>

              {/* Resolved Conditions */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-3">
                  <AlertCircle className="w-4 h-4 text-green-500" />
                  <h3 className="text-sm font-medium text-gray-900">Resolved Conditions ({resolvedConditions.length})</h3>
                </div>
                {resolvedConditions.length === 0 ? (
                  <p className="text-sm text-gray-400">No resolved conditions</p>
                ) : (
                  <div className="space-y-1.5">
                    {resolvedConditions.slice(0, 5).map((c) => (
                      <div key={c.id} className="flex items-center justify-between p-2 rounded bg-gray-50">
                        <span className="text-sm text-gray-700">
                          {String(c.attributes.condition_name ?? c.attributes.conditionName ?? "")}
                        </span>
                        <span className="px-2 py-0.5 text-xs rounded-full bg-green-100 text-green-700">Resolved</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Past Encounters */}
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <div className="flex items-center gap-2 mb-3">
                <Clock className="w-4 h-4 text-cyan-500" />
                <h3 className="text-sm font-medium text-gray-900">Past Encounters ({completedEncounters.length})</h3>
              </div>
              {completedEncounters.length === 0 ? (
                <p className="text-sm text-gray-400">No completed encounters</p>
              ) : (
                <div className="space-y-1.5">
                  {completedEncounters.slice(0, 8).map((enc) => (
                    <Link
                      key={enc.id}
                      href={`/ehr/${patientId}/encounter/${enc.id}`}
                      className="flex items-center justify-between p-2 rounded bg-gray-50 hover:bg-gray-100 transition-colors"
                    >
                      <div>
                        <span className="text-sm font-medium text-gray-900">{enc.attributes.encounterType}</span>
                        <span className="text-xs text-gray-500 ml-2">
                          {new Date(enc.attributes.startedAt).toLocaleDateString()}
                        </span>
                      </div>
                      <span className="px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-600">
                        {enc.attributes.status}
                      </span>
                    </Link>
                  ))}
                </div>
              )}
            </div>

            {/* Immunization History */}
            {immunizations.length > 0 && (
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Syringe className="w-4 h-4 text-teal-500" />
                  <h3 className="text-sm font-medium text-gray-900">Immunizations ({immunizations.length})</h3>
                </div>
                <div className="space-y-1.5">
                  {immunizations.slice(0, 8).map((imm) => (
                    <div key={imm.id} className="flex items-center justify-between p-2 rounded bg-gray-50">
                      <span className="text-sm text-gray-900">
                        {String(imm.attributes.vaccine_name ?? imm.attributes.vaccineName ?? "")}
                      </span>
                      <span className="text-xs text-gray-500">
                        {imm.attributes.administered_at
                          ? new Date(String(imm.attributes.administered_at ?? imm.attributes.administeredAt)).toLocaleDateString()
                          : ""}
                      </span>
                    </div>
                  ))}
                </div>
                <Link href={`/ehr/${patientId}/immunizations`} className="mt-2 inline-block text-xs text-blue-600 hover:text-blue-800">
                  View all immunizations
                </Link>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
