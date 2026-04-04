"use client";

/**
 * Results — View lab results for a patient.
 * Route: /ehr/[patientId]/results | pageTitle: "Results"
 *
 * Displays RESULTED lab orders with real result data from the BFF.
 * Result values come from the result_data JSONB column, populated
 * when results are entered via POST /lab-orders/{id}/result.
 */

import { useParams } from "next/navigation";
import { TestTube2, Loader2, AlertTriangle, CheckCircle2 } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useLabOrders,
  type LabOrderResource,
} from "@/hooks/queries/useLabOrders";

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

  // Real result data from BFF
  if (resultData && Array.isArray(resultData)) {
    return (resultData as ResultValue[]).map((rv) => ({
      name: rv.name ?? "",
      value: rv.value ?? "",
      unit: rv.unit ?? "",
      referenceRange: rv.referenceRange ?? "",
      interpretation: rv.interpretation ?? "NORMAL",
    }));
  }

  // If result_data is a JSON string, try to parse it
  if (resultData && typeof resultData === "string") {
    try {
      const parsed = JSON.parse(resultData);
      if (Array.isArray(parsed)) {
        return parsed.map((rv: ResultValue) => ({
          name: rv.name ?? "",
          value: rv.value ?? "",
          unit: rv.unit ?? "",
          referenceRange: rv.referenceRange ?? "",
          interpretation: rv.interpretation ?? "NORMAL",
        }));
      }
    } catch {
      // Not valid JSON
    }
  }

  // Fallback: show generic entry when no structured result data exists
  return [
    {
      name: order.attributes.testName,
      value: "Pending result entry",
      unit: "",
      referenceRange: "—",
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
      : "Unknown Date";
    const existing = groups.get(dateKey) ?? [];
    existing.push(order);
    groups.set(dateKey, existing);
  }
  return groups;
}

export default function ResultsPage() {
  const params = useParams<{ patientId: string }>();
  const { patientId } = params;

  const { data: ordersData, isLoading } = useLabOrders(patientId);

  const resultedOrders = (ordersData?.data ?? []).filter(
    (o) => o.attributes.status === "RESULTED"
  );
  const groupedResults = groupByDate(resultedOrders);

  return (
    <EHRLayout>
      <PageShell title="Results">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : resultedOrders.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <TestTube2 className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No results available</p>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center gap-2">
              <TestTube2 className="w-5 h-5 text-indigo-500" />
              <h2 className="text-sm font-semibold text-gray-900">
                Results ({resultedOrders.length})
              </h2>
            </div>

            {Array.from(groupedResults.entries()).map(([dateLabel, orders]) => (
              <div key={dateLabel} className="space-y-3">
                <h3 className="text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {dateLabel}
                </h3>

                {orders.map((order) => {
                  const resultValues = getResultValues(order);
                  const hasCritical = resultValues.some((rv) => rv.interpretation === "CRITICAL");
                  const hasAbnormal = resultValues.some(
                    (rv) => rv.interpretation === "ABNORMAL" || rv.interpretation === "CRITICAL"
                  );
                  const attrs = order.attributes as Record<string, unknown>;
                  const resultNotes = attrs.result_notes as string | null;

                  return (
                    <div key={order.id} className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                      {/* Header */}
                      <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <TestTube2 className="w-4 h-4 text-indigo-500" />
                          <div>
                            <p className="text-sm font-medium text-gray-900">{order.attributes.testName}</p>
                            <p className="text-xs text-gray-500">
                              #{order.attributes.orderNumber} · {order.attributes.category}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          {hasAbnormal && (
                            <span className="inline-flex items-center gap-1 text-xs font-medium text-red-600">
                              <AlertTriangle className="w-3 h-3" />
                              {hasCritical ? "Critical" : "Abnormal"}
                            </span>
                          )}
                          {!hasAbnormal && (
                            <span className="inline-flex items-center gap-1 text-xs font-medium text-green-600">
                              <CheckCircle2 className="w-3 h-3" />
                              Normal
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Metadata */}
                      <div className="px-4 py-2 bg-gray-50 flex flex-wrap gap-x-5 text-xs text-gray-500">
                        <span>Ordered: {order.attributes.orderedByName}</span>
                        <span>Resulted: {order.attributes.resultedAt ? new Date(order.attributes.resultedAt).toLocaleDateString() : "—"}</span>
                        {order.attributes.collectedAt && (
                          <span>Collected: {new Date(order.attributes.collectedAt).toLocaleDateString()}</span>
                        )}
                      </div>

                      {/* Result values */}
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
                            {resultValues.map((rv, idx) => (
                              <tr key={idx} className="hover:bg-gray-50">
                                <td className="px-4 py-2 text-gray-900">{rv.name}</td>
                                <td className="px-4 py-2 font-medium text-gray-900">{rv.value}</td>
                                <td className="px-4 py-2 text-gray-500">{rv.unit}</td>
                                <td className="px-4 py-2 text-gray-500">{rv.referenceRange}</td>
                                <td className="px-4 py-2">
                                  <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${
                                    INTERPRETATION_STYLE[rv.interpretation] ?? "text-gray-700 bg-gray-50"
                                  }`}>{rv.interpretation}</span>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>

                      {/* Result notes */}
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
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
