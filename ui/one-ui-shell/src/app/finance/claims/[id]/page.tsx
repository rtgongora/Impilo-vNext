"use client";

/**
 * Claim Detail — Individual claim info, line items, adjudication history, status timeline.
 * Route: /finance/claims/[id] | pageTitle: "Claim Details"
 */

import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  FileText,
  AlertCircle,
  Clock,
  CheckCircle,
  XCircle,
  ClipboardList,
  CreditCard,
  Receipt,
  User,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface ClaimLineItem {
  code: string;
  description: string;
  quantity: number;
  unitPrice: number;
  total: number;
}

interface AdjudicationEvent {
  date: string;
  status: string;
  notes: string;
  adjudicator: string;
}

interface ClaimDetailResource {
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
    lineItems: ClaimLineItem[];
    adjudicationHistory: AdjudicationEvent[];
    [key: string]: unknown;
  };
}

type ClaimDetailResponse = ApiResponse<ClaimDetailResource>;

function useClaimDetail(id: string) {
  return useQuery<ClaimDetailResponse>({
    queryKey: ["finance-claims", id],
    queryFn: () => apiClient.get<ClaimDetailResponse>(`/internal/v1/finance/claims/${id}`),
    enabled: !!id,
  });
}

const STATUS_STYLES: Record<string, string> = {
  SUBMITTED: "bg-primary-soft text-primary",
  ADJUDICATED: "bg-yellow-100 text-yellow-700",
  PAID: "bg-green-100 text-green-700",
  REJECTED: "bg-red-100 text-danger",
};

const STATUS_ICONS: Record<string, typeof CheckCircle> = {
  SUBMITTED: Clock,
  ADJUDICATED: Clock,
  PAID: CheckCircle,
  REJECTED: XCircle,
};

