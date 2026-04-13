"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  ClipboardList,
  FileText,
  Loader2,
  Pill,
  TestTube2,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useLabOrders, type LabOrderResource } from "@/hooks/queries/useLabOrders";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient } from "@/lib/api-client";

const INTERPRETATION_STYLE: Record<string, string> = {
  NORMAL: "text-green-700 bg-green-50",
  ABNORMAL: "text-red-700 bg-red-50",
  CRITICAL: "text-red-700 bg-red-50 font-bold",
};

interface ResultValue {
  name: string;
  value: string;
  unit: string;
  referenceRange: string;
  interpretation: string;
}

function getResultValues(order: LabOrderResource): ResultValue[] {
  const attrs = order.attributes as Record<string, unknown>;
  const resultData = attrs.result_data;

  if (resultData && Array.isArray(resultData)) {
    return (resultData as ResultValue[]).map((value) => ({
      name: value.name ?? "",
      value: value.value ?? "",
      unit: value.unit ?? "",
      referenceRange: value.referenceRange ?? "",
      interpretation: value.interpretation ?? "NORMAL",
    }));
  }

  if (resultData && typeof resultData === "string") {
    try {
      const parsed = JSON.parse(resultData);
      if (Array.isArray(parsed)) {
        return parsed.map((value: ResultValue) => ({
          name: value.name ?? "",
          value: value.value ?? "",
          unit: value.unit ?? "",
          referenceRange: value.referenceRange ?? "",
          interpretation: value.interpretation ?? "NORMAL",
        }));
      }
    } catch {
      return [];
    }
  }

  return [
    {
      name: order.attributes.testName,
      value: "Pending result entry",
      unit: "",
      referenceRange: "-",
      interpretation: "NORMAL",
    },
  ];
}

function groupByDate(orders: LabOrderResource[]): Map<string, LabOrderResource[]> {
  const groups = new Map<string, LabOrderResource[]>();
  for (const order of orders) {
    const dateKey = order.attributes.resultedAt
      ? new Date(order.attributes.resultedAt).toLocaleDateString("en-ZA", {
          year: "numeric",
          month: "long",
          day: "numeric",
        })
      : "Unknown date";
    const existing = groups.get(dateKey) ?? [];
    existing.push(order);
    groups.set(dateKey, existing);
  }
  return groups;
}

