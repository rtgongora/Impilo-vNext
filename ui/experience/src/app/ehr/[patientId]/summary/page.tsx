"use client";

/**
 * Patient Summary — Lovable-aligned clinical overview.
 * Route: /ehr/[patientId]/summary | pageTitle: "Summary"
 *
 * Shows encounter status, active conditions, allergies, current
 * medications, recent vitals, and recent encounters in a clinical
 * dashboard layout. Patient demographics are in the PatientBanner.
 */

import { useMemo } from "react";
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
  ArrowRightLeft,
  Video,
  ClipboardCheck,
  Globe2,
  FlaskConical,
} from "lucide-react";
import { PageShell } from "@/components/PageShell";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useReferrals } from "@/hooks/queries/useReferrals";
import { useClinicalNotes } from "@/hooks/queries/useClinicalNotes";
import { useTelemedicineSessions } from "@/hooks/queries/useTelemedicine";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { usePatientSummary } from "@/hooks/queries/useSummary";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import {
  getReferralFacilityName,
  getReferralStageCopy,
  isReferralReceivingHere,
  parseConsultationCoordinationMeta,
} from "@/lib/consult-workflows";
import { useLabOrders } from "@/hooks/queries/useLabOrders";
import { useImagingStudies } from "@/hooks/queries/useImaging";

interface GenericResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

