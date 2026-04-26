"use client";

/**
 * Encounter — Active encounter page with vitals capture, notes, orders, referrals, and close.
 * Route: /ehr/[patientId]/encounter/[encounterId] | pageTitle: "Encounter"
 */

import { useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { RoleSpecificEncounterForm } from "@/components/encounter/StructuredEncounterForms";
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
  ArrowLeft,
  Save,
  Stethoscope,
  Receipt,
} from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { PageShell } from "@/components/PageShell";
import { ClinicalAlerts } from "@/components/ClinicalAlerts";
import { PatientJourneyContextPanel } from "@/components/clinical/PatientJourneyContextPanel";
import { EncounterVitalsGuidance } from "@/components/clinical/EncounterVitalsGuidance";
import {
  ActiveDataEntryLayout,
  ANTENATAL_CONTACT_1_FORM,
  clinicalFormPatientContextFromPatient,
  DakFormRenderer,
  type ClinicalFormRuntimeContext,
} from "@/lib/clinical-forms";
import { usePatient } from "@/hooks/queries/usePatients";
import { useClinicalAlerts } from "@/hooks/useClinicalAlerts";
import { useEncounter, useCloseEncounter } from "@/hooks/queries/useEncounters";
import { useReferrals, type ReferralResource } from "@/hooks/queries/useReferrals";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function EncounterPage() {
  const params = useParams<{ patientId: string; encounterId: string }>();
  const router = useRouter();
  const { patientId, encounterId } = params;
  const { user } = useAuthStore();
  const { isClinical, isPrescriber, isDispenser } = useRoleGroup();
  const facility = useFacilityStore((state) => state.facility);

  // Determine role-specific form variant — check most specific role first
  const roles = useMemo(() => user?.roles ?? [], [user?.roles]);
  const activeRole =
    roles.includes("PHYSIOTHERAPIST") ? "PHYSIOTHERAPIST"
    : roles.includes("OCCUPATIONAL_THERAPIST") ? "OCCUPATIONAL_THERAPIST"
    : roles.includes("PSYCHOLOGIST") || roles.includes("COUNSELLOR") ? "PSYCHOLOGIST"
    : roles.includes("NUTRITIONIST") || roles.includes("DIETITIAN") ? "NUTRITIONIST"
    : roles.includes("SOCIAL_WORKER") ? "SOCIAL_WORKER"
    : roles.includes("SPEECH_THERAPIST") || roles.includes("SLT") ? "SPEECH_THERAPIST"
    : roles.includes("RADIOGRAPHER") || roles.includes("IMAGING_TECH") ? "RADIOGRAPHER"
    : roles.includes("LAB_TECHNOLOGIST") || roles.includes("LAB_TECH") ? "LAB_TECHNOLOGIST"
    : roles.includes("OPTOMETRIST") ? "OPTOMETRIST"
    : roles.includes("DENTIST") || roles.includes("ORAL_HYGIENIST") ? "DENTIST"
    : roles.includes("AUDIOLOGIST") ? "AUDIOLOGIST"
    : roles.includes("PODIATRIST") ? "PODIATRIST"
    : roles.includes("RESPIRATORY_THERAPIST") ? "RESPIRATORY_THERAPIST"
    : roles.includes("RADIOTHERAPIST") || roles.includes("RADIATION_THERAPIST") ? "RADIOTHERAPIST"
    : roles.includes("EMT") || roles.includes("PARAMEDIC") ? "EMT"
    : roles.includes("CHW") || roles.includes("COMMUNITY_HEALTH_WORKER") ? "CHW"
    : roles.includes("EHO") || roles.includes("ENVIRONMENTAL_HEALTH") ? "EHO"
    : roles.includes("MIDWIFE") ? "MIDWIFE"
    : isDispenser ? "PHARMACIST"
    : roles.includes("NURSE") ? "NURSE"
    : "CLINICIAN";

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

  // Fetch patient clinical data for CDS alerts
  const { data: allergiesData } = useQuery<{ data: Array<{ attributes: Record<string, unknown> }> }>({
    queryKey: ["cds-allergies", patientId], queryFn: () => apiClient.get(`/internal/v1/allergies?patient_id=${patientId}`), enabled: !!patientId });
  const { data: conditionsData } = useQuery<{ data: Array<{ attributes: Record<string, unknown> }> }>({
    queryKey: ["cds-conditions", patientId], queryFn: () => apiClient.get(`/internal/v1/conditions?patient_id=${patientId}`), enabled: !!patientId });
  const { data: medsData } = useQuery<{ data: Array<{ attributes: Record<string, unknown> }> }>({
    queryKey: ["cds-meds", patientId], queryFn: () => apiClient.get(`/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`), enabled: !!patientId });
  const { data: vitalsData } = useQuery<{ data: Array<{ attributes: Record<string, unknown> }> }>({
    queryKey: ["cds-vitals", patientId], queryFn: () => apiClient.get(`/internal/v1/vitals?patient_id=${patientId}&size=1`), enabled: !!patientId });

  const latestVitals = (vitalsData?.data ?? [])[0]?.attributes;
  const clinicalAlerts = useClinicalAlerts({
    allergies: (allergiesData?.data ?? []).map((a) => a.attributes as { allergen?: string; severity?: string; status?: string }),
    conditions: (conditionsData?.data ?? []).map((c) => c.attributes as { condition_name?: string; icd_code?: string; clinical_status?: string; severity?: string }),
    medications: (medsData?.data ?? []).map((m) => m.attributes as { medication_name?: string; status?: string }),
    vitals: latestVitals ? {
      systolic: latestVitals.systolic as number | undefined,
      diastolic: latestVitals.diastolic as number | undefined,
      heart_rate: latestVitals.heart_rate as number | undefined,
      temperature: latestVitals.temperature as number | undefined,
      oxygen_saturation: latestVitals.oxygen_saturation as number | undefined,
      respiratory_rate: latestVitals.respiratory_rate as number | undefined,
    } : undefined,
  });

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
  /** WHO DAK–aligned structured ANC form takes primary column; journey moves to side rail. */
  const [structuredFormFocus, setStructuredFormFocus] = useState(false);

  const dakRuntime: ClinicalFormRuntimeContext | null = useMemo(() => {
    if (!encounter || !patientData?.data) return null;
    const et = String(encounter.attributes.encounterType ?? "").toUpperCase();
    const anc =
      et.includes("ANC") ||
      et.includes("ANTENATAL") ||
      et.includes("MATERNITY") ||
      et.includes("OBSTETRIC");
    const g = (patientData.data.attributes.gender ?? "").toUpperCase();
    const pregnant = anc && (g === "FEMALE" || g === "F") ? true : null;
    const patientCtx = clinicalFormPatientContextFromPatient(patientData.data, {
      programmes: anc ? ["ANC"] : [],
      pregnant,
    });
    return {
      patient: patientCtx,
      encounterType: String(encounter.attributes.encounterType ?? "UNKNOWN"),
      providerRoles: roles,
    };
  }, [encounter, patientData, roles]);

  // Examination findings state (Lovable-aligned system-by-system capture)
  const [examGeneral, setExamGeneral] = useState("");
  const [examCVS, setExamCVS] = useState("");
  const [examResp, setExamResp] = useState("");
  const [examAbdo, setExamAbdo] = useState("");
  const [examNeuro, setExamNeuro] = useState("");
  const [examSaving, setExamSaving] = useState(false);
  const [examSaved, setExamSaved] = useState(false);

  // Triage state (Lovable-aligned acuity + danger signs + quick vitals)
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
  // Quick triage vitals
  const [tvSystolic, setTvSystolic] = useState("");
  const [tvDiastolic, setTvDiastolic] = useState("");
  const [tvHR, setTvHR] = useState("");
  const [tvTemp, setTvTemp] = useState("");
  const [tvSpO2, setTvSpO2] = useState("");
  const [tvRR, setTvRR] = useState("");

  const isActive =
    encounter?.attributes.status === "ACTIVE" ||
    encounter?.attributes.status === "IN_PROGRESS";
  const closureStep = !existingTriage
    ? "Complete triage"
    : respondedReferrals.length > 0
      ? "Review referral responses"
      : isActive
        ? "Document and disposition"
        : "Encounter closed";

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
      const toNum = (v: string) => (v.trim() === "" ? null : Number(v));
      const vitals: Record<string, number | null> = {
        systolic: toNum(tvSystolic),
        diastolic: toNum(tvDiastolic),
        heart_rate: toNum(tvHR),
        temperature: toNum(tvTemp),
        oxygen_saturation: toNum(tvSpO2),
        respiratory_rate: toNum(tvRR),
      };
      const hasVitals = Object.values(vitals).some((v) => v != null);
      await apiClient.post("/internal/v1/triage", {
        patient_id: patientId,
        encounter_id: encounterId,
        acuity: triageAcuity,
        chief_complaint: (encounter?.attributes as Record<string, unknown>)?.chief_complaint ?? null,
        danger_signs: activeDangerSigns,
        vitals: hasVitals ? vitals : null,
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
            {/* Clinical Decision Support Alerts */}
            {!structuredFormFocus && <ClinicalAlerts alerts={clinicalAlerts} />}

            {!structuredFormFocus && <PatientJourneyContextPanel patientId={patientId} variant="compact" />}

            <ClinicalReviewHeader
              badge="Encounter closure"
              badgeIcon={Stethoscope}
              title="Keep the live encounter oriented around the next clinical step, linked referral outcomes, and the eventual closure move instead of leaving those signals scattered across the workspace."
              description="The encounter detail page now makes triage state, referral-response follow-through, and discharge readiness visible above the working forms."
              facilityName={facility?.name}
              encounterLabel={`${encounter.attributes.encounterType} since ${new Date(encounter.attributes.startedAt).toLocaleString()}`}
              actions={[
                { href: `/ehr/${patientId}/orders`, label: "Orders", icon: ClipboardList },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
                { href: `/pharmacy/prescriptions?patientId=${patientId}&encounterId=${encounterId}&source=encounter`, label: "Pharmacy", icon: Pill, tone: "secondary" },
                { href: `/ehr/${patientId}/consults`, label: "Consults", icon: ArrowUpRight, tone: "secondary" },
                { href: `/ehr/${patientId}/discharge?encounterId=${encounterId}`, label: "Visit Outcome", icon: XCircle, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Triage",
                  value: existingTriage ? `P${String(existingTriage.attributes.acuity)}` : "Pending",
                  detail: existingTriage
                    ? "Encounter already has a triage record in scope."
                    : "Complete triage first if acuity or danger signs are still missing.",
                },
                {
                  label: "Referral responses",
                  value: String(respondedReferrals.length),
                  detail:
                    respondedReferrals.length > 0
                      ? "Specialist responses are back and should be reviewed before closure."
                      : "No linked referral responses are waiting on this encounter.",
                },
                {
                  label: "Closure step",
                  value: closureStep,
                  detail: isActive
                    ? "Continue working in place here, then hand off to Visit Outcome when the encounter is ready to close."
                    : "This encounter is already closed; use the chart for downstream review.",
                },
              ]}
            />

            {isClinical && isActive && dakRuntime && (
              <div className="rounded-lg border border-impilo-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="text-xs font-semibold text-gray-900">Structured clinical forms (WHO DAK pattern)</p>
                    <p className="text-[11px] text-gray-500">
                      Antenatal first-contact exemplar — coded fields, decision-support hooks, indicator & FHIR mapping utilities.
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setStructuredFormFocus((v) => !v)}
                    className="shrink-0 rounded-lg bg-impilo-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-impilo-700"
                  >
                    {structuredFormFocus ? "Exit focused entry" : "Focus ANC structured form"}
                  </button>
                </div>
                <div className="mt-3">
                  {structuredFormFocus ? (
                    <ActiveDataEntryLayout
                      active
                      onRequestExit={() => setStructuredFormFocus(false)}
                      contextRail={
                        <div className="space-y-2">
                          <p className="text-[10px] font-semibold uppercase text-gray-500">Patient context</p>
                          <PatientJourneyContextPanel patientId={patientId} variant="compact" />
                        </div>
                      }
                      alerts={<ClinicalAlerts alerts={clinicalAlerts} />}
                      primary={
                        <DakFormRenderer
                          form={ANTENATAL_CONTACT_1_FORM}
                          runtime={dakRuntime}
                          patientId={patientId}
                          encounterId={encounterId}
                        />
                      }
                    />
                  ) : (
                    <DakFormRenderer
                      form={ANTENATAL_CONTACT_1_FORM}
                      runtime={dakRuntime}
                      patientId={patientId}
                      encounterId={encounterId}
                    />
                  )}
                </div>
              </div>
            )}

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                Encounter loop status
              </p>
              <p className="mt-2 text-sm text-slate-800">
                {!existingTriage
                  ? "The encounter is open, but triage is still the first closure dependency before the rest of the clinical work can be considered complete."
                  : respondedReferrals.length > 0
                    ? "Referral responses are attached to this encounter, so they should be reviewed before final outcome selection."
                    : isActive
                      ? "This encounter is ready for ongoing documentation, orders, and a final visit outcome from the same workspace."
                      : "The encounter is closed; this page now serves as the longitudinal record of what happened in this clinical episode."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Orders for outstanding investigations or prescriptions, Notes for narrative closure, Consults for referral follow-through, and Visit Outcome when the encounter is ready to close.
              </p>
            </div>

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
                  {encounter.attributes.costa_bill_id && (
                    <Link
                      href={`/finance/billing/${encounter.attributes.costa_bill_id}?patientId=${patientId}&encounterId=${encounterId}&source=encounter`}
                      className="inline-flex items-center gap-1 px-2.5 py-0.5 text-xs font-medium rounded-full bg-emerald-50 text-emerald-700 hover:bg-emerald-100 transition-colors"
                    >
                      <Receipt className="w-3 h-3" />
                      Bill
                    </Link>
                  )}
                </div>
                {/* Quick action links — visible only to clinical staff */}
                {isActive && isClinical && (
                  <div className="flex gap-2">
                    <Link
                      href={`/ehr/${patientId}/orders`}
                      className="px-3 py-1.5 bg-purple-50 text-purple-700 text-xs font-medium rounded-lg hover:bg-purple-100 transition-colors flex items-center gap-1"
                    >
                      <ClipboardList className="w-3 h-3" /> Orders
                    </Link>
                    {isPrescriber && (
                    <Link
                      href={`/ehr/${patientId}/medications`}
                      className="px-3 py-1.5 bg-green-50 text-green-700 text-xs font-medium rounded-lg hover:bg-green-100 transition-colors flex items-center gap-1"
                    >
                      <Pill className="w-3 h-3" /> Rx
                    </Link>
                    )}
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

            {/* Existing Triage Display — show when triage already recorded */}
            {existingTriage && (
              <div className={`rounded-lg border-2 p-4 ${
                existingTriage.attributes.acuity === 1 ? "border-red-400 bg-red-50" :
                existingTriage.attributes.acuity === 2 ? "border-orange-400 bg-orange-50" :
                existingTriage.attributes.acuity === 3 ? "border-yellow-400 bg-yellow-50" :
                existingTriage.attributes.acuity === 4 ? "border-green-400 bg-green-50" :
                "border-impilo-400 bg-impilo-50"
              }`}>
                <div className="flex items-center justify-between">
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
                      {typeof existingTriage.attributes.triaged_by_name === "string" && (
                        <span className="text-xs text-gray-500 ml-2">
                          by {String(existingTriage.attributes.triaged_by_name)}
                        </span>
                      )}
                    </div>
                  </div>
                  {/* Triage vitals summary if available */}
                  {typeof existingTriage.attributes.vitals === "object" && existingTriage.attributes.vitals !== null && (
                    <div className="flex items-center gap-3 text-xs">
                      {(existingTriage.attributes.vitals as Record<string, unknown>).systolic != null && (
                        <span>BP: {String((existingTriage.attributes.vitals as Record<string, unknown>).systolic)}/{String((existingTriage.attributes.vitals as Record<string, unknown>).diastolic ?? "")}</span>
                      )}
                      {(existingTriage.attributes.vitals as Record<string, unknown>).heart_rate != null && (
                        <span>HR: {String((existingTriage.attributes.vitals as Record<string, unknown>).heart_rate)}</span>
                      )}
                      {(existingTriage.attributes.vitals as Record<string, unknown>).oxygen_saturation != null && (
                        <span>SpO₂: {String((existingTriage.attributes.vitals as Record<string, unknown>).oxygen_saturation)}%</span>
                      )}
                      {(existingTriage.attributes.vitals as Record<string, unknown>).temperature != null && (
                        <span>T: {String((existingTriage.attributes.vitals as Record<string, unknown>).temperature)}°C</span>
                      )}
                    </div>
                  )}
                </div>
                {/* Danger signs summary */}
                {Array.isArray(existingTriage.attributes.danger_signs) && (
                  (() => {
                    const present = (existingTriage.attributes.danger_signs as Array<{ name: string; present: boolean }>).filter((d) => d.present);
                    return present.length > 0 ? (
                      <div className="mt-2 flex flex-wrap gap-1">
                        {present.map((d) => (
                          <span key={d.name} className="px-2 py-0.5 text-xs bg-red-100 text-red-700 rounded-full">{d.name}</span>
                        ))}
                      </div>
                    ) : null;
                  })()
                )}
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
                      { level: 5, label: "Blue", desc: "Non-urgent", color: "border-impilo-400 bg-impilo-50 text-impilo-600", active: "border-impilo-400 bg-blue-100 ring-2 ring-impilo-300" },
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

                {/* Quick Triage Vitals */}
                <div className="mb-4">
                  <label className="block text-xs font-medium text-gray-600 mb-2">Triage Vitals</label>
                  <div className="grid grid-cols-3 gap-2">
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">Systolic</label>
                      <input type="number" value={tvSystolic} onChange={(e) => setTvSystolic(e.target.value)} placeholder="mmHg"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">Diastolic</label>
                      <input type="number" value={tvDiastolic} onChange={(e) => setTvDiastolic(e.target.value)} placeholder="mmHg"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">Heart Rate</label>
                      <input type="number" value={tvHR} onChange={(e) => setTvHR(e.target.value)} placeholder="bpm"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">Temperature</label>
                      <input type="number" step="0.1" value={tvTemp} onChange={(e) => setTvTemp(e.target.value)} placeholder="°C"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">SpO₂</label>
                      <input type="number" value={tvSpO2} onChange={(e) => setTvSpO2(e.target.value)} placeholder="%"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
                    <div>
                      <label className="block text-[10px] text-gray-500 mb-0.5">Resp Rate</label>
                      <input type="number" value={tvRR} onChange={(e) => setTvRR(e.target.value)} placeholder="/min"
                        className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-amber-500" />
                    </div>
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
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Diastolic (mmHg)</label>
                    <input type="number" value={diastolic} onChange={(e) => setDiastolic(e.target.value)} placeholder="80" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Heart Rate (bpm)</label>
                    <input type="number" value={heartRate} onChange={(e) => setHeartRate(e.target.value)} placeholder="72" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Temperature (°C)</label>
                    <input type="number" step="0.1" value={temperature} onChange={(e) => setTemperature(e.target.value)} placeholder="36.5" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Resp. Rate (/min)</label>
                    <input type="number" value={respiratoryRate} onChange={(e) => setRespiratoryRate(e.target.value)} placeholder="16" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">SpO2 (%)</label>
                    <input type="number" value={oxygenSat} onChange={(e) => setOxygenSat(e.target.value)} placeholder="98" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Weight (kg)</label>
                    <input type="number" step="0.1" value={weight} onChange={(e) => setWeight(e.target.value)} placeholder="70" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Height (cm)</label>
                    <input type="number" step="0.1" value={height} onChange={(e) => setHeight(e.target.value)} placeholder="170" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                  <div className="col-span-2">
                    <label className="block text-xs font-medium text-gray-600 mb-1">Pain Score (0-10)</label>
                    <input type="number" min="0" max="10" value={painScore} onChange={(e) => setPainScore(e.target.value)} placeholder="0" disabled={!isActive}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50 disabled:text-gray-400" />
                  </div>
                </div>
                {vitalsError && <p className="mt-2 text-xs text-red-600">{vitalsError}</p>}
                {patientData?.data && (
                  <EncounterVitalsGuidance
                    ageBand={clinicalFormPatientContextFromPatient(patientData.data).ageBand}
                    systolic={systolic}
                    diastolic={diastolic}
                    heartRate={heartRate}
                    temperature={temperature}
                    respiratoryRate={respiratoryRate}
                    oxygenSat={oxygenSat}
                    painScore={painScore}
                  />
                )}
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
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-50">
                      <option value="PROGRESS">Progress Note</option>
                      <option value="ASSESSMENT">Assessment</option>
                      <option value="DISCHARGE">Discharge Summary</option>
                      <option value="CONSULTATION">Consultation</option>
                    </select>
                  </div>
                  {/* Role-specific structured form */}
                  <RoleSpecificEncounterForm
                    role={activeRole}
                    onDataChange={(data) => {
                      // Merge structured data into note body for persistence
                      setNoteBody(JSON.stringify(data));
                    }}
                  />
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
                                ref.attributes.status === "ACCEPTED" ? "bg-impilo-100 text-impilo-600" :
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
                            className="text-xs text-impilo-500 hover:text-impilo-700"
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

            {/* Close Encounter — clinical staff only */}
            {isActive && isClinical && (
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
  );
}
