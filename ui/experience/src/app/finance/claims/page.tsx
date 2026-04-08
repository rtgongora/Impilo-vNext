"use client";

/**
 * Claims List — Insurance claims table with status tracking.
 * Route: /finance/claims | pageTitle: "Claims"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, FileText, AlertCircle, ClipboardList, CreditCard, Receipt, User } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface ClaimResource {
  id: string;
  type: "claim";
  attributes: {
    claimNumber: string;
    patient: string;
    scheme: string;
    amount: number;
    currency: string;
    status: string;
    date: string;
    [key: string]: unknown;
  };
}

type ClaimsResponse = ApiResponse<ClaimResource[]>;

function useClaims() {
  return useQuery<ClaimsResponse>({
    queryKey: ["finance-claims"],
    queryFn: () => apiClient.get<ClaimsResponse>("/internal/v1/finance/claims"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  SUBMITTED: "bg-blue-100 text-blue-700",
  ADJUDICATED: "bg-yellow-100 text-yellow-700",
  PAID: "bg-green-100 text-green-700",
  REJECTED: "bg-red-100 text-red-700",
};

export default function ClaimsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();
  const { data, isLoading, error } = useClaims();
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");

  const claims = data?.data ?? [];
  const submittedCount = claims.filter((claim) =>
    ["SUBMITTED", "ADJUDICATED"].includes(claim.attributes.status)
  ).length;
  const paidCount = claims.filter((claim) => claim.attributes.status === "PAID").length;
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
    { href: withHandoff("/finance/payments"), label: "Payments", icon: CreditCard, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell
        title="Claims"
        subtitle="Submit and track insurance claims"
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
            <p className="text-red-600 text-sm">Failed to load claims</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading claims...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <WorkflowHeader
              badge="Claims continuity"
              badgeIcon={FileText}
              title="Keep payer follow-through connected to the encounter and bill that generated the claim instead of isolating adjudication work from clinical context."
              description="Claims now surface the facility in scope, source workflow, and next reimbursement move before users open a claim."
              context={[
                { label: "Facility", value: facility?.name ?? "No facility selected" },
                { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Finance workspace" },
                { label: "Claim queue", value: `${claims.length} claim${claims.length === 1 ? "" : "s"}` },
              ]}
              actions={actions}
              metrics={[
                {
                  label: "Open claims",
                  value: String(submittedCount),
                  detail: "Submitted or adjudicating claims that still need payer follow-through.",
                },
                {
                  label: "Paid claims",
                  value: String(paidCount),
                  detail: "Claims already settled and ready for downstream payment reconciliation.",
                },
                {
                  label: "Next action",
                  value: submittedCount > 0 ? "Review claim status" : "Reconcile payments",
                  detail:
                    encounterId && patientId
                      ? "Open the linked claim here without losing the encounter and chart context."
                      : "Use claims for adjudication follow-through, then move to payments when settlement arrives.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                Claim loop status
              </p>
              <p className="mt-2 text-sm text-slate-800">
                {source === "discharge"
                  ? "This claims view was reached from encounter outcome, so the loop here is keeping payer submission and adjudication tied to the same encounter and patient."
                  : "This workspace shows the broader payer queue, but claims still need to stay connected to billing, payments, and the source encounter when context exists."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Open a claim below for adjudication detail, or move back to billing, payments, the encounter, or the chart when payer follow-through needs clarification.
              </p>
            </div>

            {claims.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No claims submitted</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Claim #</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Scheme</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Amount</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {claims.map((claim) => {
                      const statusStyle =
                        STATUS_STYLES[claim.attributes.status] ?? "bg-gray-100 text-gray-600";
                      return (
                        <tr key={claim.id} className="hover:bg-gray-50 transition-colors">
                          <td className="px-4 py-3">
                            <Link
                              href={withHandoff(`/finance/claims/${claim.id}`)}
                              className="font-medium text-blue-600 hover:text-blue-800"
                            >
                              {claim.attributes.claimNumber}
                            </Link>
                          </td>
                          <td className="px-4 py-3 text-gray-600">
                            {claim.attributes.patient}
                          </td>
                          <td className="px-4 py-3 text-gray-600">
                            <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-purple-100 text-purple-700">
                              {claim.attributes.scheme}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-right font-mono text-gray-900">
                            {claim.attributes.currency}{" "}
                            {claim.attributes.amount.toLocaleString(undefined, {
                              minimumFractionDigits: 2,
                            })}
                          </td>
                          <td className="px-4 py-3">
                            <span
                              className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                            >
                              {claim.attributes.status}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                            {new Date(claim.attributes.date).toLocaleDateString()}
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