export default function PatientSummaryPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((s) => s.facility);

  const { data: patientData, isLoading: loadingPatient } = usePatient(patientId);
  const { data: encountersData } = useEncounters(patientId);
  const { data: referralsData } = useReferrals(patientId);
  const { data: notesData } = useClinicalNotes(patientId);
  const { data: telemedicineData } = useTelemedicineSessions({ patientId, facilityId: facility?.id });
  const { data: patientSummaryData } = usePatientSummary(patientId);
  const patientSummary = patientSummaryData?.data ?? null;
  const { data: labOrdersBundle } = useLabOrders(patientId);
  const { data: imagingStudiesBundle } = useImagingStudies(patientId);
  const labOrderCount = labOrdersBundle?.data?.length ?? 0;
  const imagingStudyCount = (imagingStudiesBundle as { data?: unknown[] } | undefined)?.data?.length ?? 0;

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
  const referrals = referralsData?.data ?? [];
  const clinicalNotes = notesData?.data ?? [];
  const telemedicineSessions = telemedicineData?.data ?? [];
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
  const coordinationPulse = useMemo(() => {
    const closureReady = referrals.filter((referral) => referral.attributes.status === "RESPONDED").length;
    const receivingHere = referrals.filter((referral) => isReferralReceivingHere(referral, facility)).length;
    const activeTeleconsults = telemedicineSessions.filter(
      (session) => session.attributes.status === "IN_PROGRESS" || session.attributes.status === "SCHEDULED",
    ).length;
    const referralLoopUpdates = clinicalNotes.filter(
      (note) =>
        note.attributes.noteType === "CONSULTATION" &&
        parseConsultationCoordinationMeta(note.attributes.body).hasReferralLoopUpdate,
    ).length;

    return { closureReady, receivingHere, activeTeleconsults, referralLoopUpdates };
  }, [clinicalNotes, facility, referrals, telemedicineSessions]);
  const referralHighlights = useMemo(
    () =>
      [...referrals]
        .sort((left, right) => {
          const leftTime = new Date(
            left.attributes.respondedAt ?? left.attributes.acceptedAt ?? left.attributes.createdAt,
          ).getTime();
          const rightTime = new Date(
            right.attributes.respondedAt ?? right.attributes.acceptedAt ?? right.attributes.createdAt,
          ).getTime();
          return rightTime - leftTime;
        })
        .slice(0, 3),
    [referrals],
  );

  return (
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

            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Orders &amp; results</p>
                  <p className="mt-1 text-sm font-medium text-slate-900">OROS laboratory orders + governed imaging</p>
                  <p className="mt-1 text-xs text-slate-600">
                    Counts come from live BFF queries — nothing is fabricated when services return empty payloads.
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Link
                    href={`/ehr/${patientId}/orders`}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-medium text-slate-800 hover:bg-slate-100"
                  >
                    <FlaskConical className="h-3.5 w-3.5" />
                    Labs ({labOrderCount})
                  </Link>
                  <Link
                    href={`/ehr/${patientId}/imaging`}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-medium text-slate-800 hover:bg-slate-100"
                  >
                    <Activity className="h-3.5 w-3.5" />
                    Imaging ({imagingStudyCount})
                  </Link>
                </div>
              </div>
            </div>

            <div className="rounded-lg border border-indigo-200 bg-indigo-50/90 p-4 text-sm text-indigo-950">
              <div className="flex items-start gap-2">
                <Globe2 className="mt-0.5 h-5 w-5 shrink-0 text-indigo-700" aria-hidden />
                <div>
                  <p className="font-semibold text-indigo-950">International Patient Summary (IPS)</p>
                  <p className="mt-1 text-indigo-900/90">
                    Experience now proxies the Butano IPS bundle through{" "}
                    <code className="rounded bg-white/80 px-1">/internal/v1/summary/ips/{"{cpid}"}</code>, so the summary can
                    link to a real chart-scoped document instead of carrying a placeholder contract note.
                  </p>
                  <p className="mt-2 text-xs text-indigo-800/90">
                    Use the IPS workspace to review the returned FHIR document bundle, resource counts, and raw payload.
                  </p>
                  {patient.attributes.cpid ? (
                    <div className="mt-3 flex flex-wrap items-center gap-3">
                      <p className="text-xs text-indigo-800">
                        Patient CPID: <span className="font-mono">{String(patient.attributes.cpid)}</span>
                      </p>
                      <Link
                        href={`/ehr/${patientId}/ips`}
                        className="inline-flex items-center gap-1.5 rounded-xl bg-indigo-700 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-indigo-800"
                      >
                        <Globe2 className="h-3.5 w-3.5" />
                        Open IPS
                      </Link>
                    </div>
                  ) : (
                    <p className="mt-2 text-xs text-amber-800">
                      IPS stays unavailable until the patient chart exposes a CPID for Butano summary generation.
                    </p>
                  )}
                </div>
              </div>
            </div>

            {patientSummary && (
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Stethoscope className="w-4 h-4 text-impilo-500" />
                  <h3 className="text-sm font-medium text-gray-900">Longitudinal Summary (SHR)</h3>
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
                  <div className="p-2 bg-gray-50 rounded-lg">
                    <div className="text-lg font-semibold text-gray-900">{patientSummary.conditions?.length ?? 0}</div>
                    <div className="text-[10px] text-gray-500">Conditions</div>
                  </div>
                  <div className="p-2 bg-gray-50 rounded-lg">
                    <div className="text-lg font-semibold text-gray-900">{patientSummary.medications?.length ?? 0}</div>
                    <div className="text-[10px] text-gray-500">Medications</div>
                  </div>
                  <div className="p-2 bg-gray-50 rounded-lg">
                    <div className="text-lg font-semibold text-gray-900">{patientSummary.allergies?.length ?? 0}</div>
                    <div className="text-[10px] text-gray-500">Allergies</div>
                  </div>
                  <div className="p-2 bg-gray-50 rounded-lg">
                    <div className="text-lg font-semibold text-gray-900">{patientSummary.immunizations?.length ?? 0}</div>
                    <div className="text-[10px] text-gray-500">Immunizations</div>
                  </div>
                </div>
              </div>
            )}

            <div className="grid gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)]">
              <div className="rounded-3xl border border-slate-200 bg-[linear-gradient(135deg,#f8fbff_0%,#eef6ff_45%,#fffaf5_100%)] p-5 shadow-sm">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="space-y-2">
                    <div className="inline-flex items-center gap-2 rounded-full bg-white/80 px-3 py-1 text-xs font-medium text-slate-600">
                      <ArrowRightLeft className="h-3.5 w-3.5 text-impilo-500" />
                      Coordination snapshot
                    </div>
                    <div>
                      <h3 className="text-lg font-semibold text-slate-900">Consult and teleconsult outcomes stay visible in summary</h3>
                      <p className="mt-1 max-w-2xl text-sm text-slate-600">
                        This summary now carries the live referral loop, receiving context, and returned guidance signals so clinicians can see what needs action before diving deeper into consults or notes.
                      </p>
                    </div>
                    <div className="flex flex-wrap gap-2 pt-1">
                      <Link
                        href={`/ehr/${patientId}/consults?tab=referrals`}
                        className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
                      >
                        <ArrowRightLeft className="h-4 w-4" />
                        Open Consults
                      </Link>
                      <Link
                        href={`/ehr/${patientId}/consults?tab=teleconsults`}
                        className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
                      >
                        <Video className="h-4 w-4" />
                        Teleconsults
                      </Link>
                      <Link
                        href={`/ehr/${patientId}/notes`}
                        className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
                      >
                        <FileText className="h-4 w-4" />
                        Notes Evidence
                      </Link>
                    </div>
                  </div>

                  <div className="grid gap-3 sm:grid-cols-2 xl:w-[28rem]">
                    <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                      <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Loop closures ready</p>
                      <p className="mt-2 text-2xl font-semibold text-purple-700">{coordinationPulse.closureReady}</p>
                      <p className="mt-1 text-xs text-slate-500">Responses back in chart and ready for final closure.</p>
                    </div>
                    <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                      <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Receiving context</p>
                      <p className="mt-2 text-2xl font-semibold text-impilo-600">{coordinationPulse.receivingHere}</p>
                      <p className="mt-1 text-xs text-slate-500">Referrals where this facility is acting on the receiving side.</p>
                    </div>
                    <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                      <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Teleconsult activity</p>
                      <p className="mt-2 text-2xl font-semibold text-emerald-700">{coordinationPulse.activeTeleconsults}</p>
                      <p className="mt-1 text-xs text-slate-500">Scheduled or live virtual consult sessions for this patient.</p>
                    </div>
                    <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                      <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Returned guidance</p>
                      <p className="mt-2 text-2xl font-semibold text-indigo-700">{coordinationPulse.referralLoopUpdates}</p>
                      <p className="mt-1 text-xs text-slate-500">Consultation notes that already include structured loop updates.</p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex items-start gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-700">
                    <ClipboardCheck className="h-5 w-5" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-slate-900">Recent referral movement</p>
                    <p className="mt-1 text-sm text-slate-600">
                      The latest coordination steps stay visible here so the summary can act as a handoff board, not just a chart snapshot.
                    </p>
                  </div>
                </div>

                <div className="mt-4 space-y-3">
                  {referralHighlights.length === 0 ? (
                    <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-6 text-sm text-slate-500">
                      No referrals or teleconsult loop activity yet for this patient.
                    </div>
                  ) : (
                    referralHighlights.map((referral) => {
                      const stage = getReferralStageCopy(
                        referral.attributes.status,
                        isReferralReceivingHere(referral, facility),
                      );

                      return (
                        <div key={referral.id} className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                          <div className="flex flex-wrap items-start justify-between gap-2">
                            <div>
                              <p className="text-sm font-semibold text-slate-900">
                                {referral.attributes.specialty} referral
                              </p>
                              <p className="mt-1 text-sm text-slate-600">{referral.attributes.reason}</p>
                            </div>
                            <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                              stage.tone === "amber"
                                ? "bg-amber-100 text-amber-700"
                                : stage.tone === "blue"
                                  ? "bg-impilo-100 text-impilo-600"
                                  : stage.tone === "purple"
                                    ? "bg-purple-100 text-purple-700"
                                    : stage.tone === "green"
                                      ? "bg-green-100 text-green-700"
                                      : "bg-slate-100 text-slate-600"
                            }`}>
                              {stage.title}
                            </span>
                          </div>
                          <p className="mt-2 text-sm text-slate-600">{stage.detail}</p>
                          <div className="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                            <span className="rounded-full bg-white px-2.5 py-1">{getReferralFacilityName(referral)}</span>
                            <span className="rounded-full bg-white px-2.5 py-1">{referral.attributes.status}</span>
                            <span className="rounded-full bg-white px-2.5 py-1">{referral.attributes.urgency}</span>
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            </div>

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
                <Link href={`/ehr/${patientId}/vitals`} className="mt-2 inline-block text-xs text-impilo-500 hover:text-impilo-700">
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
                <Link href={`/ehr/${patientId}/conditions`} className="mt-2 inline-block text-xs text-impilo-500 hover:text-impilo-700">
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
                <Link href={`/ehr/${patientId}/allergies`} className="mt-2 inline-block text-xs text-impilo-500 hover:text-impilo-700">
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
                <Link href={`/ehr/${patientId}/medications`} className="mt-2 inline-block text-xs text-impilo-500 hover:text-impilo-700">
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
                <Link href={`/ehr/${patientId}/encounters`} className="mt-2 inline-block text-xs text-impilo-500 hover:text-impilo-700">
                  View all
                </Link>
              </div>
            </div>
          </div>
        )}
      </PageShell>
  );
}
