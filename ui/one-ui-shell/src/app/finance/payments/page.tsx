"use client";

/**
 * Payments — Payment records table with status tracking.
 * Route: /finance/payments | pageTitle: "Payments"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, CreditCard, AlertCircle, ClipboardList, Receipt, FileText, User } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface PaymentResource {
  id: string;
  type: "payment";
  attributes: {
    paymentNumber: string;
    payer: string;
    amount: number;
    currency: string;
    method: string;
    status: string;
    date: string;
    [key: string]: unknown;
  };
}

type PaymentsResponse = ApiResponse<PaymentResource[]>;

function usePayments() {
  return useQuery<PaymentsResponse>({
    queryKey: ["finance-payments"],
    queryFn: () => apiClient.get<PaymentsResponse>("/internal/v1/finance/payments"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  PAID: "bg-green-100 text-green-700",
  FAILED: "bg-red-100 text-danger",
  CANCELLED: "bg-neutral-100 text-muted-foreground",
};

export default function PaymentsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();
  const { data, isLoading, error } = usePayments();
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");

  const payments = data?.data ?? [];
  const pendingCount = payments.filter((payment) => payment.attributes.status === "PENDING").length;
  const paidCount = payments.filter((payment) =>
    ["PAID", "COMPLETED"].includes(payment.attributes.status)
  ).length;
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
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell
        title="Payments"
        subtitle="View payment records and transactions"
      >
        <div className="mb-4">
          <Link
            href={withHandoff("/finance")}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to finance
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load payments</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading payments...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <WorkflowHeader
              badge="Payment continuity"
              badgeIcon={CreditCard}
              title="Keep payment collection and reconciliation connected to the same encounter and billing handoff that generated the charge."
              description="Payments now show the facility in scope, source workflow, and the next finance move before staff reconcile cash, card, or remittance activity."
              context={[
                { label: "Facility", value: facility?.name ?? "No facility selected" },
                { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Finance workspace" },
                { label: "Payment queue", value: `${payments.length} payment${payments.length === 1 ? "" : "s"}` },
              ]}
              actions={actions}
              metrics={[
                {
                  label: "Pending payments",
                  value: String(pendingCount),
                  detail: "Payment intents or collections that still need settlement or cancellation handling.",
                },
                {
                  label: "Paid / completed",
                  value: String(paidCount),
                  detail: "Collections that can now support claim reconciliation or refund follow-through.",
                },
                {
                  label: "Next action",
                  value: pendingCount > 0 ? "Reconcile collection" : "Review settled payments",
                  detail:
                    encounterId && patientId
                      ? "Use this list to confirm the encounter-linked payment step without losing chart continuity."
                      : "Review pending collections first, then use billing or claims for the next finance action.",
                },
              ]}
            />

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                Payment loop status
              </p>
              <p className="mt-2 text-sm text-foreground">
                {source === "discharge"
                  ? "This payments workspace was reached from encounter outcome, so the loop here is reconciling the collection step without losing the linked bill, encounter, or chart."
                  : "This workspace tracks the broader payment queue, but it should still feed cleanly back to billing, claims, and the originating clinical episode when needed."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use the finance quick actions above to move back into billing or claims when a collection needs invoice, adjudication, or patient-context review.
              </p>
            </div>

            {payments.length === 0 ? (
              <div className="bg-card rounded-lg border border-border p-12 text-center">
                <CreditCard className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
                <p className="text-muted-foreground text-sm">No payments recorded</p>
              </div>
            ) : (
              <div className="bg-card rounded-lg border border-border overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-background">
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Payment #</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Payer</th>
                      <th className="text-right px-4 py-3 font-medium text-muted-foreground">Amount</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Method</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Date</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {payments.map((payment) => {
                      const statusStyle =
                        STATUS_STYLES[payment.attributes.status] ?? "bg-neutral-100 text-muted-foreground";
                      return (
                        <tr key={payment.id} className="hover:bg-background transition-colors">
                          <td className="px-4 py-3 font-medium text-foreground">
                            {payment.attributes.paymentNumber}
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">
                            {payment.attributes.payer}
                          </td>
                          <td className="px-4 py-3 text-right font-mono text-foreground">
                            {payment.attributes.currency}{" "}
                            {payment.attributes.amount.toLocaleString(undefined, {
                              minimumFractionDigits: 2,
                            })}
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">
                            {payment.attributes.method}
                          </td>
                          <td className="px-4 py-3">
                            <span
                              className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                            >
                              {payment.attributes.status}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                            {new Date(payment.attributes.date).toLocaleDateString()}
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
