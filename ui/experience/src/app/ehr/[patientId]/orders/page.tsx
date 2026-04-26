"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  ClipboardList,
  FileText,
  Loader2,
  Pill,
  Plus,
  Save,
  TestTube2,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { PageShell } from "@/components/PageShell";
import {
  useCancelLabOrder,
  useCollectLabOrder,
  useCreateLabOrder,
  useLabOrders,
  type LabOrderResource,
} from "@/hooks/queries/useLabOrders";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useImagingStudies } from "@/hooks/queries/useImaging";
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
  ORDERED: "bg-impilo-100 text-impilo-600",
  COLLECTED: "bg-yellow-100 text-yellow-700",
  RESULTED: "bg-green-100 text-green-700",
  REVIEWED: "bg-purple-100 text-purple-700",
  CANCELLED: "bg-gray-100 text-gray-600",
};

const PRIORITY_BADGE: Record<string, string> = {
  STAT: "bg-red-100 text-red-700",
  URGENT: "bg-orange-100 text-orange-700",
  ROUTINE: "bg-impilo-100 text-impilo-600",
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

type ProductRegistryCandidate = {
  id: string;
  name: string;
  code: string;
  kind?: string;
};

function extractProductCandidates(payload: unknown): ProductRegistryCandidate[] {
  const root = payload as any;
  const list =
    (Array.isArray(root) && root) ||
    (Array.isArray(root?.items) && root.items) ||
    (Array.isArray(root?.data) && root.data) ||
    (Array.isArray(root?.results) && root.results) ||
    [];

  return list
    .map((raw: any, i: number) => {
      const id = String(raw?.id ?? raw?.itemId ?? raw?.sku ?? raw?.code ?? `item-${i}`);
      const name = String(raw?.name ?? raw?.label ?? raw?.title ?? raw?.displayName ?? raw?.description ?? "");
      const code = String(raw?.code ?? raw?.productCode ?? raw?.sku ?? raw?.itemCode ?? "");
      const kind = raw?.kind != null ? String(raw.kind) : undefined;
      return { id, name, code, kind } satisfies ProductRegistryCandidate;
    })
    .filter((c: ProductRegistryCandidate) => c.name.trim() !== "" || c.code.trim() !== "");
}

export default function OrdersPage() {
  const params = useParams<{ patientId: string }>();
  const { patientId } = params;
  const { user } = useAuthStore();
  const { isClinical } = useRoleGroup();
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );

  const queryClient = useQueryClient();
  const { data: ordersData, isLoading } = useLabOrders(patientId);
  const { data: imagingBundle } = useImagingStudies(patientId);
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
  const imagingStudies = (imagingBundle as { data?: unknown[] } | undefined)?.data ?? [];
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

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
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
    <PageShell
      title="Orders & Results"
      subtitle="Laboratory orders are proxied to OROS through the Experience BFF. Imaging studies use the governed imaging rail (Orthanc / PACS adapter) — open Imaging for modalities, status, and viewer handoff without raw PACS chrome."
    >
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading orders...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {guidanceDraft && (
              <div className="flex items-start justify-between gap-3 rounded-lg border border-emerald-200 bg-emerald-50/90 p-3 text-sm text-emerald-900">
                <div>
                  <p className="font-medium">Clinical guidance (reference only)</p>
                  <p className="mt-1 text-xs text-emerald-800/90">
                    {guidanceDraft.generic
                      ? `Medication context: ${guidanceDraft.generic}${guidanceDraft.doseMg ? `, ${guidanceDraft.doseMg} mg` : ""}.`
                      : "Prescribing evaluation summary received."}{" "}
                    This does not create a lab order; use it while you complete the order form.
                  </p>
                  {guidanceDraft.alerts && guidanceDraft.alerts.length > 0 && (
                    <ul className="mt-2 list-disc pl-4 text-xs text-amber-900">
                      {guidanceDraft.alerts.slice(0, 5).map((a, i) => (
                        <li key={i}>{String(a.message ?? a.code ?? "Alert")}</li>
                      ))}
                    </ul>
                  )}
                </div>
                <button
                  type="button"
                  className="shrink-0 text-xs text-emerald-800 underline hover:text-emerald-950"
                  onClick={() => setGuidanceDraft(null)}
                >
                  Dismiss
                </button>
              </div>
            )}
            <div className="grid gap-4 lg:grid-cols-2">
              <div className="rounded-xl border border-slate-200 bg-white p-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Imaging (PACS)</p>
                <p className="mt-1 text-sm text-slate-800">
                  {imagingStudies.length === 0
                    ? "No governed imaging studies returned for this patient yet."
                    : `${imagingStudies.length} study record(s) available from the imaging service.`}
                </p>
                <Link
                  href={`/ehr/${patientId}/imaging`}
                  className="mt-3 inline-flex items-center gap-1 text-sm font-medium text-impilo-600 hover:text-impilo-800"
                >
                  Open imaging workspace
                </Link>
              </div>
              <div className="rounded-xl border border-slate-200 bg-slate-50/80 p-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Results acknowledgement</p>
                <p className="mt-1 text-sm text-slate-700">
                  Use the lab table below to collect specimens, enter results, acknowledge abnormal flags, and keep the encounter loop aligned with referrals and teleconsults.
                </p>
              </div>
            </div>

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
              <div className="rounded-3xl border border-amber-200 bg-amber-50/80 p-4">
                <div className="flex items-start gap-3">
                  <AlertTriangle className="mt-0.5 h-5 w-5 text-amber-600" />
                  <div>
                    <p className="text-sm font-medium text-amber-900">No active encounter is in scope</p>
                    <p className="mt-1 text-sm text-amber-800">
                      Existing orders remain visible, but new orders should start from an active encounter so collection, results, and follow-up stay tied to the right clinical episode.
                    </p>
                  </div>
                </div>
              </div>
            )}

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                Ordering loop status
              </p>
              <p className="mt-2 text-sm text-slate-800">
                {collectedCount > 0
                  ? `${collectedCount} collected order${collectedCount === 1 ? "" : "s"} are ready for result entry in this workspace.`
                  : orderedCount > 0
                    ? `${orderedCount} order${orderedCount === 1 ? "" : "s"} are still waiting for collection before the loop can move forward.`
                    : awaitingReviewCount > 0
                      ? `${awaitingReviewCount} result${awaitingReviewCount === 1 ? "" : "s"} are ready for clinical acknowledgement.`
                      : "The diagnostic loop is clear right now. New orders can be placed here when the encounter needs additional workup."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use the actions below to keep ordering, collection, result entry, and review on one surface instead of switching across multiple pages.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardList className="h-5 w-5 text-indigo-500" />
                <h2 className="text-lg font-semibold text-gray-900">Orders ({orders.length})</h2>
              </div>
              {isClinical && (
                <button
                  type="button"
                  onClick={() => setShowForm((prev) => !prev)}
                  disabled={!activeEncounter}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-impilo-500 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-impilo-600 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Plus className="h-4 w-4" />
                  Add Order
                </button>
              )}
            </div>

            {showForm && (
              <div className="rounded-lg border border-gray-200 bg-white p-5">
                <div className="mb-4 flex items-center gap-2">
                  <TestTube2 className="h-5 w-5 text-indigo-500" />
                  <h3 className="font-medium text-gray-900">New Lab Order</h3>
                </div>
                <div className="mb-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-800">
                  <p className="font-medium text-slate-900">Product / service lookup (shared rail)</p>
                  <p className="mt-1 text-xs text-slate-600">
                    Optional assistance: search{" "}
                    <code className="rounded bg-white px-1">GET /internal/v1/product-registry/search</code> to fill Test Name and
                    Test Code. If nothing matches, you can still enter a free-text order (no fake catalog is shown).
                  </p>
                  <div className="mt-3 flex flex-wrap items-end gap-3">
                    <label className="text-xs font-medium text-slate-700">
                      Search term
                      <input
                        type="text"
                        value={catalogQuery}
                        onChange={(e) => setCatalogQuery(e.target.value)}
                        placeholder="e.g. CBC, Malaria smear, CXR"
                        className="mt-1 block w-80 max-w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
                      />
                    </label>
                    <div className="text-xs text-slate-600">
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
                    <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200 bg-white">
                      <table className="w-full min-w-[520px] text-sm">
                        <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                          <tr>
                            <th className="px-3 py-2">Name</th>
                            <th className="px-3 py-2">Code</th>
                            <th className="px-3 py-2">Kind</th>
                            <th className="px-3 py-2">Action</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {productCandidates.map((c) => (
                            <tr key={c.id} className="hover:bg-slate-50/70">
                              <td className="px-3 py-2 text-slate-900">{c.name || "—"}</td>
                              <td className="px-3 py-2 font-mono text-xs text-slate-700">{c.code || "—"}</td>
                              <td className="px-3 py-2 text-slate-700">{c.kind ?? "—"}</td>
                              <td className="px-3 py-2">
                                <button
                                  type="button"
                                  onClick={() => {
                                    if (c.name) updateField("test_name", c.name);
                                    if (c.code) updateField("test_code", c.code);
                                  }}
                                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-800 hover:bg-slate-50"
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
                      <label className="mb-1 block text-xs font-medium text-gray-600">Test Name</label>
                      <input
                        type="text"
                        value={form.test_name}
                        onChange={(e) => updateField("test_name", e.target.value)}
                        placeholder="e.g. Complete Blood Count"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Test Code</label>
                      <input
                        type="text"
                        value={form.test_code}
                        onChange={(e) => updateField("test_code", e.target.value)}
                        placeholder="e.g. CBC"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Category</label>
                      <select
                        value={form.category}
                        onChange={(e) => updateField("category", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                      >
                        <option value="LABORATORY">Laboratory</option>
                        <option value="RADIOLOGY">Radiology</option>
                        <option value="PATHOLOGY">Pathology</option>
                      </select>
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Priority</label>
                      <select
                        value={form.priority}
                        onChange={(e) => updateField("priority", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                      >
                        <option value="ROUTINE">Routine</option>
                        <option value="URGENT">Urgent</option>
                        <option value="STAT">STAT</option>
                      </select>
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Ordered By (ID)</label>
                      <input
                        type="text"
                        value={form.ordered_by}
                        onChange={(e) => updateField("ordered_by", e.target.value)}
                        placeholder="Practitioner ID"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Ordered By (Name)</label>
                      <input
                        type="text"
                        value={form.ordered_by_name}
                        onChange={(e) => updateField("ordered_by_name", e.target.value)}
                        placeholder="Dr. Jane Smith"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-gray-600">Facility ID</label>
                    <input
                      type="text"
                      value={form.facility_id}
                      onChange={(e) => updateField("facility_id", e.target.value)}
                      placeholder="facility-uuid"
                      required
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-gray-600">Clinical Notes</label>
                    <textarea
                      value={form.clinical_notes}
                      onChange={(e) => updateField("clinical_notes", e.target.value)}
                      placeholder="Any relevant clinical context..."
                      rows={3}
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 resize-none"
                    />
                  </div>
                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={() => {
                        setShowForm(false);
                        setForm(buildFormState());
                      }}
                      className="flex-1 rounded-lg bg-gray-100 py-2.5 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-200"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={createOrder.isPending}
                      className="flex flex-1 items-center justify-center gap-2 rounded-lg bg-impilo-500 py-2.5 text-sm font-medium text-white transition-colors hover:bg-impilo-600 disabled:cursor-not-allowed disabled:opacity-50"
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
              <div className="rounded-lg border-2 border-green-300 bg-white p-5">
                <div className="mb-4 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <TestTube2 className="h-5 w-5 text-green-600" />
                    <h3 className="font-medium text-gray-900">
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
                    className="text-xs text-gray-500 hover:text-gray-700"
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
                        className="rounded border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-green-500"
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
                        className="rounded border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-green-500"
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
                        className="rounded border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-green-500"
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
                        className="rounded border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-green-500"
                      />
                      <select
                        value={value.interpretation}
                        onChange={(e) => {
                          const next = [...resultValues];
                          next[index] = { ...value, interpretation: e.target.value };
                          setResultValues(next);
                        }}
                        className="rounded border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-green-500"
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
                    className="text-xs text-impilo-500 hover:text-impilo-700"
                  >
                    + Add row
                  </button>
                </div>
                <textarea
                  value={resultNotes}
                  onChange={(e) => setResultNotes(e.target.value)}
                  rows={2}
                  placeholder="Result notes..."
                  className="mb-3 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500 resize-none"
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
              <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
                <TestTube2 className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                <p className="text-sm text-gray-400">No lab orders found</p>
              </div>
            ) : (
              <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Order #</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Test Name</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Category</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Priority</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Status</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Ordered By</th>
                        <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Date</th>
                        <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {orders.map((order) => (
                        <tr key={order.id} className="transition-colors hover:bg-gray-50">
                          <td className="whitespace-nowrap px-4 py-3 font-medium text-gray-900">{order.attributes.orderNumber}</td>
                          <td className="whitespace-nowrap px-4 py-3 text-gray-900">{order.attributes.testName}</td>
                          <td className="whitespace-nowrap px-4 py-3 text-gray-500">{order.attributes.category}</td>
                          <td className="whitespace-nowrap px-4 py-3">
                            <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${PRIORITY_BADGE[order.attributes.priority] ?? "bg-gray-100 text-gray-600"}`}>
                              {order.attributes.priority}
                            </span>
                          </td>
                          <td className="whitespace-nowrap px-4 py-3">
                            <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_BADGE[order.attributes.status] ?? "bg-gray-100 text-gray-600"}`}>
                              {order.attributes.status}
                            </span>
                          </td>
                          <td className="whitespace-nowrap px-4 py-3 text-gray-500">{order.attributes.orderedByName}</td>
                          <td className="whitespace-nowrap px-4 py-3 text-gray-500">{new Date(order.attributes.createdAt).toLocaleDateString()}</td>
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
                                  className="rounded bg-red-50 px-2.5 py-1 text-xs font-medium text-red-600 transition-colors hover:bg-red-100"
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
                                className="rounded bg-purple-100 px-2.5 py-1 text-xs font-medium text-purple-700 transition-colors hover:bg-purple-200"
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
  );
}
