"use client";

/**
 * Patient Summary — Aggregated overview of clinical data.
 * Route: /ehr/[patientId]/summary | pageTitle: "Patient Summary"
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Loader2,
  User,
  Activity,
  AlertCircle,
  Pill,
  FileText,
  TestTube2,
  Syringe } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function PatientSummaryPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data: patientData, isLoading: loadingPatient } = usePatient(patientId);
  const { data: encountersData } = useEncounters(patientId);

  const { data: allergiesData } = useQuery({
    queryKey: ["allergies", { patientId }],
    queryFn: () => apiClient.get<ApiResponse<Array<{ id: string; type: string; attributes: Record<string, unknown> }>>>(`/internal/v1/allergies?patient_id=${patientId}`),
    enabled: !!patientId });

  const { data: conditionsData } = useQuery({
    queryKey: ["conditions", { patientId }],
    queryFn: () => apiClient.get<ApiResponse<Array<{ id: string; type: string; attributes: Record<string, unknown> }>>>(`/internal/v1/conditions?patient_id=${patientId}`),
    enabled: !!patientId });

  const { data: medsData } = useQuery({
    queryKey: ["prescriptions", { patientId }],
    queryFn: () => apiClient.get<ApiResponse<Array<{ id: string; type: string; attributes: Record<string, unknown> }>>>(`/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`),
    enabled: !!patientId });

  const patient = patientData?.data;
  const encounters = encountersData?.data ?? [];
  const allergies = allergiesData?.data ?? [];
  const conditions = conditionsData?.data ?? [];
  const medications = medsData?.data ?? [];
  const activeEncounter = encounters.find((e) => e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS");
  const activeConditions = conditions.filter((c) => c.attributes.clinical_status === "ACTIVE" || c.attributes.clinicalStatus === "ACTIVE");
  const activeMeds = medications.filter((m) => m.attributes.status === "PENDING" || m.attributes.status === "ACTIVE");

  return (
    <EHRLayout>
      <PageShell title="Patient Summary">

        {loadingPatient ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : !patient ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <User className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Patient not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Demographics */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-center gap-4">
                <div className="w-14 h-14 rounded-full bg-blue-100 flex items-center justify-center">
                  <User className="w-7 h-7 text-blue-600" />
                </div>
                <div>
                  <h2 className="text-lg font-semibold text-gray-900">{patient.attributes.displayName}</h2>
                  <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-gray-500 mt-1">
                    <span>DOB: {patient.attributes.dateOfBirth}</span>
                    <span>Gender: {patient.attributes.gender}</span>
                    <span>CPID: {patient.attributes.cpid}</span>
                  </div>
                </div>
                {activeEncounter && (
                  <Link href={`/ehr/${patientId}/encounter/${activeEncounter.id}`}
                    className="ml-auto px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 transition-colors">
                    Active Encounter
                  </Link>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Active Conditions */}
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-3">
                  <AlertCircle className="w-5 h-5 text-orange-500" />
                  <h3 className="font-medium text-gray-900">Active Conditions ({activeConditions.length})</h3>
                </div>
                {activeConditions.length === 0 ? (
                  <p className="text-sm text-gray-400">No active conditions</p>
                ) : (
                  <div className="space-y-2">
                    {activeConditions.slice(0, 5).map((c) => (
                      <div key={c.id} className="flex items-center justify-between p-2 rounded bg-gray-50">
                        <div>
                          <p className="text-sm font-medium text-gray-900">{String(c.attributes.condition_name || c.attributes.conditionName)}</p>
                          {c.attributes.icd_code && <p className="text-xs text-gray-500">ICD: {String(c.attributes.icd_code || c.attributes.icdCode)}</p>}
                        </div>
                        <span className="px-2 py-0.5 text-xs rounded-full bg-red-100 text-red-700">Active</span>
                      </div>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/conditions`} className="mt-3 inline-block text-xs text-blue-600 hover:text-blue-800">View all conditions</Link>
              </div>

              {/* Allergies */}
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-3">
                  <AlertCircle className="w-5 h-5 text-red-500" />
                  <h3 className="font-medium text-gray-900">Allergies ({allergies.length})</h3>
                </div>
                {allergies.length === 0 ? (
                  <p className="text-sm text-green-600">No known allergies (NKA)</p>
                ) : (
                  <div className="space-y-2">
                    {allergies.slice(0, 5).map((a) => (
                      <div key={a.id} className="flex items-center justify-between p-2 rounded bg-gray-50">
                        <div>
                          <p className="text-sm font-medium text-gray-900">{String(a.attributes.allergen)}</p>
                          <p className="text-xs text-gray-500">{String(a.attributes.reaction || "")}</p>
                        </div>
                        <span className={`px-2 py-0.5 text-xs rounded-full ${
                          a.attributes.severity === "SEVERE" ? "bg-red-100 text-red-700" :
                          a.attributes.severity === "MODERATE" ? "bg-yellow-100 text-yellow-700" :
                          "bg-green-100 text-green-700"
                        }`}>{String(a.attributes.severity)}</span>
                      </div>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/allergies`} className="mt-3 inline-block text-xs text-blue-600 hover:text-blue-800">View all allergies</Link>
              </div>

              {/* Active Medications */}
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-3">
                  <Pill className="w-5 h-5 text-green-500" />
                  <h3 className="font-medium text-gray-900">Active Medications ({activeMeds.length})</h3>
                </div>
                {activeMeds.length === 0 ? (
                  <p className="text-sm text-gray-400">No active medications</p>
                ) : (
                  <div className="space-y-2">
                    {activeMeds.slice(0, 5).map((m) => (
                      <div key={m.id} className="p-2 rounded bg-gray-50">
                        <p className="text-sm font-medium text-gray-900">{String(m.attributes.medication_name || m.attributes.medicationName)}</p>
                        <p className="text-xs text-gray-500">{String(m.attributes.dosage || "")} - {String(m.attributes.frequency || "")}</p>
                      </div>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/medications`} className="mt-3 inline-block text-xs text-blue-600 hover:text-blue-800">View all medications</Link>
              </div>

              {/* Recent Encounters */}
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-3">
                  <FileText className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-medium text-gray-900">Recent Encounters ({encounters.length})</h3>
                </div>
                {encounters.length === 0 ? (
                  <p className="text-sm text-gray-400">No encounters</p>
                ) : (
                  <div className="space-y-2">
                    {encounters.slice(0, 5).map((e) => (
                      <Link key={e.id} href={`/ehr/${patientId}/encounter/${e.id}`}
                        className="flex items-center justify-between p-2 rounded bg-gray-50 hover:bg-gray-100 transition-colors">
                        <div>
                          <p className="text-sm font-medium text-gray-900">{e.attributes.encounterType}</p>
                          <p className="text-xs text-gray-500">{new Date(e.attributes.startedAt).toLocaleDateString()}</p>
                        </div>
                        <span className={`px-2 py-0.5 text-xs rounded-full ${
                          e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"
                        }`}>{e.attributes.status}</span>
                      </Link>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/encounters`} className="mt-3 inline-block text-xs text-blue-600 hover:text-blue-800">View all encounters</Link>
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