export default function ClaimDetailPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const facility = useFacilityStore((state) => state.facility);
  const id = params.id as string;
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");

  const { data, isLoading, error } = useClaimDetail(id);
  const claim = data?.data;
  const handoffParams = new URLSearchParams();

  if (patientId) handoffParams.set("patientId", patientId);
  if (encounterId) handoffParams.set("encounterId", encounterId);
  if (source) handoffParams.set("source", source);

  const withHandoff = (href: string) => {
    const query = handoffParams.toString();
    return query ? `${href}?${query}` : href;
  };

  const status = claim?.attributes.status ?? "";
  const nextAction =
    status === "SUBMITTED"
      ? "Await adjudication"
      : status === "ADJUDICATED"
        ? "Reconcile payer response"
        : status === "PAID"
          ? "Confirm settlement"
          : status === "REJECTED"
            ? "Correct and resubmit"
            : "Review claim";
  const actions = [
    encounterId && patientId
      ? { href: `/ehr/${patientId}/encounter/${encounterId}`, label: "Encounter", icon: ClipboardList }
      : null,
    patientId
      ? { href: `/ehr/${patientId}`, label: "Chart", icon: User, tone: "secondary" as const }
      : null,
    { href: withHandoff("/finance/billing"), label: "Billing", icon: Receipt, tone: "secondary" as const },
    { href: withHandoff("/finance/payments"), label: "Payments", icon: CreditCard, tone: "secondary" as const },
    { href: withHandoff("/finance/payer-claims"), label: "Payer queue", icon: FileText, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell
        title="Claim Details"
        subtitle={claim ? `Claim ${claim.attributes.claimNumber}` : "Loading claim..."}
      >
        <div className="mb-4">
          <Link
            href={withHandoff("/finance/claims")}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to claims
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load claim details</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading claim details...</span>
          </div>
        ) : !claim ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <FileText className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">Claim not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            <WorkflowHeader
              badge="Claim closure"
              badgeIcon={FileText}
              title="Keep claim follow-through tied to the source encounter and patient context so adjudication decisions stay connected to the originating episode of care."
              description="Claim detail now surfaces the facility in scope, closure source, and next payer action before finance users review adjudication history."
              context={[
                { label: "Facility", value: facility?.name ?? "No facility selected" },
                { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Finance workspace" },
                { label: "Claim", value: claim.attributes.claimNumber },
              ]}
              actions={actions}
              metrics={[
                {
                  label: "Status",
                  value: status,
                  detail: "This claim remains at the reimbursement stage shown here.",
                },
                {
                  label: "Line items",
                  value: String(claim.attributes.lineItems?.length ?? 0),
                  detail: "Submitted billable services attached to the current claim.",
                },
                {
                  label: "Next action",
                  value: nextAction,
                  detail:
                    encounterId && patientId
                      ? "Resolve the payer step here, then move back to the linked encounter or chart when clarification is needed."
                      : "Use this surface to review adjudication and determine the next reimbursement action.",
                },
              ]}
            />

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                Claim loop status
              </p>
              <p className="mt-2 text-sm text-foreground">
                {source === "discharge"
                  ? "This claim was opened from encounter outcome, so the loop here is resolving payer follow-through without losing the linked encounter, chart, or finance handoff."
                  : "This claim is already in the payer workflow; the key task is reviewing adjudication while keeping the source bill and clinical episode easy to reach."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use the quick actions above to move between billing, payments, the encounter, and the chart when adjudication needs finance or clinical clarification.
              </p>
            </div>

            {/* Claim Summary */}
            <div className="bg-card rounded-lg border border-border p-6">
              <div className="flex items-start justify-between">
                <div>
                  <h2 className="text-lg font-semibold text-foreground">
                    {claim.attributes.claimNumber}
                  </h2>
                  <p className="text-sm text-muted-foreground mt-1">
                    Submitted {new Date(claim.attributes.date).toLocaleDateString()}
                  </p>
                </div>
                <span
                  className={`inline-block px-2.5 py-1 text-xs rounded-full font-medium ${
                    STATUS_STYLES[claim.attributes.status] ?? "bg-neutral-100 text-muted-foreground"
                  }`}
                >
                  {claim.attributes.status}
                </span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4 pt-4 border-t">
                <div>
                  <p className="text-xs text-muted-foreground">Patient</p>
                  <p className="text-sm font-medium text-foreground">{claim.attributes.patient}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Scheme</p>
                  <p className="text-sm font-medium text-foreground">{claim.attributes.scheme}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Total Amount</p>
                  <p className="text-sm font-semibold text-foreground">
                    {claim.attributes.currency}{" "}
                    {claim.attributes.amount.toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                    })}
                  </p>
                </div>
              </div>
            </div>

            {/* Line Items */}
            <div className="bg-card rounded-lg border border-border">
              <div className="px-5 py-4 border-b">
                <h3 className="text-sm font-medium text-foreground">Line Items</h3>
              </div>
              {!claim.attributes.lineItems || claim.attributes.lineItems.length === 0 ? (
                <div className="p-8 text-center">
                  <p className="text-muted-foreground text-sm">No line items</p>
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-background">
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Code</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Description</th>
                      <th className="text-right px-4 py-3 font-medium text-muted-foreground">Qty</th>
                      <th className="text-right px-4 py-3 font-medium text-muted-foreground">Unit Price</th>
                      <th className="text-right px-4 py-3 font-medium text-muted-foreground">Total</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {claim.attributes.lineItems.map((item, idx) => (
                      <tr key={idx} className="hover:bg-background transition-colors">
                        <td className="px-4 py-3 font-mono text-xs text-foreground">{item.code}</td>
                        <td className="px-4 py-3 text-muted-foreground">{item.description}</td>
                        <td className="px-4 py-3 text-right text-muted-foreground">{item.quantity}</td>
                        <td className="px-4 py-3 text-right font-mono text-muted-foreground">
                          {item.unitPrice.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                        <td className="px-4 py-3 text-right font-mono text-foreground">
                          {item.total.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Adjudication History / Status Timeline */}
            <div className="bg-card rounded-lg border border-border">
              <div className="px-5 py-4 border-b">
                <h3 className="text-sm font-medium text-foreground">Adjudication History</h3>
              </div>
              {!claim.attributes.adjudicationHistory ||
              claim.attributes.adjudicationHistory.length === 0 ? (
                <div className="p-8 text-center">
                  <p className="text-muted-foreground text-sm">No adjudication events</p>
                </div>
              ) : (
                <div className="p-5">
                  <div className="space-y-4">
                    {claim.attributes.adjudicationHistory.map((event, idx) => {
                      const StatusIcon = STATUS_ICONS[event.status] ?? Clock;
                      const style =
                        STATUS_STYLES[event.status] ?? "bg-neutral-100 text-muted-foreground";
                      return (
                        <div key={idx} className="flex gap-4">
                          <div className="flex flex-col items-center">
                            <div
                              className={`w-8 h-8 rounded-full flex items-center justify-center ${style}`}
                            >
                              <StatusIcon className="w-4 h-4" />
                            </div>
                            {idx < claim.attributes.adjudicationHistory.length - 1 && (
                              <div className="w-px h-full bg-neutral-100 mt-1" />
                            )}
                          </div>
                          <div className="pb-4">
                            <div className="flex items-center gap-2">
                              <span
                                className={`inline-block px-2 py-0.5 text-xs rounded-full ${style}`}
                              >
                                {event.status}
                              </span>
                              <span className="text-xs text-muted-foreground">
                                {new Date(event.date).toLocaleString()}
                              </span>
                            </div>
                            <p className="text-sm text-muted-foreground mt-1">{event.notes}</p>
                            <p className="text-xs text-muted-foreground mt-0.5">
                              by {event.adjudicator}
                            </p>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
