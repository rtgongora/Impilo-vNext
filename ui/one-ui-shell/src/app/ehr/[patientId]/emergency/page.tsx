"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, Siren, Pill, User, ShieldAlert } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { usePatient } from "@/hooks/queries/usePatients";
import { usePatientSummary } from "@/hooks/queries/useSummary";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { maskName, displayCpid } from "@/lib/pii-mask";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";

interface GenericResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

export default function EmergencyPatientViewPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const privacyLevel = usePrivacyDisplayStore((s) => s.level);

  const { data: patientData, isLoading: loadingPatient } = usePatient(patientId);
  const { data: patientSummaryRes } = usePatientSummary(patientId);
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
    queryKey: ["prescriptions", { patientId, emergency: true }],
    queryFn: () => apiClient.get(`/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`),
    enabled: !!patientId,
  });

  const patient = patientData?.data;
  const summary = patientSummaryRes?.data;
  const cs = summary?.consentSummary;
  const encounters = encountersData?.data ?? [];
  const activeEncounter = encounters.find(
    (e) => e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS"
  );
  const allergies = allergiesData?.data ?? [];
  const activeAllergies = allergies.filter((a) => a.attributes.status === "ACTIVE");
  const conditions = conditionsData?.data ?? [];
  const activeConditions = conditions.filter(
    (c) => c.attributes.clinical_status === "ACTIVE" || c.attributes.clinicalStatus === "ACTIVE"
  );
  const medications = medsData?.data ?? [];
  const activeMeds = medications.filter(
    (m) => m.attributes.status === "PENDING" || m.attributes.status === "ACTIVE"
  );

  return (
    <EHRLayout>
      <PageShell title="Emergency summary">
        {loadingPatient ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
          </div>
        ) : !patient ? (
          <p className="text-sm text-slate-500">Patient not found.</p>
        ) : (
          <div className="space-y-4 max-w-3xl">
            <div className="flex items-center justify-between gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2">
              <div className="flex items-center gap-2 text-sm font-semibold text-red-900">
                <Siren className="h-4 w-4" aria-hidden />
                High-risk / emergency snapshot
              </div>
              <Link href={`/ehr/${patientId}/summary`} className="text-xs font-medium text-red-800 hover:underline">
                Full summary
              </Link>
            </div>

            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <div className="flex items-start gap-2">
                <User className="h-4 w-4 text-slate-500 mt-0.5" aria-hidden />
                <div>
                  <p className="text-sm font-semibold text-slate-900">
                    {maskName(patient.attributes.displayName, privacyLevel)}
                  </p>
                  <p className="text-xs font-mono text-slate-600">{displayCpid(patient.attributes.cpid)}</p>
                  {patient.attributes.dateOfBirth && (
                    <p className="text-xs text-slate-500">DoB: {String(patient.attributes.dateOfBirth)}</p>
                  )}
                </div>
              </div>
              {activeEncounter && (
                <p className="mt-3 text-sm text-amber-900">
                  <span className="font-medium">Active encounter:</span>{" "}
                  {String(activeEncounter.attributes.encounterType)} — since{" "}
                  {String(activeEncounter.attributes.startedAt ?? "")}
                </p>
              )}
            </div>

            <div className="rounded-lg border border-amber-200 bg-amber-50/50 p-4">
              <h2 className="text-xs font-bold uppercase tracking-wide text-amber-900">Resuscitation &amp; consent</h2>
              <ul className="mt-2 space-y-1 text-sm text-amber-950">
                {(cs?.criticalFlags ?? []).length === 0 && (
                  <li className="text-amber-800/90">No critical consent flags in the current extract.</li>
                )}
                {(cs?.criticalFlags ?? []).map((f) => (
                  <li key={f.id ?? f.code} className="font-medium">
                    {f.label}
                    {f.severity ? ` — ${f.severity}` : ""}
                  </li>
                ))}
                {cs?.capacityStatus && (
                  <li>
                    <span className="font-medium">Capacity:</span> {cs.capacityStatus}
                  </li>
                )}
              </ul>
              <p className="mt-2 text-[10px] text-amber-900/80">
                Emergency override and break-glass are policy-gated in the host shell — not shown here.
              </p>
            </div>

            <div className="rounded-lg border border-red-100 bg-red-50/30 p-4">
              <h2 className="flex items-center gap-1 text-xs font-bold uppercase tracking-wide text-red-900">
                <ShieldAlert className="h-3.5 w-3.5" />
                Allergies
              </h2>
              {activeAllergies.length === 0 ? (
                <p className="mt-1 text-sm text-slate-600">No active allergies on record in this view.</p>
              ) : (
                <ul className="mt-2 space-y-1 text-sm text-slate-900">
                  {activeAllergies.map((a) => (
                    <li key={a.id}>
                      <span className="font-medium">{String(a.attributes.allergen)}</span>
                      {a.attributes.reaction ? ` — ${String(a.attributes.reaction)}` : ""}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-lg border border-slate-200 p-3">
                <h2 className="text-xs font-bold uppercase text-slate-500">Problems (active)</h2>
                <ul className="mt-2 space-y-1 text-sm text-slate-800">
                  {activeConditions.length === 0 && <li className="text-slate-500">None listed.</li>}
                  {activeConditions.slice(0, 8).map((c) => (
                    <li key={c.id}>
                      {String(
                        c.attributes.conditionName ?? c.attributes.condition_name ?? c.attributes.title ?? "Condition"
                      )}
                    </li>
                  ))}
                </ul>
              </div>
              <div className="rounded-lg border border-slate-200 p-3">
                <h2 className="flex items-center gap-1 text-xs font-bold uppercase text-slate-500">
                  <Pill className="h-3.5 w-3.5" />
                  Medications
                </h2>
                <ul className="mt-2 space-y-1 text-sm text-slate-800">
                  {activeMeds.length === 0 && <li className="text-slate-500">None active.</li>}
                  {activeMeds.slice(0, 8).map((m) => (
                    <li key={m.id}>{String(m.attributes.medicationName ?? m.attributes.name ?? m.id)}</li>
                  ))}
                </ul>
              </div>
            </div>

            <div className="rounded-lg border border-slate-200 p-3 text-xs text-slate-600">
              <p className="font-medium text-slate-800">Pregnancy &amp; emergency contacts</p>
              <p className="mt-1">
                When available from PCT longitudinal summary, extend this view to include obstetric status and
                next-of-kin.
              </p>
            </div>

            <p className="text-[10px] text-slate-500">
              <AlertTriangle className="inline h-3 w-3 -translate-y-px" aria-hidden /> Audit: details shown for rapid
              decision support; use governed workflows for override and attestation.
            </p>
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
