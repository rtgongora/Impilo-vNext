"use client";

/**
 * Pharmacy Dispense — View pending prescriptions and dispense them.
 * Route: /pharmacy/dispense | pageTitle: "Dispensing"
 */

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Pill, Loader2, CheckCircle2, ArrowLeft, ShieldAlert, ClipboardList, FileText, Package, User } from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface Prescription {
  id: string;
  medication_name: string;
  dosage: string;
  frequency: string;
  duration: string;
  quantity: number;
  status: string;
  prescribed_by: string;
  patient_name: string;
  patient_id: string;
  created_at: string;
}

type PrescriptionsResponse = ApiResponse<Prescription[]>;

/* ------------------------------------------------------------------ */
/*  Inline hook — fetches pending prescriptions                        */
/* ------------------------------------------------------------------ */

function usePendingPrescriptions() {
  return useQuery<PrescriptionsResponse>({
    queryKey: ["prescriptions", { status: "PENDING" }],
    queryFn: () =>
      apiClient.get<PrescriptionsResponse>(
        `/internal/v1/pharmacy/prescriptions?status=PENDING`,
      ),
  });
}

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function PharmacyDispensePage() {
  const queryClient = useQueryClient();
  const { isDispenser } = useRoleGroup();
  const { user } = useAuthStore();
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();

  const { data: prescriptionsData, isLoading, isError } =
    usePendingPrescriptions();
  const prescriptions = prescriptionsData?.data ?? [];
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");

  const [dispensingId, setDispensingId] = useState<string | null>(null);
  const [dispensedIds, setDispensedIds] = useState<Set<string>>(new Set());
  const [dispenseError, setDispenseError] = useState<string | null>(null);
  const handoffParams = new URLSearchParams();

  if (patientId) handoffParams.set("patientId", patientId);
  if (encounterId) handoffParams.set("encounterId", encounterId);
  if (source) handoffParams.set("source", source);

  const withHandoff = (href: string) => {
    const query = handoffParams.toString();
    return query ? `${href}?${query}` : href;
  };

  const completedCount = dispensedIds.size;
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
    { href: withHandoff("/pharmacy/prescriptions"), label: "Prescriptions", icon: FileText, tone: "secondary" as const },
    { href: withHandoff("/pharmacy/stock"), label: "Stock", icon: Package, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  async function handleDispense(prescriptionId: string) {
    setDispensingId(prescriptionId);
    setDispenseError(null);

    try {
      await apiClient.post("/internal/v1/pharmacy/dispense", {
        prescription_id: prescriptionId,
        dispensed_by: user?.id ?? "system",
      });

      setDispensedIds((prev) => new Set(prev).add(prescriptionId));
      queryClient.invalidateQueries({
        queryKey: ["prescriptions", { status: "PENDING" }],
      });
    } catch {
      setDispenseError(prescriptionId);
    } finally {
      setDispensingId(null);
    }
  }

  return (
    <AppLayout>
      <PageShell title="Dispensing" subtitle="Review and dispense pending prescriptions">
        {!isDispenser ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <ShieldAlert className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm font-medium">Access Denied</p>
            <p className="text-gray-500 text-xs mt-1">Only pharmacists can access the dispensing workflow.</p>
          </div>
        ) : (<>
        {/* Back link */}
        <div className="mb-4">
          <Link
            href={withHandoff("/pharmacy")}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to pharmacy
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">
              Loading pending prescriptions...
            </span>
          </div>
        ) : isError ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-12 text-center">
            <p className="text-red-600 text-sm">
              Failed to load prescriptions. Please try again later.
            </p>
          </div>
        ) : (
          <div className="space-y-6">
            <WorkflowHeader
              badge="Dispense continuity"
              badgeIcon={Pill}
              title="Keep dispensing tied to the patient, encounter, and prescription loop so medication fulfillment does not drift away from the source clinical episode."
              description="Dispense now surfaces the facility in scope, source workflow, and the next medication handoff before pharmacists complete fulfillment."
              context={[
                { label: "Facility", value: facility?.name ?? "No facility selected" },
                { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Pharmacy workspace" },
                { label: "Pending queue", value: `${prescriptions.length} prescription${prescriptions.length === 1 ? "" : "s"}` },
              ]}
              actions={actions}
              metrics={[
                {
                  label: "Ready to dispense",
                  value: String(prescriptions.length),
                  detail: "Pending prescriptions still waiting on fulfillment.",
                },
                {
                  label: "Completed this session",
                  value: String(completedCount),
                  detail: "Prescriptions dispensed in place without losing continuity.",
                },
                {
                  label: "Next action",
                  value: prescriptions.length > 0 ? "Dispense pending meds" : "Review prescriptions or stock",
                  detail:
                    encounterId && patientId
                      ? "Complete the encounter-linked dispense step here, then move back to chart or medications if clarification is needed."
                      : "Dispense from the current queue, then use prescriptions or stock for the next medication action.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                Dispense loop status
              </p>
              <p className="mt-2 text-sm text-slate-800">
                {source === "discharge"
                  ? "This dispense view was reached from encounter outcome, so the loop here is fulfilling discharge medications without losing the linked encounter, chart, or prescription context."
                  : "This workspace runs the broader dispense queue, but it should still connect cleanly back to prescriptions, stock, and the source encounter when context exists."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Dispense in place below, then move into prescriptions, stock, the encounter, or the chart when the medication handoff needs the next action.
              </p>
            </div>

            {/* Header */}
            <div className="flex items-center gap-2">
              <Pill className="w-5 h-5 text-indigo-500" />
              <h2 className="text-lg font-semibold text-gray-900">
                Pending Prescriptions ({prescriptions.length})
              </h2>
            </div>

            {/* Prescription Cards */}
            {prescriptions.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Pill className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">
                  No pending prescriptions to dispense
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-4">
                {prescriptions.map((rx) => {
                  const isDispensing = dispensingId === rx.id;
                  const isDispensed = dispensedIds.has(rx.id);
                  const hasError = dispenseError === rx.id;

                  return (
                    <div
                      key={rx.id}
                      className="bg-white rounded-lg border border-gray-200 p-4 hover:shadow-sm transition-shadow"
                    >
                      <div className="flex items-start justify-between">
                        <div className="flex items-center gap-2">
                          <Pill className="w-4 h-4 text-indigo-500 mt-0.5" />
                          <div>
                            <p className="text-sm font-semibold text-gray-900">
                              {rx.medication_name}
                            </p>
                            <p className="text-xs text-gray-500 mt-0.5">
                              {rx.dosage} &middot; {rx.frequency} &middot;{" "}
                              {rx.duration}
                            </p>
                          </div>
                        </div>
                        <span className="px-2.5 py-0.5 text-xs font-medium rounded-full bg-yellow-100 text-yellow-700">
                          {rx.status}
                        </span>
                      </div>

                      <div className="mt-3 flex items-center gap-4 text-xs text-gray-500">
                        <span>
                          Patient:{" "}
                          <span className="font-medium">{rx.patient_name}</span>
                        </span>
                        <span>
                          Qty: <span className="font-medium">{rx.quantity}</span>
                        </span>
                        <span>
                          Prescribed by:{" "}
                          <span className="font-medium">{rx.prescribed_by}</span>
                        </span>
                        <span>
                          {new Date(rx.created_at).toLocaleDateString()}
                        </span>
                      </div>

                      <div className="mt-3 flex items-center gap-3">
                        {isDispensed ? (
                          <div className="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-green-700">
                            <CheckCircle2 className="w-4 h-4" />
                            Dispensed
                          </div>
                        ) : (
                          <button
                            onClick={() => handleDispense(rx.id)}
                            disabled={isDispensing}
                            className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                          >
                            {isDispensing ? (
                              <>
                                <Loader2 className="w-4 h-4 animate-spin" />
                                Dispensing...
                              </>
                            ) : (
                              <>
                                <Pill className="w-4 h-4" />
                                Dispense
                              </>
                            )}
                          </button>
                        )}
                        {hasError && (
                          <p className="text-sm text-red-600">
                            Failed to dispense. Please try again.
                          </p>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
        </>)}
      </PageShell>
    </AppLayout>
  );
}