export default function ResultsPage() {
  const params = useParams<{ patientId: string }>();
  const { patientId } = params;
  const facility = useFacilityStore((state) => state.facility);
  const queryClient = useQueryClient();
  const { data: encountersData } = useEncounters(patientId);
  const { data: ordersData, isLoading } = useLabOrders(patientId);
  const [acknowledgingId, setAcknowledgingId] = useState<string | null>(null);

  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const resultOrders = [...(ordersData?.data ?? [])]
    .filter((order) => order.attributes.status === "RESULTED" || order.attributes.status === "REVIEWED")
    .sort((left, right) => {
      const leftTime = left.attributes.resultedAt ? new Date(left.attributes.resultedAt).getTime() : 0;
      const rightTime = right.attributes.resultedAt ? new Date(right.attributes.resultedAt).getTime() : 0;
      return rightTime - leftTime;
    });
  const groupedResults = groupByDate(resultOrders);
  const criticalResults = resultOrders.filter((order) =>
    getResultValues(order).some((value) => value.interpretation === "CRITICAL")
  ).length;
  const abnormalResults = resultOrders.filter((order) =>
    getResultValues(order).some(
      (value) => value.interpretation === "ABNORMAL" || value.interpretation === "CRITICAL"
    )
  ).length;
  const awaitingReview = resultOrders.filter((order) => order.attributes.status === "RESULTED").length;
  const reviewedResults = resultOrders.filter((order) => order.attributes.status === "REVIEWED").length;
  const latestResultedAt = resultOrders[0]?.attributes.resultedAt
    ? new Date(resultOrders[0].attributes.resultedAt).toLocaleString()
    : null;

  async function handleAcknowledge(orderId: string) {
    setAcknowledgingId(orderId);
    try {
      await apiClient.post(`/internal/v1/lab-orders/${orderId}/acknowledge`);
      await queryClient.invalidateQueries({ queryKey: ["lab-orders"] });
    } finally {
      setAcknowledgingId(null);
    }
  }

  return (
    <EHRLayout>
      <PageShell title="Results">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
          </div>
        ) : (
          <div className="space-y-6">
            <ClinicalReviewHeader
              badge="Diagnostics Review"
              badgeIcon={TestTube2}
              title="Review incoming results, acknowledge what is ready, and carry the next decision back into the encounter"
              description="This workspace now keeps result review in the same clinical loop: see what needs attention, acknowledge completed studies in place, and branch back to orders, medications, or notes without losing facility or encounter context."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/summary`, label: "Summary", icon: Activity },
                { href: `/ehr/${patientId}/orders`, label: "Orders", icon: ClipboardList, tone: "secondary" },
                { href: `/ehr/${patientId}/medications`, label: "Medications", icon: Pill, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Awaiting review",
                  value: String(awaitingReview),
                  detail: "Resulted studies that still need clinician acknowledgement.",
                },
                {
                  label: "Critical flags",
                  value: String(criticalResults),
                  detail:
                    criticalResults > 0
                      ? "Critical values are present. Review and close the loop before leaving the workspace."
                      : "No critical result flags in the current result list.",
                },
                {
                  label: "Reviewed",
                  value: String(reviewedResults),
                  detail: "Results already acknowledged and retained in the longitudinal record.",
                },
              ]}
            />

            <div className="rounded-3xl border border-amber-200 bg-amber-50/80 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-amber-700">
                Review loop status
              </p>
              <p className="mt-2 text-sm text-amber-900">
                {criticalResults > 0
                  ? `${criticalResults} critical result${criticalResults === 1 ? "" : "s"} require immediate acknowledgement from this workspace.`
                  : awaitingReview > 0
                    ? `${awaitingReview} result${awaitingReview === 1 ? "" : "s"} are ready for acknowledgement here before you move back into notes or treatment changes.`
                    : "All visible results have already been acknowledged, and the remaining work is follow-through in notes, medications, or the patient summary."}
              </p>
              <p className="mt-1 text-xs text-amber-800">
                {latestResultedAt
                  ? `Latest result posted ${latestResultedAt}. ${abnormalResults} result${abnormalResults === 1 ? "" : "s"} show abnormal or critical interpretation.`
                  : "Result timestamps are not available yet for this patient."}
              </p>
            </div>

            {resultOrders.length === 0 ? (
              <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
                <TestTube2 className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                <p className="text-sm text-gray-400">No results available</p>
              </div>
            ) : (
              <>
                <div className="flex items-center gap-2">
                  <TestTube2 className="h-5 w-5 text-indigo-500" />
                  <h2 className="text-sm font-semibold text-gray-900">Results ({resultOrders.length})</h2>
                </div>

                {Array.from(groupedResults.entries()).map(([dateLabel, orders]) => (
                  <div key={dateLabel} className="space-y-3">
                    <h3 className="text-xs font-medium uppercase tracking-wider text-gray-500">
                      {dateLabel}
                    </h3>

                    {orders.map((order) => {
                      const resultValues = getResultValues(order);
                      const hasCritical = resultValues.some((value) => value.interpretation === "CRITICAL");
                      const hasAbnormal = resultValues.some(
                        (value) => value.interpretation === "ABNORMAL" || value.interpretation === "CRITICAL"
                      );
                      const attrs = order.attributes as Record<string, unknown>;
                      const resultNotes = attrs.result_notes as string | null;

                      return (
                        <div key={order.id} className="overflow-hidden rounded-lg border border-gray-200 bg-white">
                          <div className="flex items-center justify-between border-b border-gray-100 px-4 py-3">
                            <div className="flex items-center gap-3">
                              <TestTube2 className="h-4 w-4 text-indigo-500" />
                              <div>
                                <p className="text-sm font-medium text-gray-900">{order.attributes.testName}</p>
                                <p className="text-xs text-gray-500">
                                  #{order.attributes.orderNumber} · {order.attributes.category}
                                </p>
                              </div>
                            </div>
                            <div className="flex items-center gap-2">
                              {order.attributes.status === "RESULTED" ? (
                                <button
                                  type="button"
                                  onClick={() => handleAcknowledge(order.id)}
                                  disabled={acknowledgingId === order.id}
                                  className="inline-flex items-center gap-1 rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                                >
                                  {acknowledgingId === order.id ? (
                                    <>
                                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                      Acknowledging...
                                    </>
                                  ) : (
                                    "Acknowledge result"
                                  )}
                                </button>
                              ) : (
                                <span className="inline-flex items-center gap-1 text-xs font-medium text-slate-600">
                                  <CheckCircle2 className="h-3 w-3" />
                                  Reviewed
                                </span>
                              )}
                              {hasAbnormal ? (
                                <span className="inline-flex items-center gap-1 text-xs font-medium text-red-600">
                                  <AlertTriangle className="h-3 w-3" />
                                  {hasCritical ? "Critical" : "Abnormal"}
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 text-xs font-medium text-green-600">
                                  <CheckCircle2 className="h-3 w-3" />
                                  Normal
                                </span>
                              )}
                            </div>
                          </div>

                          <div className="flex flex-wrap gap-x-5 bg-gray-50 px-4 py-2 text-xs text-gray-500">
                            <span>Ordered: {order.attributes.orderedByName}</span>
                            <span>
                              Resulted: {order.attributes.resultedAt ? new Date(order.attributes.resultedAt).toLocaleDateString() : "-"}
                            </span>
                            {order.attributes.collectedAt && (
                              <span>Collected: {new Date(order.attributes.collectedAt).toLocaleDateString()}</span>
                            )}
                          </div>

                          <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                              <thead>
                                <tr className="border-b bg-gray-50">
                                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Test</th>
                                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Value</th>
                                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Unit</th>
                                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Reference</th>
                                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Status</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-gray-100">
                                {resultValues.map((value, index) => (
                                  <tr key={`${order.id}-${index}`} className="hover:bg-gray-50">
                                    <td className="px-4 py-2 text-gray-900">{value.name}</td>
                                    <td className="px-4 py-2 font-medium text-gray-900">{value.value}</td>
                                    <td className="px-4 py-2 text-gray-500">{value.unit}</td>
                                    <td className="px-4 py-2 text-gray-500">{value.referenceRange}</td>
                                    <td className="px-4 py-2">
                                      <span
                                        className={`px-2 py-0.5 text-xs font-medium rounded-full ${
                                          INTERPRETATION_STYLE[value.interpretation] ?? "text-gray-700 bg-gray-50"
                                        }`}
                                      >
                                        {value.interpretation}
                                      </span>
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>

                          {resultNotes && (
                            <div className="px-4 py-2 border-t border-gray-100 bg-gray-50">
                              <p className="text-xs text-gray-500">
                                <span className="font-medium">Notes:</span> {resultNotes}
                              </p>
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                ))}
              </>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
