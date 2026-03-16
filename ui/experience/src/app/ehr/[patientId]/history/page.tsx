"use client";

/**
 * Medical History — Past conditions, encounters, and procedures.
 * Route: /ehr/[patientId]/history | pageTitle: "Medical History"
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Loader2, Clock, AlertCircle, Activity, FileText } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface GenericResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

export default function MedicalHistoryPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data: encountersData, isLoading: loadingEnc } = useEncounters(patientId);
  const { data: conditionsData, isLoading: loadingCond } = useQuery({
    queryKey: ["conditions", { patientId }],
    queryFn: () => apiClient.get<ApiResponse<GenericResource[]>>(`/internal/v1/conditions?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const { data: immunData, isLoading: loadingImmun } = useQuery({
    queryKey: ["immunizations", { patientId }],
    queryFn: () => apiClient.get<ApiResponse<GenericResource[]>>(`/internal/v1/immunizations?patient_id=${patientId}`),
    enabled: !!patientId,
  });

  const encounters = encountersData?.data ?? [];
  const conditions = conditionsData?.data ?? [];
  const immunizations = immunData?.data ?? [];
  const completedEncounters = encounters.filter((e) => e.attributes.status === "COMPLETED");
  const resolvedConditions = conditions.filter((c) => c.attributes.clinical_status === "RESOLVED" || c.attributes.clinicalStatus === "RESOLVED");

  const isLoading = loadingEnc || loadingCond || loadingImmun;

  return (
    <EHRLayout>
      <PageShell title="Medical History">
        <div className="mb-4">
          <Link href={`/ehr/${patientId}`} className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors">
            <ArrowLeft className="w-4 h-4" /> Back to patient chart
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : (
          <div className="space-y-6">
            {/* Past Encounters */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-center gap-2 mb-4">
                <Clock className="w-5 h-5 text-cyan-500" />
                <h3 className="font-medium text-gray-900">Past Encounters ({completedEncounters.length})</h3>
              </div>
              {completedEncounters.length === 0 ? (
                <p className="text-sm text-gray-400">No completed encounters</p>
              ) : (
                <div className="space-y-2">
                  {completedEncounters.map((enc) => (
                    <Link key={enc.id} href={`/ehr/${patientId}/encounter/${enc.id}`}
                      className="flex items-center justify-between p-3 rounded-lg border border-gray-100 hover:bg-gray-50 transition-colors">
                      <div>
                        <p className="text-sm font-medium text-gray-900">{enc.attributes.encounterType}</p>
                        <p className="text-xs text-gray-500">
                          {new Date(enc.attributes.startedAt).toLocaleDateString()}
                          {enc.attributes.closedAt && ` — ${new Date(enc.attributes.closedAt as string).toLocaleDateString()}`}
                        </p>
                      </div>
                      <span className="px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-600">COMPLETED</span>
                    </Link>
                  ))}
                </div>
              )}
            </div>

            {/* Resolved Conditions */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-center gap-2 mb-4">
                <AlertCircle className="w-5 h-5 text-green-500" />
                <h3 className="font-medium text-gray-900">Resolved Conditions ({resolvedConditions.length})</h3>
              </div>
              {resolvedConditions.length === 0 ? (
                <p className="text-sm text-gray-400">No resolved conditions in history</p>
              ) : (
                <div className="space-y-2">
                  {resolvedConditions.map((c) => (
                    <div key={c.id} className="flex items-center justify-between p-3 rounded-lg border border-gray-100">
                      <div>
                        <p className="text-sm font-medium text-gray-900">{String(c.attributes.condition_name || c.attributes.conditionName)}</p>
                        {c.attributes.icd_code && <p className="text-xs text-gray-500">ICD: {String(c.attributes.icd_code || c.attributes.icdCode)}</p>}
                      </div>
                      <span className="px-2 py-0.5 text-xs rounded-full bg-green-100 text-green-700">Resolved</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Immunization History */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-center gap-2 mb-4">
                <Activity className="w-5 h-5 text-teal-500" />
                <h3 className="font-medium text-gray-900">Immunization History ({immunizations.length})</h3>
              </div>
              {immunizations.length === 0 ? (
                <p className="text-sm text-gray-400">No immunization records</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b bg-gray-50">
                        <th className="text-left px-3 py-2 font-medium text-gray-600">Vaccine</th>
                        <th className="text-left px-3 py-2 font-medium text-gray-600">Dose</th>
                        <th className="text-left px-3 py-2 font-medium text-gray-600">Date</th>
                        <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {immunizations.map((imm) => (
                        <tr key={imm.id}>
                          <td className="px-3 py-2 text-gray-900">{String(imm.attributes.vaccine_name || imm.attributes.vaccineName)}</td>
                          <td className="px-3 py-2 text-gray-600">{String(imm.attributes.dose_number || imm.attributes.doseNumber || "-")}</td>
                          <td className="px-3 py-2 text-gray-600">{imm.attributes.administered_at ? new Date(String(imm.attributes.administered_at || imm.attributes.administeredAt)).toLocaleDateString() : "-"}</td>
                          <td className="px-3 py-2">
                            <span className="px-2 py-0.5 text-xs rounded-full bg-green-100 text-green-700">{String(imm.attributes.status)}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
