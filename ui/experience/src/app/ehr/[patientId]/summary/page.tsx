"use client";

/**
 * Patient Summary — Lovable-aligned clinical overview.
 * Route: /ehr/[patientId]/summary | pageTitle: "Summary"
 *
 * Shows encounter status, active conditions, allergies, current
 * medications, recent vitals, and recent encounters in a clinical
 * dashboard layout. Patient demographics are in the PatientBanner.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Loader2,
  Activity,
  AlertCircle,
  Pill,
  FileText,
  HeartPulse,
  Stethoscope,
  ShieldAlert,
  Clock,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface GenericResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

export default function PatientSummaryPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data: patientData, isLoading: loadingPatient } = usePatient(patientId);
  const { data: encountersData } = useEncounters(patientId);

  const { data: allergiesData } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["allergies", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/allergies?patient_id=${patientId}`),
    enabled: !!patientId,
  });

  const { data: conditionsData } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["conditions", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/conditions?patient_id=${patientId}`),
    enabled: !!patientId,
  });

  const { data: medsData } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["prescriptions", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`),
    enabled: !!patientId,
  });

  const { data: vitalsData } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["vitals", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/vitals?patient_id=${patientId}`),
    enabled: !!patientId,
  });

  const patient = patientData?.data;
  const encounters = encountersData?.data ?? [];
  const allergies = (allergiesData?.data ?? []);
  const conditions = (conditionsData?.data ?? []);
  const medications = (medsData?.data ?? []);
  const vitals = (vitalsData?.data ?? []);
  const activeEncounter = encounters.find(
    (e) => e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS"
  );
  const activeConditions = conditions.filter(
    (c) => c.attributes.clinical_status === "ACTIVE" || c.attributes.clinicalStatus === "ACTIVE"
  );
  const activeAllergies = allergies.filter(
    (a) => a.attributes.status === "ACTIVE"
  );
  const activeMeds = medications.filter(
    (m) => m.attributes.status === "PENDING" || m.attributes.status === "ACTIVE"
  );
  const latestVitals = vitals.length > 0 ? vitals[0] : null;

  return (
    <EHRLayout>
      <PageShell title="Summary">

        {loadingPatient ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : !patient ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Patient not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Encounter Status Card */}
            {activeEncounter && (
              <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-green-100 flex items-center justify-center">
                      <Activity className="w-5 h-5 text-green-600" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-green-800">
                        Active {activeEncounter.attributes.encounterType} Encounter
                      </p>
                      <p className="text-xs text-green-600">
                        Since {new Date(activeEncounter.attributes.startedAt).toLocaleString()}
                      </p>
                    </div>
                  </div>
                  <Link
                    href={`/ehr/${patientId}/encounter/${activeEncounter.id}`}
                    className="px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 transition-colors"
                  >
                    Open Encounter
                  </Link>
                </div>
              </div>
            )}

            {/* Latest Vitals */}
            {latestVitals && (
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-3">
                  <HeartPulse className="w-4 h-4 text-red-500" />
                  <h3 className="text-sm font-medium text-gray-900">Latest Vitals</h3>
                  <span className="text-xs text-gray-400 ml-auto">
                    {latestVitals.attributes.created_at
                      ? new Date(String(latestVitals.attributes.created_at)).toLocaleString()
                      : ""}
                  </span>
                </div>
                <div className="grid grid-cols-3 sm:grid-cols-5 gap-3">
                  {latestVitals.attributes.systolic != null && (
                    <div className="text-center p-2 bg-gray-50 rounded-lg">
                      <div className="text-lg font-semibold text-gray-900">
                        {String(latestVitals.attributes.systolic)}/{String(latestVitals.attributes.diastolic ?? "")}
                      </div>
                      <div className="text-[10px] text-gray-500">BP (mmHg)</div>
                    </div>
                  )}
                  {latestVitals.attributes.heart_rate != null && (
                    <div className="text-center p-2 bg-gray-50 rounded-lg">
                      <div className="text-lg font-semibold text-gray-900">{String(latestVitals.attributes.heart_rate)}</div>
                      <div className="text-[10px] text-gray-500">HR (bpm)</div>
                    </div>
                  )}
                  {latestVitals.attributes.temperature != null && (
                    <div className="text-center p-2 bg-gray-50 rounded-lg">
                      <div className="text-lg font-semibold text-gray-900">{String(latestVitals.attributes.temperature)}</div>
                      <div className="text-[10px] text-gray-500">Temp (°C)</div>
                    </div>
                  )}
                  {latestVitals.attributes.oxygen_saturation != null && (
                    <div className="text-center p-2 bg-gray-50 rounded-lg">
                      <div className="text-lg font-semibold text-gray-900">{String(latestVitals.attributes.oxygen_saturation)}%</div>
                      <div className="text-[10px] text-gray-500">SpO₂</div>
                    </div>
                  )}
                  {latestVitals.attributes.respiratory_rate != null && (
                    <div className="text-center p-2 bg-gray-50 rounded-lg">
                      <div className="text-lg font-semibold text-gray-900">{String(latestVitals.attributes.respiratory_rate)}</div>
                      <div className="text-[10px] text-gray-500">RR (/min)</div>
                    </div>
                  )}
                </div>
                <Link href={`/ehr/${patientId}/vitals`} className="mt-2 inline-block text-xs text-blue-600 hover:text-blue-800">
                  View all vitals
                </Link>
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Active Conditions */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Stethoscope className="w-4 h-4 text-orange-500" />
                  <h3 className="text-sm font-medium text-gray-900">Active Conditions ({activeConditions.length})</h3>
                </div>
                {activeConditions.length === 0 ? (
                  <p className="text-sm text-gray-400">No active conditions</p>
                ) : (
                  <div className="space-y-1.5">
                    {activeConditions.slice(0, 5).map((c) => (
                      <div key={c.id} className="flex items-center justify-between p-2 rounded bg-gray-50">
                        <span className="text-sm font-medium text-gray-900">
                          {String(c.attributes.condition_name ?? c.attributes.conditionName ?? "")}
                        </span>
                        <span className="text-xs text-gray-500 capitalize">
                          {String(c.attributes.severity ?? "")}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/conditions`} className="mt-2 inline-block text-xs text-blue-600 hover:text-blue-800">
                  View all
                </Link>
              </div>

              {/* Allergies */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-3">
                  <ShieldAlert className="w-4 h-4 text-red-500" />
                  <h3 className="text-sm font-medium text-gray-900">Allergies ({activeAllergies.length})</h3>
                </div>
                {activeAllergies.length === 0 ? (
                  <p className="text-sm text-green-600">No known allergies (NKDA)</p>
                ) : (
                  <div className="space-y-1.5">
                    {activeAllergies.slice(0, 5).map((a) => (
                      <div key={a.id} className={`flex items-center justify-between p-2 rounded ${
                        a.attributes.severity === "SEVERE" ? "bg-red-50" : "bg-gray-50"
                      }`}>
                        <span className="text-sm font-medium text-gray-900">{String(a.attributes.allergen)}</span>
                        <span className={`text-xs capitalize ${
                          a.attributes.severity === "SEVERE" ? "text-red-700" : "text-gray-500"
                        }`}>{String(a.attributes.severity)}</span>
                      </div>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/allergies`} className="mt-2 inline-block text-xs text-blue-600 hover:text-blue-800">
                  View all
                </Link>
              </div>

              {/* Active Medications */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Pill className="w-4 h-4 text-green-500" />
                  <h3 className="text-sm font-medium text-gray-900">Medications ({activeMeds.length})</h3>
                </div>
                {activeMeds.length === 0 ? (
                  <p className="text-sm text-gray-400">No active medications</p>
                ) : (
                  <div className="space-y-1.5">
                    {activeMeds.slice(0, 5).map((m) => (
                      <div key={m.id} className="p-2 rounded bg-gray-50">
                        <p className="text-sm font-medium text-gray-900">
                          {String(m.attributes.medication_name ?? m.attributes.medicationName ?? "")}
                        </p>
                        <p className="text-xs text-gray-500">
                          {String(m.attributes.dosage ?? "")} {String(m.attributes.frequency ?? "")}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/medications`} className="mt-2 inline-block text-xs text-blue-600 hover:text-blue-800">
                  View all
                </Link>
              </div>

              {/* Recent Encounters */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Clock className="w-4 h-4 text-indigo-500" />
                  <h3 className="text-sm font-medium text-gray-900">Recent Encounters ({encounters.length})</h3>
                </div>
                {encounters.length === 0 ? (
                  <p className="text-sm text-gray-400">No encounters</p>
                ) : (
                  <div className="space-y-1.5">
                    {encounters.slice(0, 5).map((e) => (
                      <Link
                        key={e.id}
                        href={`/ehr/${patientId}/encounter/${e.id}`}
                        className="flex items-center justify-between p-2 rounded bg-gray-50 hover:bg-gray-100 transition-colors"
                      >
                        <div>
                          <p className="text-sm font-medium text-gray-900">{e.attributes.encounterType}</p>
                          <p className="text-xs text-gray-500">{new Date(e.attributes.startedAt).toLocaleDateString()}</p>
                        </div>
                        <span className={`px-2 py-0.5 text-xs rounded-full ${
                          e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
                            ? "bg-green-100 text-green-700"
                            : "bg-gray-100 text-gray-600"
                        }`}>{e.attributes.status}</span>
                      </Link>
                    ))}
                  </div>
                )}
                <Link href={`/ehr/${patientId}/encounters`} className="mt-2 inline-block text-xs text-blue-600 hover:text-blue-800">
                  View all
                </Link>
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
