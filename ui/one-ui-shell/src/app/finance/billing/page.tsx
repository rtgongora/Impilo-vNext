"use client";

/**
 * Billing Dashboard — Invoice table with status tracking.
 * Route: /finance/billing | pageTitle: "Billing"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, ClipboardList, Loader2, Receipt, AlertCircle, User } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { ServiceAccessPaymentPanel } from "@/components/billing/ServiceAccessPaymentPanel";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface BillingResource {
  id: string;
  type: "invoice";
  attributes: {
    invoiceNumber: string;
    patient: string;
    amount: number;
    currency: string;
    status: string;
    date: string;
    [key: string]: unknown;
  };
}

type BillingResponse = ApiResponse<BillingResource[]>;

function useBilling() {
  return useQuery<BillingResponse>({
    queryKey: ["finance-billing"],
    queryFn: () => apiClient.get<BillingResponse>("/internal/v1/finance/billing"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  DRAFT: "bg-neutral-100 text-muted-foreground",
  ACCUMULATING: "bg-yellow-100 text-yellow-700",
  APPROVAL_PENDING: "bg-orange-100 text-orange-700",
  APPROVED: "bg-primary-soft text-primary",
  FINAL: "bg-green-100 text-green-700",
  VOID: "bg-red-100 text-danger",
  ADJUSTED: "bg-purple-100 text-warning-foreground",
};

export default function BillingPage() {
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();
  const { data, isLoading, error } = useBilling();
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");

  const invoices = data?.data ?? [];
  const draftCount = invoices.filter((invoice) =>
    ["DRAFT", "ACCUMULATING", "APPROVAL_PENDING"].includes(invoice.attributes.status)
  ).length;
  const finalCount = invoices.filter((invoice) => invoice.attributes.status === "FINAL").length;
  const actions = [
    encounterId && patientId
      ? { href: `/ehr/${patientId}/encounter/${encounterId}`, label: "Encounter", icon: ClipboardList }
      : null,
    patientId
      ? { href: `/ehr/${patientId}`, label: "Chart", icon: User, tone: "secondary" as const }
      : null,
    { href: "/finance", label: "Finance Home", icon: Receipt, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell
        title="Billing"
        subtitle="Manage invoices and billing records. Billing may occur before, during, or after care; costing runs continuously."
      >
        <div className="mb-4">
          <Link
            href="/finance"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to finance
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load billing records</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading billing records...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <WorkflowHeader
              badge="Billing follow-through"
              badgeIcon={Receipt}
              title="Keep newly created bills connected to the originating encounter and patient context instead of dropping finance staff into an unscoped invoice table."
              description="Billing now surfaces the current facility, any incoming encounter handoff, and the next revenue-cycle move before users open a bill."
              context={[
                { label: "Facility", value: facility?.name ?? "No facility selected" },
                { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Finance workspace" },
                { label: "Invoice list", value: `${invoices.length} bill${invoices.length === 1 ? "" : "s"}` },
              ]}
              actions={actions}
              metrics={[
                {
                  label: "Needs finance action",
                  value: String(draftCount),
                  detail: "Draft, accumulating, or approval-pending bills still moving through the cycle.",
                },
                {
                  label: "Final bills",
                  value: String(finalCount),
                  detail: "Bills ready for invoice or payment handling.",
                },
                {
                  label: "Next action",
                  value: encounterId ? "Open linked bill" : "Review queue",
                  detail:
                    encounterId && patientId
                      ? "Use this list to continue the encounter-linked finance handoff without losing chart context."
                      : "Open a bill below to continue approval, invoicing, payment, or refund work.",
                },
              ]}
            />

            <ServiceAccessPaymentPanel encounterId={encounterId} patientId={patientId} />

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                Billing loop status
              </p>
              <p className="mt-2 text-sm text-foreground">
                {source === "discharge"
                  ? "This billing view was reached from encounter outcome, so the loop here is confirming the right bill and continuing approval or payment steps without losing patient context."
                  : "This workspace shows the broader finance queue, but each bill still needs to resolve cleanly back to its encounter and patient context."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Open a bill below for the detailed workflow, or move back to the encounter or patient chart when finance follow-through needs clinical clarification.
              </p>
            </div>

            {invoices.length === 0 ? (
              <div className="bg-card rounded-lg border border-border p-12 text-center">
                <Receipt className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
                <p className="text-muted-foreground text-sm">No billing records</p>
              </div>
            ) : (
              <div className="bg-card rounded-lg border border-border overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-background">
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Invoice #</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Patient</th>
                      <th className="text-right px-4 py-3 font-medium text-muted-foreground">Amount</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Date</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {invoices.map((invoice) => {
                      const statusStyle =
                        STATUS_STYLES[invoice.attributes.status] ?? "bg-neutral-100 text-muted-foreground";
                      const billingHref =
                        patientId && encounterId
                          ? `/finance/billing/${invoice.id}?patientId=${patientId}&encounterId=${encounterId}&source=${source ?? "billing"}`
                          : `/finance/billing/${invoice.id}`;
                      return (
                        <tr key={invoice.id} className="hover:bg-background transition-colors">
                          <td className="px-4 py-3">
                            <Link
                              href={billingHref}
                              className="font-medium text-primary hover:text-primary-hover"
                            >
                              {invoice.attributes.invoiceNumber}
                            </Link>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">
                            {invoice.attributes.patient}
                          </td>
                          <td className="px-4 py-3 text-right font-mono text-foreground">
                            {invoice.attributes.currency}{" "}
                            {invoice.attributes.amount.toLocaleString(undefined, {
                              minimumFractionDigits: 2,
                            })}
                          </td>
                          <td className="px-4 py-3">
                            <span
                              className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                            >
                              {invoice.attributes.status}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                            {new Date(invoice.attributes.date).toLocaleDateString()}
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
