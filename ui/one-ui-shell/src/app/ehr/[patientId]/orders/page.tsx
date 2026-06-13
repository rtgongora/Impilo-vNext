"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import {
  Activity,
  Camera,
  AlertTriangle,
  CheckCircle2,
  ClipboardList,
  FileText,
  Layers,
  Loader2,
  Microscope,
  Pill,
  Plus,
  Radio,
  Stethoscope,
  Save,
  TestTube2,
  Droplet,
  type LucideIcon,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCancelLabOrder,
  useCollectLabOrder,
  useCreateLabOrder,
  useLabOrders,
  type LabOrderResource,
} from "@/hooks/queries/useLabOrders";
import { useClinicalWorklist } from "@/hooks/queries/useClinicalWorklist";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { usePatient } from "@/hooks/queries/usePatients";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useProductRegistrySearch } from "@/hooks/queries/useProductRegistry";
import { apiClient } from "@/lib/api-client";
import {
  CLINICAL_ORDER_DRAFT_EVENT,
  type ClinicalOrderDraftDetail,
} from "@/lib/clinical-guidance-events";

const STATUS_BADGE: Record<string, string> = {
  ORDERED: "bg-primary-soft text-primary",
  COLLECTED: "bg-yellow-100 text-yellow-700",
  RESULTED: "bg-green-100 text-green-700",
  REVIEWED: "bg-purple-100 text-warning-foreground",
  CANCELLED: "bg-neutral-100 text-muted-foreground",
};

const PRIORITY_BADGE: Record<string, string> = {
  STAT: "bg-red-100 text-danger",
  URGENT: "bg-orange-100 text-orange-700",
  ROUTINE: "bg-primary-soft text-primary",
};

const EMPTY_FORM = {
  test_name: "",
  test_code: "",
  category: "LABORATORY",
  priority: "ROUTINE",
  clinical_notes: "",
  ordered_by: "",
  ordered_by_name: "",
  facility_id: "",
};

type GuidedLane =
  | "LAB"
  | "MEDICATION"
  | "REFERRAL"
  | "TELEMEDICINE"
  | "IMAGING"
  | "PROCEDURE"
  | "ORDER_SET";

const GUIDED_LANES: Array<{ key: GuidedLane; label: string; icon: LucideIcon }> = [
  { key: "LAB", label: "Lab", icon: Microscope },
  { key: "MEDICATION", label: "Medication", icon: Pill },
  { key: "REFERRAL", label: "Referral", icon: Radio },
  { key: "TELEMEDICINE", label: "Teleconsult", icon: Camera },
  { key: "IMAGING", label: "Imaging", icon: TestTube2 },
  { key: "PROCEDURE", label: "Procedure", icon: Stethoscope },
  { key: "ORDER_SET", label: "Order Set", icon: Layers },
];

type ProductRegistryCandidate = {
  id: string;
  name: string;
  code: string;
  kind?: string;
};

function validateGuidedForm(
  lane: GuidedLane,
  form: {
    lab_name: string;
    lab_code: string;
    imaging_name: string;
    medication_name: string;
    dosage: string;
    referral_specialty: string;
    referral_reason: string;
  }
): string | null {
  if (lane === "LAB") {
    if (!form.lab_name.trim() || !form.lab_code.trim()) {
      return "Lab lane requires test name and code.";
    }
  }
  if (lane === "IMAGING") {
    if (!form.imaging_name.trim()) {
      return "Imaging lane requires a study or procedure name.";
    }
  }
  if (lane === "MEDICATION") {
    if (!form.medication_name.trim() || !form.dosage.trim()) {
      return "Medication lane requires medication name and dosage.";
    }
  }
  if (lane === "REFERRAL") {
    if (!form.referral_specialty.trim() || !form.referral_reason.trim()) {
      return "Referral lane requires specialty and reason.";
    }
  }
  return null;
}

function extractProductCandidates(payload: unknown): ProductRegistryCandidate[] {
  const root = payload as Record<string, unknown> | undefined;
  const list =
    (Array.isArray(root) && root) ||
    (Array.isArray(root?.items) && root.items) ||
    (Array.isArray(root?.data) && root.data) ||
    (Array.isArray(root?.results) && root.results) ||
    [];

  return list
    .map((raw, i: number) => {
      const row = raw as Record<string, unknown> | undefined;
      const id = String(row?.id ?? row?.itemId ?? row?.sku ?? row?.code ?? `item-${i}`);
      const name = String(row?.name ?? row?.label ?? row?.title ?? row?.displayName ?? row?.description ?? "");
      const code = String(row?.code ?? row?.productCode ?? row?.sku ?? row?.itemCode ?? "");
      const kind = row?.kind != null ? String(row.kind) : undefined;
      return { id, name, code, kind } satisfies ProductRegistryCandidate;
    })
    .filter((c: ProductRegistryCandidate) => c.name.trim() !== "" || c.code.trim() !== "");
}

