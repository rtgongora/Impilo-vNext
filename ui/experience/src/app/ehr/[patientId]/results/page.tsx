"use client";

/**
 * Results — View lab results for a patient.
 * Route: /ehr/[patientId]/results | pageTitle: "Results"
 * Displays only RESULTED lab orders, grouped by result date.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { TestTube2, Loader2, ArrowLeft, AlertTriangle } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useLabOrders,
  type LabOrderResource,
} from "@/hooks/queries/useLabOrders";

/** Interpretation colour classes */
const INTERPRETATION_STYLE: Record<string, string> = {
  NORMAL: "text-green-700 bg-green-50",
  ABNORMAL: "text-red-700 bg-red-50",
  CRITICAL: "text-red-700 bg-red-50 font-bold",
};

function interpretationClass(interpretation: string): string {
  return INTERPRETATION_STYLE[interpretation] ?? "text-gray-700 bg-gray-50";
}

/** Simulated result values attached to a resulted order.
 *  In production these would come from a dedicated results API. */
interface ResultValue {
  name: string;
  value: string;
  unit: string;
  referenceRange: string;
  interpretation: "NORMAL" | "ABNORMAL" | "CRITICAL";
}

function getResultValues(order: LabOrderResource): ResultValue[] {
  // Placeholder: derive sample result values from order metadata.
  // Replace with a real API call when available.
  const code = order.attributes.testCode?.toUpperCase() ?? "";
  if (code === "CBC") {
    return [
      { name: "WBC", value: "7.2", unit: "x10^3/uL", referenceRange: "4.5-11.0", interpretation: "NORMAL" },
      { name: "RBC", value: "4.8", unit: "x10^6/uL", referenceRange: "4.5-5.5", interpretation: "NORMAL" },
      { name: "Hemoglobin", value: "14.1", unit: "g/dL", referenceRange: "13.5-17.5", interpretation: "NORMAL" },
    ];
  }
  if (code === "BMP" || code === "CMP") {
    return [
      { name: "Glucose", value: "210", unit: "mg/dL", referenceRange: "70-100", interpretation: "ABNORMAL" },
      { name: "Creatinine", value: "1.0", unit: "mg/dL", referenceRange: "0.7-1.3", interpretation: "NORMAL" },
    ];
  }
  return [
    { name: order.attributes.testName, value: "See report", unit: "", referenceRange: "-", interpretation: "NORMAL" },
  ];
}

/** Group resulted orders by their resultedAt date string (date portion only). */
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

  // Filter to only RESULTED orders
  const resultedOrders = (ordersData?.data ?? []).filter(
    (o) => o.attributes.status === "RESULTED",
  );

  const groupedResults = groupByDate(resultedOrders);

  return (
    <EHRLayout>
      <PageShell title="Results">
        {/* Back link */}
        <div className="mb-4">
          <Link
            href={`/ehr/${patientId}`}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to patient chart
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading results...</span>
          </div>
        ) : resultedOrders.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <TestTube2 className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No results available</p>
          </div>
        ) : (
          <div className="space-y-8">
            {/* Header */}
            <div className="flex items-center gap-2">
              <TestTube2 className="w-5 h-5 text-indigo-500" />
              <h2 className="text-lg font-semibold text-gray-900">
                Results ({resultedOrders.length})
              </h2>
            </div>

            {/* Results grouped by date */}
            {Array.from(groupedResults.entries()).map(([dateLabel, orders]) => (
              <div key={dateLabel} className="space-y-4">
                <h3 className="text-sm font-medium text-gray-500 uppercase tracking-wider">
                  {dateLabel}
                </h3>

                {orders.map((order) => {
                  const resultValues = getResultValues(order);
                  const hasCritical = resultValues.some(
                    (rv) => rv.interpretation === "CRITICAL",
                  );
                  const hasAbnormal = resultValues.some(
                    (rv) =>
                      rv.interpretation === "ABNORMAL" ||
                      rv.interpretation === "CRITICAL",
                  );

                  return (
                    <div
                      key={order.id}
                      className="bg-white rounded-lg border border-gray-200 overflow-hidden"
                    >
                      {/* Card header */}
                      <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <TestTube2 className="w-5 h-5 text-indigo-500" />
                          <div>
                            <p className="font-medium text-gray-900">
                              {order.attributes.testName}
                            </p>
                            <p className="text-xs text-gray-500 mt-0.5">
                              Order #{order.attributes.orderNumber} &middot;{" "}
                              {order.attributes.category}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-3">
                          {hasAbnormal && (
                            <span className="inline-flex items-center gap-1 text-xs font-medium text-red-600">
                              <AlertTriangle className="w-3.5 h-3.5" />
                              {hasCritical ? "Critical" : "Abnormal"}
                            </span>
                          )}
                          <span className="px-2.5 py-0.5 text-xs font-medium rounded-full bg-green-100 text-green-700">
                            RESULTED
                          </span>
                        </div>
                      </div>

                      {/* Metadata row */}
                      <div className="px-5 py-3 bg-gray-50 flex flex-wrap gap-x-6 gap-y-1 text-xs text-gray-500">
                        <span>
                          <span className="font-medium text-gray-600">Ordered by:</span>{" "}
                          {order.attributes.orderedByName}
                        </span>
                        <span>
                          <span className="font-medium text-gray-600">Result date:</span>{" "}
                          {order.attributes.resultedAt
                            ? new Date(order.attributes.resultedAt).toLocaleDateString()
                            : "-"}
                        </span>
                        <span>
                          <span className="font-medium text-gray-600">Collected:</span>{" "}
                          {order.attributes.collectedAt
                            ? new Date(order.attributes.collectedAt).toLocaleDateString()
                            : "-"}
                        </span>
                      </div>

                      {/* Result values table */}
                      <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="border-b border-gray-200 bg-gray-50">
                              <th className="px-5 py-2.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                Test
                              </th>
                              <th className="px-5 py-2.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                Value
                              </th>
                              <th className="px-5 py-2.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                Unit
                              </th>
                              <th className="px-5 py-2.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                Reference Range
                              </th>
                              <th className="px-5 py-2.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                Interpretation
                              </th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-200">
                            {resultValues.map((rv, idx) => (
                              <tr key={idx} className="hover:bg-gray-50 transition-colors">
                                <td className="px-5 py-3 text-gray-900 whitespace-nowrap">
                                  {rv.name}
                                </td>
                                <td className="px-5 py-3 text-gray-900 font-medium whitespace-nowrap">
                                  {rv.value}
                                </td>
                                <td className="px-5 py-3 text-gray-500 whitespace-nowrap">
                                  {rv.unit}
                                </td>
                                <td className="px-5 py-3 text-gray-500 whitespace-nowrap">
                                  {rv.referenceRange}
                                </td>
                                <td className="px-5 py-3 whitespace-nowrap">
                                  <span
                                    className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${interpretationClass(rv.interpretation)}`}
                                  >
                                    {rv.interpretation}
                                  </span>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
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
