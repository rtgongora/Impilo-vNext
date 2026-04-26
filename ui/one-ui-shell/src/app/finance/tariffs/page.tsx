"use client";

/**
 * Tariff Management — Service tariff schedule table.
 * Route: /finance/tariffs | pageTitle: "Tariff Management"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, BookOpen, AlertCircle, ClipboardList, CreditCard, FileText, Receipt, User } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface TariffResource {
  id: string;
  type: "tariff";
  attributes: {
    serviceCode: string;
    description: string;
    tariffAmount: number;
    currency: string;
    effectiveDate: string;
    status: string;
    [key: string]: unknown;
  };
}

type TariffsResponse = ApiResponse<TariffResource[]>;

function useTariffs() {
  return useQuery<TariffsResponse>({
    queryKey: ["finance-tariffs"],
    queryFn: () => apiClient.get<TariffsResponse>("/internal/v1/finance/tariffs"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-gray-100 text-gray-600",
  DRAFT: "bg-yellow-100 text-yellow-700",
  SUPERSEDED: "bg-impilo-100 text-impilo-600",
};

export default function TariffsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();
  const { data, isLoading, error } = useTariffs();
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");

  const tariffs = data?.data ?? [];
  const activeCount = tariffs.filter((tariff) => tariff.attributes.status === "ACTIVE").length;
  const draftCount = tariffs.filter((tariff) => tariff.attributes.status === "DRAFT").length;
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
    { href: withHandoff("/finance/billing"), label: "Billing", icon: Receipt, tone: "secondary" as const },
    { href: withHandoff("/finance/claims"), label: "Claims", icon: FileText, tone: "secondary" as const },
    { href: withHandoff("/finance/payments"), label: "Payments", icon: CreditCard, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell
        title="Tariff Management"
        subtitle="Manage service tariff schedules"
      >
        <div className="mb-4">
          <Link
            href={withHandoff("/finance")}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to finance
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load tariffs</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading tariffs...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <WorkflowHeader
              badge="Tariff continuity"
              badgeIcon={BookOpen}
              title="Keep tariff review tied to the active facility and downstream billing flow so pricing reference work does not drift away from the encounter handoff."
              description="Tariffs now surface facility context, incoming workflow source, and the next revenue-cycle move before staff review schedule entries."
              context={[
                { label: "Facility", value: facility?.name ?? "No facility selected" },
                { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Finance workspace" },
                { label: "Tariff schedule", value: `${tariffs.length} tariff${tariffs.length === 1 ? "" : "s"}` },
              ]}
              actions={actions}
              metrics={[
                {
                  label: "Active tariffs",
                  value: String(activeCount),
                  detail: "Current billable reference entries supporting pricing across finance workflows.",
                },
                {
                  label: "Draft tariffs",
                  value: String(draftCount),
                  detail: "Pricing entries still awaiting finalization before they should drive billing.",
                },
                {
                  label: "Next action",
                  value: draftCount > 0 ? "Review draft pricing" : "Return to finance flow",
                  detail:
                    encounterId && patientId
                      ? "Use this reference view without losing the linked encounter or chart context."
                      : "Review tariff status here, then move back to billing, claims, or payments for the next finance action.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                Tariff loop status
              </p>
              <p className="mt-2 text-sm text-slate-800">
                {source === "discharge"
                  ? "This tariff view was reached from encounter outcome, so the loop here is confirming the right pricing reference without losing the linked encounter, chart, or finance handoff."
                  : "This workspace holds the broader pricing schedule, but tariff review should still connect cleanly back to billing, claims, and payments when context exists."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Review pricing in place, then move back to billing, claims, payments, the encounter, or the chart when the next action depends on the tariff reference.
              </p>
            </div>

            {tariffs.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <BookOpen className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No tariffs configured</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Service Code</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Description</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Tariff Amount</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Effective Date</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {tariffs.map((tariff) => {
                      const statusStyle =
                        STATUS_STYLES[tariff.attributes.status] ?? "bg-gray-100 text-gray-600";
                      return (
                        <tr key={tariff.id} className="hover:bg-gray-50 transition-colors">
                          <td className="px-4 py-3 font-mono text-xs text-gray-900">
                            {tariff.attributes.serviceCode}
                          </td>
                          <td className="px-4 py-3 text-gray-600">
                            {tariff.attributes.description}
                          </td>
                          <td className="px-4 py-3 text-right font-mono text-gray-900">
                            {tariff.attributes.currency}{" "}
                            {tariff.attributes.tariffAmount.toLocaleString(undefined, {
                              minimumFractionDigits: 2,
                            })}
                          </td>
                          <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                            {new Date(tariff.attributes.effectiveDate).toLocaleDateString()}
                          </td>
                          <td className="px-4 py-3">
                            <span
                              className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                            >
                              {tariff.attributes.status}
                            </span>
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