export default function OrdersPage() {
  const params = useParams<{ patientId: string }>();
  const searchParams = useSearchParams();
  const { patientId } = params;
  const encounterIdFromUrl = searchParams.get("encounterId") ?? "";
  const laneFromUrl = searchParams.get("lane") ?? "";
  const { user } = useAuthStore();
  const { isClinical } = useRoleGroup();
  const facility = useFacilityStore((state) => state.facility);
  const { data: patientData } = usePatient(patientId);
  const patientCpid = String(patientData?.data?.attributes?.cpid ?? "");
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter =
    (encounterIdFromUrl
      ? (encountersData?.data ?? []).find((encounter) => encounter.id === encounterIdFromUrl)
      : undefined) ??
    (encountersData?.data ?? []).find(
      (encounter) =>
        encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE",
    );

  const queryClient = useQueryClient();
  const { data: ordersData, isLoading } = useLabOrders(patientId);
  const createOrder = useCreateLabOrder();
  const collectOrder = useCollectLabOrder();
  const cancelOrder = useCancelLabOrder();

  const [resultingOrder, setResultingOrder] = useState<LabOrderResource | null>(null);
  const [resultValues, setResultValues] = useState([
    { name: "", value: "", unit: "", referenceRange: "", interpretation: "NORMAL" },
  ]);
  const [resultNotes, setResultNotes] = useState("");
  const [resultSubmitting, setResultSubmitting] = useState(false);

  const orders = ordersData?.data ?? [];
  const orderedCount = orders.filter((order) => order.attributes.status === "ORDERED").length;
  const collectedCount = orders.filter((order) => order.attributes.status === "COLLECTED").length;
  const awaitingReviewCount = orders.filter((order) => order.attributes.status === "RESULTED").length;
  const reviewedCount = orders.filter((order) => order.attributes.status === "REVIEWED").length;

  const buildFormState = () => ({
    ...EMPTY_FORM,
    ordered_by: user?.id ?? "",
    ordered_by_name: user?.displayName ?? user?.email ?? "",
    facility_id: facility?.id ?? "",
  });

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(buildFormState);
  const [guidedLane, setGuidedLane] = useState<GuidedLane>("LAB");
  const [guidedSubmitting, setGuidedSubmitting] = useState(false);
  const [guidedError, setGuidedError] = useState<string | null>(null);
  const [guidedSuccess, setGuidedSuccess] = useState<string | null>(null);
  const [guidedForm, setGuidedForm] = useState({
    lab_name: "",
    lab_code: "",
    lab_priority: "ROUTINE",
    imaging_name: "",
    imaging_modality: "XRAY",
    imaging_priority: "ROUTINE",
    imaging_notes: "",
    medication_name: "",
    dosage: "",
    frequency: "Twice daily (BD)",
    duration: "",
    referral_specialty: "",
    referral_reason: "",
    referral_urgency: "ROUTINE",
    tele_mode: "VIDEO",
    tele_notes: "",
    tele_when: "",
  });

  useEffect(() => {
    if (!laneFromUrl) return;
    const normalized = laneFromUrl.toUpperCase();
    if (GUIDED_LANES.some((lane) => lane.key === normalized)) {
      setGuidedLane(normalized as GuidedLane);
    }
  }, [laneFromUrl]);
  const [catalogQuery, setCatalogQuery] = useState("");
  const [guidanceDraft, setGuidanceDraft] = useState<ClinicalOrderDraftDetail | null>(null);

  useEffect(() => {
    function onDraft(e: Event) {
      const ce = e as CustomEvent<ClinicalOrderDraftDetail>;
      const d = ce.detail;
      if (!d?.patientId || d.patientId !== patientId) return;
      setGuidanceDraft(d);
    }
    window.addEventListener(CLINICAL_ORDER_DRAFT_EVENT, onDraft);
    return () => window.removeEventListener(CLINICAL_ORDER_DRAFT_EVENT, onDraft);
  }, [patientId]);

  const productSearch = useProductRegistrySearch({
    q: catalogQuery.trim() || undefined,
    size: 8,
    page: 0,
    kind: form.category === "LABORATORY" ? "service" : undefined,
  });
  const productCandidates = extractProductCandidates(productSearch.data);
  const worklistQ = useClinicalWorklist({
    facilityId: facility?.id,
    size: 80,
  });
  const orchestrationItems = (worklistQ.data?.data?.items ?? []).filter((item) => {
    const itemPatient = String((item.patient_id as string | undefined) ?? "");
    return !itemPatient || itemPatient === patientId;
  });
  const laneCounts = orchestrationItems.reduce(
    (acc, item) => {
      const kind = String(item.kind ?? "").toUpperCase();
      if (kind === "ORDER") acc.orders += 1;
      if (kind === "PHARMACY") acc.pharmacy += 1;
      if (kind === "REFERRAL") acc.referrals += 1;
      if (kind === "TELEMEDICINE") acc.telemedicine += 1;
      return acc;
    },
    { orders: 0, pharmacy: 0, referrals: 0, telemedicine: 0 }
  );
  const unsupportedLane = guidedLane === "PROCEDURE" || guidedLane === "ORDER_SET";
  const guidedValidationError = validateGuidedForm(guidedLane, guidedForm);
  const guidedSubmitDisabled = guidedSubmitting || unsupportedLane || !!guidedValidationError;

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function updateGuidedField(field: keyof typeof guidedForm, value: string) {
    setGuidedForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleGuidedSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setGuidedError(null);
    setGuidedSuccess(null);

    if (!facility?.id) {
      setGuidedError("Select a facility context before submitting guided orders.");
      return;
    }
    if (!activeEncounter?.id && guidedLane !== "TELEMEDICINE") {
      setGuidedError("Start an encounter first so order orchestration stays episode-scoped.");
      return;
    }

    if (unsupportedLane) {
      setGuidedError(
        "This lane is visible but not yet writable through a typed BFF command contract. No fake submit is executed."
      );
      return;
    }
    if (guidedValidationError) {
      setGuidedError(guidedValidationError);
      return;
    }

    setGuidedSubmitting(true);
    try {
      if (guidedLane === "LAB") {
        await createOrder.mutateAsync({
          patientId,
          encounterId: activeEncounter?.id ?? "",
          testName: guidedForm.lab_name,
          testCode: guidedForm.lab_code,
          category: "LABORATORY",
          priority: guidedForm.lab_priority,
          clinicalNotes: null,
          facilityId: facility.id,
          orderedBy: user?.id ?? "",
          orderedByName: user?.displayName ?? user?.email ?? "",
          patientCpid: patientCpid || undefined,
          pctEncounterRef: activeEncounter?.id ?? "",
        });
      } else if (guidedLane === "IMAGING") {
        await createOrder.mutateAsync({
          patientId,
          encounterId: activeEncounter?.id ?? "",
          testName: guidedForm.imaging_name,
          testCode: `${guidedForm.imaging_modality}-${guidedForm.imaging_name.trim().replace(/\s+/g, "_").toUpperCase()}`,
          category: "IMAGING",
          priority: guidedForm.imaging_priority,
          clinicalNotes: guidedForm.imaging_notes.trim() || null,
          facilityId: facility.id,
          orderedBy: user?.id ?? "",
          orderedByName: user?.displayName ?? user?.email ?? "",
          patientCpid: patientCpid || undefined,
          pctEncounterRef: activeEncounter?.id ?? "",
        });
      } else if (guidedLane === "MEDICATION") {
        await apiClient.post("/internal/v1/pharmacy/prescriptions", {
          patient_id: patientId,
          facility_id: facility.id,
          encounter_id: activeEncounter?.id ?? null,
          medication_name: guidedForm.medication_name,
          dosage: guidedForm.dosage,
          frequency: guidedForm.frequency,
          duration: guidedForm.duration || null,
          prescribed_by: user?.id ?? "system",
        });
        await queryClient.invalidateQueries({ queryKey: ["prescriptions"] });
      } else if (guidedLane === "REFERRAL") {
        await apiClient.post("/internal/v1/referrals", {
          patient_id: patientId,
          encounter_id: activeEncounter?.id ?? null,
          referral_type: "SPECIALIST_CONSULT",
          specialty: guidedForm.referral_specialty,
          referred_to: "",
          referred_to_facility: "",
          reason: guidedForm.referral_reason,
          urgency: guidedForm.referral_urgency,
          clinical_summary: null,
          referred_by: user?.id ?? "system",
          referred_by_name: user?.displayName ?? user?.email ?? "Provider",
        });
        await queryClient.invalidateQueries({ queryKey: ["referrals"] });
      } else if (guidedLane === "TELEMEDICINE") {
        await apiClient.post("/internal/v1/teleconsult/sessions", {
          patient_id: patientId,
          provider_id: user?.id,
          facility_id: facility.id,
          session_type: guidedForm.tele_mode,
          scheduled_at: guidedForm.tele_when ? new Date(guidedForm.tele_when).toISOString() : undefined,
          notes: guidedForm.tele_notes || undefined,
        });
        await queryClient.invalidateQueries({ queryKey: ["telemedicine-sessions"] });
      }
      await queryClient.invalidateQueries({ queryKey: ["clinical-worklist"] });
      await queryClient.invalidateQueries({ queryKey: ["lab-orders"] });
      setGuidedSuccess(`Submitted ${guidedLane.toLowerCase()} workflow action.`);
    } catch {
      setGuidedError(`Failed to submit ${guidedLane.toLowerCase()} workflow action.`);
    } finally {
      setGuidedSubmitting(false);
    }
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    createOrder.mutate(
      {
        patientId,
        encounterId: activeEncounter?.id ?? "",
        testName: form.test_name,
        testCode: form.test_code,
        category: form.category,
        priority: form.priority,
        clinicalNotes: form.clinical_notes || null,
        facilityId: facility?.id ?? form.facility_id,
        orderedBy: form.ordered_by || user?.id || "",
        orderedByName: form.ordered_by_name || user?.displayName || user?.email || "",
        patientCpid: patientCpid || undefined,
        pctEncounterRef: activeEncounter?.id ?? "",
      },
      {
        onSuccess: () => {
          setForm(buildFormState());
          setShowForm(false);
        },
      }
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Lab Orders">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading orders...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {guidanceDraft && (
              <div className="flex items-start justify-between gap-3 rounded-lg border border-success/25 bg-success-soft/90 p-3 text-sm text-primary-hover">
                <div>
                  <p className="font-medium">Clinical guidance (reference only)</p>
                  <p className="mt-1 text-xs text-primary-hover/90">
                    {guidanceDraft.generic
                      ? `Medication context: ${guidanceDraft.generic}${guidanceDraft.doseMg ? `, ${guidanceDraft.doseMg} mg` : ""}.`
                      : "Prescribing evaluation summary received."}{" "}
                    This does not create a lab order; use it while you complete the order form.
                  </p>
                  {guidanceDraft.alerts && guidanceDraft.alerts.length > 0 && (
                    <ul className="mt-2 list-disc pl-4 text-xs text-warning-foreground">
                      {guidanceDraft.alerts.slice(0, 5).map((a, i) => (
                        <li key={i}>{String(a.message ?? a.code ?? "Alert")}</li>
                      ))}
                    </ul>
                  )}
                </div>
                <button
                  type="button"
                  className="shrink-0 text-xs text-primary-hover underline hover:text-emerald-950"
                  onClick={() => setGuidanceDraft(null)}
                >
                  Dismiss
                </button>
              </div>
            )}
            <ClinicalReviewHeader
              badge="Diagnostics ordering"
              badgeIcon={ClipboardList}
              title="Order, collect, result, and review diagnostics from the same encounter-aware workspace"
              description="This page now shows the diagnostic loop instead of just the order list: place new requests with the active encounter in scope, see what is waiting for collection or result entry, and move directly into results, medications, or notes from the same context."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/summary`, label: "Summary", icon: Activity },
                { href: `/ehr/${patientId}/results`, label: "Results", icon: TestTube2, tone: "secondary" },
                { href: `/ehr/${patientId}/medications`, label: "Medications", icon: Pill, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Pending collection",
                  value: String(orderedCount),
                  detail: "Ordered studies that still need specimen collection or dispatch.",
                },
                {
                  label: "Ready for result entry",
                  value: String(collectedCount),
                  detail: "Collected studies that can move straight into result capture here.",
                },
                {
                  label: "Awaiting review",
                  value: String(awaitingReviewCount),
                  detail:
                    awaitingReviewCount > 0
                      ? "Completed studies should be acknowledged in Results or from the order list below."
                      : `${reviewedCount} completed study${reviewedCount === 1 ? "" : "ies"} already reviewed.`,
                },
              ]}
            />

            {!activeEncounter && (
              <div className="rounded-3xl border border-warning/35 bg-warning-soft/80 p-4">
                <div className="flex items-start gap-3">
                  <AlertTriangle className="mt-0.5 h-5 w-5 text-amber-600" />
                  <div>
                    <p className="text-sm font-medium text-warning-foreground">No active encounter is in scope</p>
                    <p className="mt-1 text-sm text-warning-foreground">
                      Existing orders remain visible, but new orders should start from an active encounter so collection, results, and follow-up stay tied to the right clinical episode.
                    </p>
                  </div>
                </div>
              </div>
            )}

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                Ordering loop status
              </p>
              <p className="mt-2 text-sm text-foreground">
                {collectedCount > 0
                  ? `${collectedCount} collected order${collectedCount === 1 ? " is" : "s are"} ready for result entry in this workspace.`
                  : orderedCount > 0
                    ? `${orderedCount} order${orderedCount === 1 ? " is" : "s are"} still waiting for collection before the loop can move forward.`
                    : awaitingReviewCount > 0
                      ? `${awaitingReviewCount} result${awaitingReviewCount === 1 ? " is" : "s are"} ready for clinical acknowledgement.`
                      : "The diagnostic loop is clear right now. New orders can be placed here when the encounter needs additional workup."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use the actions below to keep ordering, collection, result entry, and review on one surface instead of switching across multiple pages.
              </p>
            </div>

            <div className="rounded-3xl border border-info/25 bg-info-soft/60 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-indigo-600">
                Unified order orchestration
              </p>
              <p className="mt-2 text-sm text-primary-hover">
                This workspace now composes cross-domain actions (lab, pharmacy, referrals, telemedicine) from the clinical worklist rail instead of treating lab as the only active lane.
              </p>
              <div className="mt-3 grid gap-3 md:grid-cols-4">
                <div className="rounded-xl border border-indigo-100 bg-card p-3">
                  <p className="text-xs text-indigo-500">Orders</p>
                  <p className="text-xl font-semibold text-primary-hover">{laneCounts.orders}</p>
                </div>
                <div className="rounded-xl border border-indigo-100 bg-card p-3">
                  <p className="text-xs text-indigo-500">Pharmacy</p>
                  <p className="text-xl font-semibold text-primary-hover">{laneCounts.pharmacy}</p>
                </div>
                <div className="rounded-xl border border-indigo-100 bg-card p-3">
                  <p className="text-xs text-indigo-500">Referrals</p>
                  <p className="text-xl font-semibold text-primary-hover">{laneCounts.referrals}</p>
                </div>
                <div className="rounded-xl border border-indigo-100 bg-card p-3">
                  <p className="text-xs text-indigo-500">Telemedicine</p>
                  <p className="text-xl font-semibold text-primary-hover">{laneCounts.telemedicine}</p>
                </div>
              </div>
              <div className="mt-3 flex flex-wrap gap-2 text-xs">
                <Link href={`/ehr/${patientId}/medications`} className="rounded-lg border border-info/25 bg-card px-2.5 py-1.5 text-primary-hover hover:bg-indigo-100">
                  Medication lane
                </Link>
                <Link href={`/home/referrals`} className="rounded-lg border border-info/25 bg-card px-2.5 py-1.5 text-primary-hover hover:bg-indigo-100">
                  Referral lane
                </Link>
                <Link href={`/ehr/${patientId}/imaging`} className="rounded-lg border border-info/25 bg-card px-2.5 py-1.5 text-primary-hover hover:bg-indigo-100">
                  Imaging lane
                </Link>
                <Link href={`/ehr/${patientId}/procedures`} className="rounded-lg border border-info/25 bg-card px-2.5 py-1.5 text-primary-hover hover:bg-indigo-100">
                  Procedure lane
                </Link>
                <Link
                  href={`/madi/orders?patientId=${encodeURIComponent(patientCpid || patientId)}`}
                  className="rounded-lg border border-danger/28 bg-danger-soft px-2.5 py-1.5 text-rose-800 hover:bg-rose-100 inline-flex items-center gap-1"
                  data-testid="ehr-order-blood-link"
                >
                  <Droplet className="h-3.5 w-3.5" />
                  Order blood (Madi)
                </Link>
              </div>
            </div>

            <div className="rounded-3xl border border-danger/28 bg-danger-soft/60 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-danger">
                Blood products
              </p>
              <p className="mt-2 text-sm text-danger">
                Order crossmatched blood for this patient through Madi — sample collection, crossmatch, issue and transfusion are tracked in the blood bank workspace.
              </p>
              <Link
                href={`/madi/orders?patientId=${encodeURIComponent(patientCpid || patientId)}`}
                className="mt-3 inline-flex items-center gap-2 rounded-lg bg-rose-600 px-3 py-2 text-sm font-medium text-white hover:bg-rose-700"
                data-testid="ehr-order-blood-card"
              >
                <Droplet className="h-4 w-4" />
                Order blood for patient
              </Link>
            </div>

            <div className="rounded-3xl border border-cyan-200 bg-cyan-50/60 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-cyan-700">
                Guided cross-domain order composer
              </p>
              <p className="mt-2 text-sm text-cyan-900">
                Compose lab, medication, referral, and teleconsult actions in one place. Lanes without typed BFF write contracts stay visible but explicitly non-submittable.
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                {GUIDED_LANES.map((lane) => {
                  const Icon = lane.icon;
                  const active = guidedLane === lane.key;
                  return (
                    <button
                      key={lane.key}
                      type="button"
                      onClick={() => {
                        setGuidedLane(lane.key);
                        setGuidedError(null);
                        setGuidedSuccess(null);
                      }}
                      data-testid={`guided-lane-${lane.key.toLowerCase()}`}
                      className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors ${
                        active
                          ? "border-cyan-400 bg-cyan-100 text-cyan-900"
                          : "border-cyan-200 bg-card text-cyan-700 hover:bg-cyan-100/60"
                      }`}
                    >
                      <Icon className="h-3.5 w-3.5" />
                      {lane.label}
                    </button>
                  );
                })}
              </div>

              <form onSubmit={handleGuidedSubmit} className="mt-4 space-y-3 rounded-xl border border-cyan-100 bg-card p-4">
                {guidedLane === "LAB" && (
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                    <label className="text-xs font-medium text-foreground">
                      Test Name
                      <input
                        type="text"
                        data-testid="guided-lab-name"
                        value={guidedForm.lab_name}
                        onChange={(e) => updateGuidedField("lab_name", e.target.value)}
                        required
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="e.g. FBC"
                      />
                    </label>
                    <label className="text-xs font-medium text-foreground">
                      Test Code
                      <input
                        type="text"
                        data-testid="guided-lab-code"
                        value={guidedForm.lab_code}
                        onChange={(e) => updateGuidedField("lab_code", e.target.value)}
                        required
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="e.g. CBC"
                      />
                    </label>
                    <label className="text-xs font-medium text-foreground">
                      Priority
                      <select
                        value={guidedForm.lab_priority}
                        onChange={(e) => updateGuidedField("lab_priority", e.target.value)}
                        className="mt-1 w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
                      >
                        <option value="ROUTINE">Routine</option>
                        <option value="URGENT">Urgent</option>
                        <option value="STAT">STAT</option>
                      </select>
                    </label>
                  </div>
                )}

                {guidedLane === "MEDICATION" && (
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
                    <label className="text-xs font-medium text-foreground">
                      Medication
                      <input
                        type="text"
                        value={guidedForm.medication_name}
                        onChange={(e) => updateGuidedField("medication_name", e.target.value)}
                        required
                        data-testid="guided-medication-name"
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="e.g. Amoxicillin"
                      />
                    </label>
                    <label className="text-xs font-medium text-foreground">
                      Dosage
                      <input
                        type="text"
                        value={guidedForm.dosage}
                        onChange={(e) => updateGuidedField("dosage", e.target.value)}
                        required
                        data-testid="guided-medication-dosage"
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="e.g. 500mg"
                      />
                    </label>
                    <label className="text-xs font-medium text-foreground">
                      Frequency
                      <input
                        type="text"
                        value={guidedForm.frequency}
                        onChange={(e) => updateGuidedField("frequency", e.target.value)}
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="text-xs font-medium text-foreground">
                      Duration
                      <input
                        type="text"
                        value={guidedForm.duration}
                        onChange={(e) => updateGuidedField("duration", e.target.value)}
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="e.g. 5 days"
                      />
                    </label>
                  </div>
                )}

                {guidedLane === "REFERRAL" && (
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                    <label className="text-xs font-medium text-foreground">
                      Specialty
                      <input
                        type="text"
                        value={guidedForm.referral_specialty}
                        onChange={(e) => updateGuidedField("referral_specialty", e.target.value)}
                        required
                        data-testid="guided-referral-specialty"
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="e.g. Cardiology"
                      />
                    </label>
                    <label className="text-xs font-medium text-foreground">
                      Reason
                      <input
                        type="text"
                        value={guidedForm.referral_reason}
                        onChange={(e) => updateGuidedField("referral_reason", e.target.value)}
                        required
                        data-testid="guided-referral-reason"
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="text-xs font-medium text-foreground">
                      Urgency
                      <select
                        value={guidedForm.referral_urgency}
                        onChange={(e) => updateGuidedField("referral_urgency", e.target.value)}
                        className="mt-1 w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
                      >
                        <option value="ROUTINE">Routine</option>
                        <option value="URGENT">Urgent</option>
                        <option value="EMERGENCY">Emergency</option>
                      </select>
                    </label>
                  </div>
                )}

                {guidedLane === "TELEMEDICINE" && (
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                    <label className="text-xs font-medium text-foreground">
                      Session type
                      <select
                        value={guidedForm.tele_mode}
                        onChange={(e) => updateGuidedField("tele_mode", e.target.value)}
                        data-testid="guided-tele-mode"
                        className="mt-1 w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
                      >
                        <option value="VIDEO">Video</option>
                        <option value="AUDIO">Audio</option>
                        <option value="CHAT">Chat</option>
                        <option value="CASE_REVIEW">Case Review</option>
                      </select>
                    </label>
                    <label className="text-xs font-medium text-foreground">
                      Scheduled for
                      <input
                        type="datetime-local"
                        value={guidedForm.tele_when}
                        onChange={(e) => updateGuidedField("tele_when", e.target.value)}
                        data-testid="guided-tele-when"
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="text-xs font-medium text-foreground md:col-span-1">
                      Notes
                      <input
                        type="text"
                        value={guidedForm.tele_notes}
                        onChange={(e) => updateGuidedField("tele_notes", e.target.value)}
                        data-testid="guided-tele-notes"
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="Reason for teleconsult"
                      />
                    </label>
                  </div>
                )}

                {guidedLane === "IMAGING" && (
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                    <label className="text-xs font-medium text-foreground md:col-span-2">
                      Study / procedure
                      <input
                        type="text"
                        data-testid="guided-imaging-name"
                        value={guidedForm.imaging_name}
                        onChange={(e) => updateGuidedField("imaging_name", e.target.value)}
                        required
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="e.g. Chest X-ray PA view"
                      />
                    </label>
                    <label className="text-xs font-medium text-foreground">
                      Modality
                      <select
                        value={guidedForm.imaging_modality}
                        onChange={(e) => updateGuidedField("imaging_modality", e.target.value)}
                        className="mt-1 w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
                      >
                        <option value="XRAY">X-ray</option>
                        <option value="CT">CT</option>
                        <option value="MRI">MRI</option>
                        <option value="ULTRASOUND">Ultrasound</option>
                        <option value="MAMMOGRAPHY">Mammography</option>
                      </select>
                    </label>
                    <label className="text-xs font-medium text-foreground">
                      Priority
                      <select
                        value={guidedForm.imaging_priority}
                        onChange={(e) => updateGuidedField("imaging_priority", e.target.value)}
                        className="mt-1 w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
                      >
                        <option value="ROUTINE">Routine</option>
                        <option value="URGENT">Urgent</option>
                        <option value="STAT">STAT</option>
                      </select>
                    </label>
                    <label className="text-xs font-medium text-foreground md:col-span-2">
                      Clinical indication
                      <input
                        type="text"
                        data-testid="guided-imaging-notes"
                        value={guidedForm.imaging_notes}
                        onChange={(e) => updateGuidedField("imaging_notes", e.target.value)}
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="Brief indication"
                      />
                    </label>
                  </div>
                )}

                {(guidedLane === "PROCEDURE" || guidedLane === "ORDER_SET") && (
                  <div className="rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-xs text-warning-foreground">
                    This lane is intentionally read-only until a typed Experience BFF write contract is available.
                  </div>
                )}
                {guidedValidationError && !unsupportedLane ? (
                  <div className="rounded-lg border border-danger/28 bg-danger-soft px-3 py-2 text-xs text-danger">
                    {guidedValidationError}
                  </div>
                ) : null}

                <div className="flex items-center gap-3">
                  <button
                    type="submit"
                    disabled={guidedSubmitDisabled}
                    data-testid="guided-submit-action"
                    className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-cyan-700 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {guidedSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    Submit guided action
                  </button>
                  {guidedSuccess ? <span className="text-xs text-green-700">{guidedSuccess}</span> : null}
                  {guidedError ? <span className="text-xs text-danger">{guidedError}</span> : null}
                </div>
              </form>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardList className="h-5 w-5 text-indigo-500" />
                <h2 className="text-lg font-semibold text-foreground">Orders ({orders.length})</h2>
              </div>
              {isClinical && (
                <button
                  type="button"
                  onClick={() => setShowForm((prev) => !prev)}
                  disabled={!activeEncounter}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Plus className="h-4 w-4" />
                  Add Order
                </button>
              )}
            </div>

            {showForm && (
              <div className="rounded-lg border border-border bg-card p-5">
                <div className="mb-4 flex items-center gap-2">
                  <TestTube2 className="h-5 w-5 text-indigo-500" />
                  <h3 className="font-medium text-foreground">New Lab Order</h3>
                </div>
                <div className="mb-4 rounded-lg border border-border bg-background px-4 py-3 text-sm text-foreground">
                  <p className="font-medium text-foreground">Product / service lookup (shared rail)</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Optional assistance: search{" "}
                    <code className="rounded bg-card px-1">GET /internal/v1/product-registry/search</code> to fill Test Name and
                    Test Code. If nothing matches, you can still enter a free-text order (no fake catalog is shown).
                  </p>
                  <div className="mt-3 flex flex-wrap items-end gap-3">
                    <label className="text-xs font-medium text-foreground">
                      Search term
                      <input
                        type="text"
                        value={catalogQuery}
                        onChange={(e) => setCatalogQuery(e.target.value)}
                        placeholder="e.g. CBC, Malaria smear, CXR"
                        className="mt-1 block w-80 max-w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
                      />
                    </label>
                    <div className="text-xs text-muted-foreground">
                      {catalogQuery.trim().length < 2
                        ? "Type at least 2 characters to search."
                        : productSearch.isLoading
                          ? "Searching registry…"
                          : productSearch.isError
                            ? "Registry search failed."
                            : productCandidates.length === 0
                              ? "No matches."
                              : `${productCandidates.length} match${productCandidates.length === 1 ? "" : "es"} shown.`}
                    </div>
                  </div>
                  {catalogQuery.trim().length >= 2 && productCandidates.length > 0 && (
                    <div className="mt-3 overflow-x-auto rounded-lg border border-border bg-card">
                      <table className="w-full min-w-[520px] text-sm">
                        <thead className="bg-background text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          <tr>
                            <th className="px-3 py-2">Name</th>
                            <th className="px-3 py-2">Code</th>
                            <th className="px-3 py-2">Kind</th>
                            <th className="px-3 py-2">Action</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {productCandidates.map((c) => (
                            <tr key={c.id} className="hover:bg-background/70">
                              <td className="px-3 py-2 text-foreground">{c.name || "—"}</td>
                              <td className="px-3 py-2 font-mono text-xs text-foreground">{c.code || "—"}</td>
                              <td className="px-3 py-2 text-foreground">{c.kind ?? "—"}</td>
                              <td className="px-3 py-2">
                                <button
                                  type="button"
                                  onClick={() => {
                                    if (c.name) updateField("test_name", c.name);
                                    if (c.code) updateField("test_code", c.code);
                                  }}
                                  className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background"
                                >
                                  Use
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Test Name</label>
                      <input
                        type="text"
                        value={form.test_name}
                        onChange={(e) => updateField("test_name", e.target.value)}
                        placeholder="e.g. Complete Blood Count"
                        required
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Test Code</label>
                      <input
                        type="text"
                        value={form.test_code}
                        onChange={(e) => updateField("test_code", e.target.value)}
                        placeholder="e.g. CBC"
                        required
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Category</label>
                      <select
                        value={form.category}
                        onChange={(e) => updateField("category", e.target.value)}
                        className="w-full rounded-lg border border-border bg-card px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                      >
                        <option value="LABORATORY">Laboratory</option>
                        <option value="RADIOLOGY">Radiology</option>
                        <option value="PATHOLOGY">Pathology</option>
                      </select>
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Priority</label>
                      <select
                        value={form.priority}
                        onChange={(e) => updateField("priority", e.target.value)}
                        className="w-full rounded-lg border border-border bg-card px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                      >
                        <option value="ROUTINE">Routine</option>
                        <option value="URGENT">Urgent</option>
                        <option value="STAT">STAT</option>
                      </select>
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Ordered By (ID)</label>
                      <input
                        type="text"
                        value={form.ordered_by}
                        onChange={(e) => updateField("ordered_by", e.target.value)}
                        placeholder="Practitioner ID"
                        required
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Ordered By (Name)</label>
                      <input
                        type="text"
                        value={form.ordered_by_name}
                        onChange={(e) => updateField("ordered_by_name", e.target.value)}
                        placeholder="Dr. Jane Smith"
                        required
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Facility ID</label>
                    <input
                      type="text"
                      value={form.facility_id}
                      onChange={(e) => updateField("facility_id", e.target.value)}
                      placeholder="facility-uuid"
                      required
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Clinical Notes</label>
                    <textarea
                      value={form.clinical_notes}
                      onChange={(e) => updateField("clinical_notes", e.target.value)}
                      placeholder="Any relevant clinical context..."
                      rows={3}
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 resize-none"
                    />
                  </div>
                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={() => {
                        setShowForm(false);
                        setForm(buildFormState());
                      }}
                      className="flex-1 rounded-lg bg-neutral-100 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-neutral-100"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={createOrder.isPending}
                      className="flex flex-1 items-center justify-center gap-2 rounded-lg bg-primary py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {createOrder.isPending ? (
                        <>
                          <Loader2 className="h-4 w-4 animate-spin" />
                          Submitting...
                        </>
                      ) : (
                        <>
                          <Plus className="h-4 w-4" />
                          Submit Order
                        </>
                      )}
                    </button>
                  </div>
                  {createOrder.isError && (
                    <p className="text-center text-sm text-red-600">
                      Failed to create order. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {resultingOrder && (
              <div className="rounded-lg border-2 border-green-300 bg-card p-5">
                <div className="mb-4 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <TestTube2 className="h-5 w-5 text-green-600" />
                    <h3 className="font-medium text-foreground">
                      Enter Results: {resultingOrder.attributes.testName}
                    </h3>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setResultingOrder(null);
                      setResultValues([{ name: "", value: "", unit: "", referenceRange: "", interpretation: "NORMAL" }]);
                      setResultNotes("");
                    }}
                    className="text-xs text-muted-foreground hover:text-foreground"
                  >
                    Cancel
                  </button>
                </div>
                <div className="mb-3 space-y-2">
                  {resultValues.map((value, index) => (
                    <div key={index} className="grid grid-cols-5 gap-2">
                      <input
                        type="text"
                        value={value.name}
                        placeholder="Test name"
                        onChange={(e) => {
                          const next = [...resultValues];
                          next[index] = { ...value, name: e.target.value };
                          setResultValues(next);
                        }}
                        className="rounded border border-border px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-green-500"
                      />
                      <input
                        type="text"
                        value={value.value}
                        placeholder="Value"
                        onChange={(e) => {
                          const next = [...resultValues];
                          next[index] = { ...value, value: e.target.value };
                          setResultValues(next);
                        }}
                        className="rounded border border-border px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-green-500"
                      />
                      <input
                        type="text"
                        value={value.unit}
                        placeholder="Unit"
                        onChange={(e) => {
                          const next = [...resultValues];
                          next[index] = { ...value, unit: e.target.value };
                          setResultValues(next);
                        }}
                        className="rounded border border-border px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-green-500"
                      />
                      <input
                        type="text"
                        value={value.referenceRange}
                        placeholder="Ref range"
                        onChange={(e) => {
                          const next = [...resultValues];
                          next[index] = { ...value, referenceRange: e.target.value };
                          setResultValues(next);
                        }}
                        className="rounded border border-border px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-green-500"
                      />
                      <select
                        value={value.interpretation}
                        onChange={(e) => {
                          const next = [...resultValues];
                          next[index] = { ...value, interpretation: e.target.value };
                          setResultValues(next);
                        }}
                        className="rounded border border-border px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-green-500"
                      >
                        <option value="NORMAL">Normal</option>
                        <option value="ABNORMAL">Abnormal</option>
                        <option value="CRITICAL">Critical</option>
                      </select>
                    </div>
                  ))}
                  <button
                    type="button"
                    onClick={() => setResultValues([...resultValues, { name: "", value: "", unit: "", referenceRange: "", interpretation: "NORMAL" }])}
                    className="text-xs text-primary hover:text-primary-hover"
                  >
                    + Add row
                  </button>
                </div>
                <textarea
                  value={resultNotes}
                  onChange={(e) => setResultNotes(e.target.value)}
                  rows={2}
                  placeholder="Result notes..."
                  className="mb-3 w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500 resize-none"
                />
                <button
                  type="button"
                  onClick={async () => {
                    setResultSubmitting(true);
                    try {
                      const validResults = resultValues.filter((value) => value.name.trim() && value.value.trim());
                      await apiClient.post(`/internal/v1/lab-orders/${resultingOrder.id}/result`, {
                        result_data: validResults,
                        result_notes: resultNotes || null,
                        resulted_by: user?.id ?? "system",
                        resulted_by_name: user?.displayName ?? user?.email ?? "",
                      });
                      await queryClient.invalidateQueries({ queryKey: ["lab-orders"] });
                      setResultingOrder(null);
                      setResultValues([{ name: "", value: "", unit: "", referenceRange: "", interpretation: "NORMAL" }]);
                      setResultNotes("");
                    } finally {
                      setResultSubmitting(false);
                    }
                  }}
                  disabled={resultSubmitting || resultValues.every((value) => !value.name.trim())}
                  className="flex w-full items-center justify-center gap-2 rounded-lg bg-green-600 py-2 text-sm font-medium text-white transition-colors hover:bg-green-700 disabled:opacity-50"
                >
                  {resultSubmitting ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" /> Submitting...
                    </>
                  ) : (
                    <>
                      <Save className="h-4 w-4" /> Submit Results
                    </>
                  )}
                </button>
              </div>
            )}

            {orders.length === 0 ? (
              <div className="rounded-lg border border-border bg-card p-12 text-center">
                <TestTube2 className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                <p className="text-sm text-muted-foreground">No lab orders found</p>
              </div>
            ) : (
              <div className="overflow-hidden rounded-lg border border-border bg-card">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border bg-background">
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-muted-foreground">Order #</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-muted-foreground">Test Name</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-muted-foreground">Category</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-muted-foreground">Priority</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-muted-foreground">Status</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-muted-foreground">Ordered By</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-muted-foreground">Date</th>
                        <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-muted-foreground">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {orders.map((order) => (
                        <tr key={order.id} className="transition-colors hover:bg-background">
                          <td className="whitespace-nowrap px-4 py-3 font-medium text-foreground">{order.attributes.orderNumber}</td>
                          <td className="whitespace-nowrap px-4 py-3 text-foreground">{order.attributes.testName}</td>
                          <td className="whitespace-nowrap px-4 py-3 text-muted-foreground">{order.attributes.category}</td>
                          <td className="whitespace-nowrap px-4 py-3">
                            <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${PRIORITY_BADGE[order.attributes.priority] ?? "bg-neutral-100 text-muted-foreground"}`}>
                              {order.attributes.priority}
                            </span>
                          </td>
                          <td className="whitespace-nowrap px-4 py-3">
                            <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_BADGE[order.attributes.status] ?? "bg-neutral-100 text-muted-foreground"}`}>
                              {order.attributes.status}
                            </span>
                          </td>
                          <td className="whitespace-nowrap px-4 py-3 text-muted-foreground">{order.attributes.orderedByName}</td>
                          <td className="whitespace-nowrap px-4 py-3 text-muted-foreground">{new Date(order.attributes.createdAt).toLocaleDateString()}</td>
                          <td className="whitespace-nowrap px-4 py-3 text-right">
                            {order.attributes.status === "ORDERED" && (
                              <div className="inline-flex gap-1">
                                <button
                                  type="button"
                                  onClick={() => collectOrder.mutate({ id: order.id })}
                                  disabled={collectOrder.isPending}
                                  className="rounded bg-yellow-100 px-2.5 py-1 text-xs font-medium text-yellow-700 transition-colors hover:bg-yellow-200"
                                >
                                  Collect
                                </button>
                                <button
                                  type="button"
                                  onClick={() => cancelOrder.mutate({ id: order.id })}
                                  disabled={cancelOrder.isPending}
                                  className="rounded bg-danger-soft px-2.5 py-1 text-xs font-medium text-red-600 transition-colors hover:bg-red-100"
                                >
                                  Cancel
                                </button>
                              </div>
                            )}
                            {order.attributes.status === "COLLECTED" && (
                              <button
                                type="button"
                                onClick={() => setResultingOrder(order)}
                                className="rounded bg-green-100 px-2.5 py-1 text-xs font-medium text-green-700 transition-colors hover:bg-green-200"
                              >
                                Enter Result
                              </button>
                            )}
                            {order.attributes.status === "RESULTED" && (
                              <button
                                type="button"
                                onClick={async () => {
                                  await apiClient.post(`/internal/v1/lab-orders/${order.id}/acknowledge`);
                                  await queryClient.invalidateQueries({ queryKey: ["lab-orders"] });
                                }}
                                className="rounded bg-purple-100 px-2.5 py-1 text-xs font-medium text-warning-foreground transition-colors hover:bg-purple-200"
                              >
                                Acknowledge
                              </button>
                            )}
                            {order.attributes.status === "REVIEWED" && (
                              <span className="flex items-center justify-end gap-1 text-xs text-purple-600">
                                <CheckCircle2 className="h-3 w-3" /> Reviewed
                              </span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
