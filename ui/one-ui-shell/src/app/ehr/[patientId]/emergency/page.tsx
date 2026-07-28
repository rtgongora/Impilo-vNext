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
import { BreakGlassRequestPanel } from "@/components/trust/BreakGlassRequestPanel";
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
  const { data: allergiesData, isError: allergiesUnavailable } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["allergies", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/allergies?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const { data: conditionsData, isError: conditionsUnavailable } = useQuery<
    ApiResponse<GenericResource[]>
  >({
    queryKey: ["conditions", { patientId }],
    queryFn: () => apiClient.get(`/internal/v1/conditions?patient_id=${patientId}`),
    enabled: !!patientId,
  });
  const { data: medsData, isError: medsUnavailable } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["prescriptions", { patientId, emergency: true }],
    queryFn: () => apiClient.get(`/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`),
    enabled: !!patientId,
  });

  const patient = patientData?.data;
  const summary = patientSummaryRes?.data;
  const cs = summary?.consentSummary;
  const encounters = encountersData?.data ?? [];
  const activeEncounter = encounters.find(
    (e) => e.attributes.isOpen
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
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : !patient ? (
          <p className="text-sm text-muted-foreground">Patient not found.</p>
        ) : (
          <div className="space-y-4 max-w-3xl">
            <div className="flex items-center justify-between gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-2">
              <div className="flex items-center gap-2 text-sm font-semibold text-red-900">
                <Siren className="h-4 w-4" aria-hidden />
                High-risk / emergency snapshot
              </div>
              <Link href={`/ehr/${patientId}/summary`} className="text-xs font-medium text-red-800 hover:underline">
                Full summary
              </Link>
            </div>

            <div className="rounded-lg border border-border bg-card p-4">
              <div className="flex items-start gap-2">
                <User className="h-4 w-4 text-muted-foreground mt-0.5" aria-hidden />
                <div>
                  <p className="text-sm font-semibold text-foreground">
                    {maskName(patient.attributes.displayName, privacyLevel)}
                  </p>
                  <p className="text-xs font-mono text-muted-foreground">{displayCpid(patient.attributes.cpid)}</p>
                  {patient.attributes.dateOfBirth && (
                    <p className="text-xs text-muted-foreground">DoB: {String(patient.attributes.dateOfBirth)}</p>
                  )}
                </div>
              </div>
              {activeEncounter && (
                <p className="mt-3 text-sm text-warning-foreground">
                  <span className="font-medium">Active encounter:</span>{" "}
                  {String(activeEncounter.attributes.encounterType)} — since{" "}
                  {String(activeEncounter.attributes.startedAt ?? "")}
                </p>
              )}
            </div>

            <div className="rounded-lg border border-warning/35 bg-warning-soft/50 p-4">
              <h2 className="text-xs font-bold uppercase tracking-wide text-warning-foreground">Resuscitation &amp; consent</h2>
              <ul className="mt-2 space-y-1 text-sm text-warning-foreground">
                {(cs?.criticalFlags ?? []).length === 0 && (
                  <li className="text-warning-foreground/90">No critical consent flags in the current extract.</li>
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
              <BreakGlassRequestPanel patientId={patientId} resourceType="EMERGENCY_PATIENT_RECORD" />
            </div>

            <div className="rounded-lg border border-red-100 bg-danger-soft/30 p-4">
              <h2 className="flex items-center gap-1 text-xs font-bold uppercase tracking-wide text-red-900">
                <ShieldAlert className="h-3.5 w-3.5" />
                Allergies
              </h2>
              {allergiesUnavailable ? (
                /* On an emergency view, "no allergies on record" is the claim most likely to be
                   acted on immediately. Never say it on the strength of a failed read. */
                <p className="mt-1 text-sm font-medium text-red-700">
                  Allergy record unavailable — not a statement that there are none. Check another
                  source before treating.
                </p>
              ) : activeAllergies.length === 0 ? (
                <p className="mt-1 text-sm text-muted-foreground">No active allergies on record in this view.</p>
              ) : (
                <ul className="mt-2 space-y-1 text-sm text-foreground">
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
              <div className="rounded-lg border border-border p-3">
                <h2 className="text-xs font-bold uppercase text-muted-foreground">Problems (active)</h2>
                <ul className="mt-2 space-y-1 text-sm text-foreground">
                  {conditionsUnavailable ? (
                    <li className="text-warning">
                      Problem list unavailable — not a statement that there are none. Check another
                      source before treating.
                    </li>
                  ) : (
                    activeConditions.length === 0 && (
                      <li className="text-muted-foreground">None listed.</li>
                    )
                  )}
                  {activeConditions.slice(0, 8).map((c) => (
                    <li key={c.id}>
                      {String(
                        c.attributes.conditionName ?? c.attributes.condition_name ?? c.attributes.title ?? "Condition"
                      )}
                    </li>
                  ))}
                </ul>
              </div>
              <div className="rounded-lg border border-border p-3">
                <h2 className="flex items-center gap-1 text-xs font-bold uppercase text-muted-foreground">
                  <Pill className="h-3.5 w-3.5" />
                  Medications
                </h2>
                <ul className="mt-2 space-y-1 text-sm text-foreground">
                  {medsUnavailable ? (
                    /* Same rule as allergies one panel up: on an emergency view the medication
                       list drives immediate prescribing decisions, so "none active" must never be
                       said on the strength of a failed read. These two facts sat side by side with
                       opposite honesty until now. */
                    <li className="font-medium text-red-700">
                      Medication record unavailable — not a statement that there are none. Check
                      another source before prescribing.
                    </li>
                  ) : (
                    activeMeds.length === 0 && <li className="text-muted-foreground">None active.</li>
                  )}
                  {activeMeds.slice(0, 8).map((m) => (
                    <li key={m.id}>{String(m.attributes.medicationName ?? m.attributes.name ?? m.id)}</li>
                  ))}
                </ul>
              </div>
            </div>

            <div className="rounded-lg border border-border p-3 text-xs text-muted-foreground">
              <p className="font-medium text-foreground">Pregnancy &amp; emergency contacts</p>
              <p className="mt-1">
                When available from PCT longitudinal summary, extend this view to include obstetric status and
                next-of-kin.
              </p>
            </div>

            <p className="text-[10px] text-muted-foreground">
              <AlertTriangle className="inline h-3 w-3 -translate-y-px" aria-hidden /> Audit: details shown for rapid
              decision support; use governed workflows for override and attestation.
            </p>
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
