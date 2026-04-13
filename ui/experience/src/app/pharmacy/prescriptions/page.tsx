"use client";

/**
 * Prescriptions List — View and manage prescriptions.
 * Route: /pharmacy/prescriptions
 */

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, ClipboardList, Loader2, AlertTriangle, FileText, Package, Pill, User } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { usePrescriptions } from "@/hooks/queries/usePharmacy";

const STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-700",
  DISPENSED: "bg-green-100 text-green-700",
  CANCELLED: "bg-red-100 text-red-700",
};

const STATUS_FILTERS = [
  { value: "", label: "All" },
  { value: "PENDING", label: "Pending" },
  { value: "DISPENSED", label: "Dispensed" },
  { value: "CANCELLED", label: "Cancelled" },
];

export default function PrescriptionsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();
  const [statusFilter, setStatusFilter] = useState("");
  const { data, isLoading, error } = usePrescriptions(
    statusFilter ? { status: statusFilter } : undefined,
  );
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");

  const prescriptions = data?.data ?? [];
  const pendingCount = prescriptions.filter((rx) => rx.attributes.status === "PENDING").length;
  const dispensedCount = prescriptions.filter((rx) => rx.attributes.status === "DISPENSED").length;
  const handoffParams = new URLSearchParams();

  if (patientId) handoffParams.set("patientId", patientId);
  if (encounterId) handoffParams.set("encounterId", encounterId);
  if (source) handoffParams.set("source", source);

  const withHandoff = (href: string) => {
    const query = handoffParams.toString();
    return query ? `${href}?${query}` : href;
  };

  const actions = [
    encounterId && patientId
      ? { href: `/ehr/${patientId}/encounter/${encounterId}`, label: "Encounter", icon: ClipboardList }
      : null,
    patientId
      ? { href: `/ehr/${patientId}`, label: "Chart", icon: User, tone: "secondary" as const }
      : null,
    patientId
      ? { href: `/ehr/${patientId}/medications`, label: "Medication Review", icon: FileText, tone: "secondary" as const }
      : null,
    { href: withHandoff("/pharmacy/dispense"), label: "Dispense", icon: Pill, tone: "secondary" as const },
    { href: withHandoff("/pharmacy/stock"), label: "Stock", icon: Package, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell title="Prescriptions" subtitle="View and manage patient prescriptions">
        <div className="mb-4">
          <Link
            href={withHandoff("/pharmacy")}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to pharmacy
          </Link>
        </div>

        {/* Filter */}
        <div className="mb-4 flex gap-2">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.value}
              onClick={() => setStatusFilter(f.value)}
              className={`px-3 py-1.5 text-xs font-medium rounded-full border transition-colors ${
                statusFilter === f.value
                  ? "bg-impilo-500 text-white border-impilo-500"
                  : "bg-white text-gray-600 border-gray-300 hover:border-gray-400"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading prescriptions...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load prescriptions</p>
          </div>
        ) : (
          <div className="space-y-6">
            <WorkflowHeader
              badge="Prescription continuity"
              badgeIcon={FileText}
              title="Keep prescription review tied to the patient, encounter, and downstream dispense loop instead of leaving pharmacy in a disconnected list."
              description="Prescriptions now surface the facility in scope, source workflow, and next medication handoff before staff filter or review orders."
              context={[
                { label: "Facility", value: facility?.name ?? "No facility selected" },
                { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Pharmacy workspace" },
                { label: "Filter", value: statusFilter || "All prescriptions" },
              ]}
              actions={actions}
              metrics={[
                {
                  label: "Pending",
                  value: String(pendingCount),
                  detail: "Prescriptions still waiting on dispensing or follow-through.",
                },
                {
                  label: "Dispensed",
                  value: String(dispensedCount),
                  detail: "Medication orders already completed and ready for continuity review.",
                },
                {
                  label: "Next action",
                  value: pendingCount > 0 ? "Review pending prescriptions" : "Check dispense or stock",
                  detail:
                    encounterId && patientId
                      ? "Use this list to continue the encounter-linked medication loop without losing chart context."
                      : "Review active prescriptions here, then move into dispense or stock as needed.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                Prescription loop status
              </p>
              <p className="mt-2 text-sm text-slate-800">
                {source === "discharge"
                  ? "This prescription view was reached from encounter outcome, so the loop here is confirming the active medication plan without losing the linked encounter or chart."
                  : "This workspace shows the broader prescription queue, but it should still connect cleanly back to medication review, dispensing, and the source encounter when context exists."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Filter prescriptions in place, then move into dispense, stock, the encounter, or the chart when the medication plan needs the next action.
              </p>
            </div>

            {prescriptions.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No prescriptions found</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Rx #</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Medication</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Dosage</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Prescriber</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {prescriptions.map((rx) => {
                      const attrs = rx.attributes as Record<string, unknown>;
                      const firstItem = rx.attributes.items?.[0];
                      const statusStyle = STATUS_STYLES[rx.attributes.status] ?? "bg-gray-100 text-gray-700";
                      return (
                        <tr key={rx.id} className="border-b last:border-b-0 hover:bg-gray-50">
                          <td className="px-4 py-3 font-mono text-xs text-gray-700">
                            {rx.id.slice(0, 8).toUpperCase()}
                          </td>
                          <td className="px-4 py-3 font-medium text-gray-900">
                            {(attrs.patientName as string) || rx.attributes.patientId}
                          </td>
                          <td className="px-4 py-3 text-gray-600">
                            {firstItem?.medication || "\u2014"}
                            {rx.attributes.items.length > 1 && (
                              <span className="text-gray-400 ml-1">+{rx.attributes.items.length - 1} more</span>
                            )}
                          </td>
                          <td className="px-4 py-3 text-gray-600">{firstItem?.dosage || "\u2014"}</td>
                          <td className="px-4 py-3">
                            <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                              {rx.attributes.status}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-gray-600">
                            {(attrs.prescriberName as string) || rx.attributes.prescriberId}
                          </td>
                          <td className="px-4 py-3 text-gray-600">
                            {(attrs.createdAt as string)
                              ? new Date(attrs.createdAt as string).toLocaleDateString()
                              : "\u2014"}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
