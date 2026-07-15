"use client";

/**
 * Billing Detail — Single bill view with line items, party allocations,
 * and lifecycle action buttons (submit, approve, finalize, invoice).
 * Route: /finance/billing/[id] | pageTitle: "Bill Details"
 */

import { useState, useEffect } from "react";
import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft, Loader2, Receipt, AlertCircle, FileText,
  Send, CheckCircle, Lock, FileOutput, CreditCard, DollarSign,
  XCircle, RefreshCw, RotateCcw, ClipboardList, User,
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface BillLineItem {
  msikaCode?: string;
  description?: string;
  kind?: string;
  qty?: number;
  unitPrice?: number;
  amount?: number;
  /** COSTA flags a line with no configured tariff: amount=0 until priced. */
  pendingPricing?: boolean;
  [key: string]: unknown;
}

interface BillParty {
  partyType?: string;
  partyRef?: string;
  amount?: number;
  [key: string]: unknown;
}

interface BillingDetailResource {
  id: string;
  type: "invoice";
  attributes: {
    invoiceNumber: string;
    patient: string;
    amount: number;
    currency: string;
    status: string;
    date: string;
    billType?: string;
    facilityId?: string;
    totalCost?: number;
    totalCharge?: number;
    patientPayable?: number;
    insurerPayable?: number;
    coverageStatus?: string;
    lineItems?: BillLineItem[];
    parties?: BillParty[];
    [key: string]: unknown;
  };
}

type BillingDetailResponse = ApiResponse<BillingDetailResource>;

const STATUS_STYLES: Record<string, string> = {
  DRAFT: "bg-neutral-100 text-foreground",
  ACCUMULATING: "bg-yellow-100 text-yellow-700",
  APPROVAL_PENDING: "bg-orange-100 text-orange-700",
  APPROVED: "bg-primary-soft text-primary",
  FINAL: "bg-green-100 text-green-700",
  VOID: "bg-red-100 text-danger",
  ADJUSTED: "bg-purple-100 text-warning-foreground",
};

function useBillingDetail(id: string) {
  return useQuery<BillingDetailResponse>({
    queryKey: ["finance-billing", id],
    queryFn: () => apiClient.get<BillingDetailResponse>(`/internal/v1/finance/billing/${id}`),
    enabled: !!id,
  });
}

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
    paidAmount: number;
    billId: string;
    [key: string]: unknown;
  };
}

const PAYMENT_STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  PAID: "bg-green-100 text-green-700",
  COMPLETED: "bg-green-100 text-green-700",
  FAILED: "bg-red-100 text-danger",
  CANCELLED: "bg-neutral-100 text-muted-foreground",
};

interface RefundResource {
  id: string;
  type: "refund";
  attributes: {
    billId: string;
    amount: number;
    reason: string;
    reasonCode: string;
    refundType: string;
    status: string;
    createdAt: string;
    processedAt: string;
    [key: string]: unknown;
  };
}

const REFUND_STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  PROCESSED: "bg-green-100 text-green-700",
  FAILED: "bg-red-100 text-danger",
};

function useBillRefunds(billId: string) {
  return useQuery<{ data: RefundResource[] }>({
    queryKey: ["finance-billing-refunds", billId],
    queryFn: () => apiClient.get(`/internal/v1/finance/billing/${billId}/refunds`),
    enabled: !!billId,
  });
}

function useBillPayments(billId: string) {
  return useQuery<{ data: PaymentResource[] }>({
    queryKey: ["finance-billing-payments", billId],
    queryFn: () => apiClient.get(`/internal/v1/finance/billing/${billId}/payments`),
    enabled: !!billId,
  });
}

function useBillAction(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ action, body, query }: { action: string; body?: Record<string, string>; query?: Record<string, string> }) => {
      const qs = query ? `?${new URLSearchParams(query).toString()}` : "";
      return apiClient.post(`/internal/v1/finance/billing/${id}/${action}${qs}`, body ?? {});
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["finance-billing", id] });
      queryClient.invalidateQueries({ queryKey: ["finance-billing"] });
    },
  });
}

export default function BillingDetailPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const facility = useFacilityStore((state) => state.facility);
  const id = params.id as string;
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");
  const [approvalNote, setApprovalNote] = useState("");
  const [paymentAmount, setPaymentAmount] = useState("");
  const [paymentType, setPaymentType] = useState("FULL");
  const [paymentMethod, setPaymentMethod] = useState("CASH");
  const [refundAmount, setRefundAmount] = useState("");
  const [refundReason, setRefundReason] = useState("");
  const [allowUnpriced, setAllowUnpriced] = useState(false);

  const { data, isLoading, error } = useBillingDetail(id);
  const { data: paymentsData, refetch: refetchPayments } = useBillPayments(id);
  const { data: refundsData, refetch: refetchRefunds } = useBillRefunds(id);
  const billAction = useBillAction(id);
  const queryClient = useQueryClient();
  const createPayment = useMutation({
    mutationFn: (body: { paymentType: string; amount?: string; method?: string }) => {
      // Blank amount for FULL/REMAINDER → the BFF derives it from the bill's
      // coverage split (REMAINDER = patient shortfall, FULL = total payable).
      const payload: Record<string, string> = { paymentType: body.paymentType };
      if (body.amount) payload.amount = body.amount;
      if (body.method) payload.method = body.method;
      return apiClient.post<{ data: unknown; meta?: { amount_source?: string } }>(
        `/internal/v1/finance/billing/${id}/payment`,
        payload,
      );
    },
    onSuccess: () => {
      refetchPayments();
      queryClient.invalidateQueries({ queryKey: ["finance-billing", id] });
      setPaymentAmount("");
    },
  });
  const paymentAmountSource = (createPayment.data as { meta?: { amount_source?: string } } | undefined)
    ?.meta?.amount_source;
  const serverDerivableType = paymentType === "FULL" || paymentType === "REMAINDER";
  const cancelPayment = useMutation({
    mutationFn: (paymentId: string) =>
      apiClient.post(`/internal/v1/finance/billing/${id}/payments/${paymentId}/cancel`),
    onSuccess: () => {
      refetchPayments();
    },
  });
  const createRefund = useMutation({
    mutationFn: (body: { amount: string; reason: string }) =>
      apiClient.post(`/internal/v1/finance/billing/${id}/refund`, body),
    onSuccess: () => {
      refetchRefunds();
      refetchPayments();
      setRefundAmount("");
      setRefundReason("");
    },
  });
  const applyCoverage = useMutation({
    mutationFn: () =>
      apiClient.post(`/internal/v1/finance/billing/${id}/apply-coverage`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["finance-billing", id] });
    },
  });
  const bill = data?.data;
  const payments: PaymentResource[] = paymentsData?.data ?? [];
  const refunds: RefundResource[] = refundsData?.data ?? [];
  const hasPaidPayment = payments.some((p) => p.attributes.status === "PAID");
  const status = bill?.attributes.status ?? "";
  // Lines COSTA could not price (no configured tariff) — finalizing these
  // requires an explicit governed override.
  const pendingLineCount = (bill?.attributes.lineItems ?? []).filter((l) => l.pendingPricing).length;
  const nextAction =
    status === "DRAFT" || status === "ACCUMULATING"
      ? "Submit for approval"
      : status === "APPROVAL_PENDING"
        ? "Approve bill"
        : status === "APPROVED"
          ? "Finalize bill"
          : status === "FINAL"
            ? "Invoice or collect payment"
            : status === "VOID"
              ? "Closed"
              : "Review status";
  const actions = [
    encounterId && patientId
      ? { href: `/ehr/${patientId}/encounter/${encounterId}`, label: "Encounter", icon: ClipboardList }
      : null,
    patientId
      ? { href: `/ehr/${patientId}`, label: "Chart", icon: User, tone: "secondary" as const }
      : null,
    { href: "/finance/billing", label: "Billing List", icon: Receipt, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  // Default payment amount: patient shortfall when coverage split the bill
  // (REMAINDER of patientPayable), otherwise the full totalPayable.
  const insurerShare = bill?.attributes.insurerPayable ?? 0;
  const patientShare = bill?.attributes.patientPayable ?? bill?.attributes.amount ?? 0;
  useEffect(() => {
    if (!bill || paymentAmount) return;
    if (insurerShare > 0 && paymentType === "FULL") {
      setPaymentType("REMAINDER");
      setPaymentAmount(patientShare.toFixed(2));
    } else if (paymentType === "FULL") {
      setPaymentAmount(bill.attributes.amount.toFixed(2));
    }
  }, [bill, paymentType, paymentAmount, insurerShare, patientShare]);

  return (
    <AppLayout>
      <PageShell
        title="Bill Details"
        subtitle={bill ? `Bill ${bill.attributes.invoiceNumber}` : "Loading bill..."}
      >
        <div className="mb-4">
          <Link
            href="/finance/billing"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to billing
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load bill details</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading bill details...</span>
          </div>
        ) : !bill ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <Receipt className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">Bill not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            <WorkflowHeader
              badge="Finance closure"
              badgeIcon={Receipt}
              title="Keep bill handling connected to the source encounter and patient context so finance actions do not drift away from the clinical episode that created them."
              description="Bill detail now surfaces the facility in scope, encounter handoff, and next revenue-cycle step before users approve, finalize, invoice, or refund."
              context={[
                { label: "Facility", value: facility?.name ?? "No facility selected" },
                { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Finance workspace" },
                { label: "Bill", value: bill.attributes.invoiceNumber },
              ]}
              actions={actions}
              metrics={[
                {
                  label: "Status",
                  value: status.replace(/_/g, " "),
                  detail: "This bill remains in the finance workflow stage shown here.",
                },
                {
                  label: "Line items",
                  value: String(bill.attributes.lineItems?.length ?? 0),
                  detail: "Charges posted from encounter services and downstream activity.",
                },
                {
                  label: "Next action",
                  value: nextAction,
                  detail:
                    encounterId && patientId
                      ? "Resolve the finance step here, then move back to the linked encounter or chart if clarification is needed."
                      : "Use the action panel below to move this bill to its next finance state.",
                },
              ]}
            />

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                Bill loop status
              </p>
              <p className="mt-2 text-sm text-foreground">
                {source === "discharge"
                  ? "This bill was opened from encounter outcome, so the finance loop here is completing the next billing step without losing patient or encounter continuity."
                  : "This bill is already in the finance workflow; the key task is moving it to the next status while keeping its originating encounter easy to reach."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use the action panel for the current finance step, or jump back to the linked encounter or chart when the bill needs clinical review, documentation, or correction.
              </p>
            </div>

            {/* Bill Summary */}
            <div className="bg-card rounded-lg border border-border p-6">
              <div className="flex items-start justify-between">
                <div>
                  <h2 className="text-lg font-semibold text-foreground">
                    {bill.attributes.invoiceNumber}
                  </h2>
                  <p className="text-sm text-muted-foreground mt-1">
                    Created {bill.attributes.date ? new Date(bill.attributes.date).toLocaleDateString() : "—"}
                  </p>
                </div>
                <span
                  className={`inline-block px-2.5 py-1 text-xs rounded-full font-medium ${
                    STATUS_STYLES[status] ?? "bg-neutral-100 text-muted-foreground"
                  }`}
                >
                  {status.replace(/_/g, " ")}
                </span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mt-4 pt-4 border-t">
                <div>
                  <p className="text-xs text-muted-foreground">Encounter</p>
                  <p className="text-sm font-medium text-foreground">
                    {bill.attributes.patient || "—"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Bill Type</p>
                  <p className="text-sm font-medium text-foreground">
                    {bill.attributes.billType || "ENCOUNTER"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Total Charge</p>
                  <p className="text-sm font-semibold text-foreground">
                    {bill.attributes.currency}{" "}
                    {(bill.attributes.totalCharge ?? 0).toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                    })}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Total Payable</p>
                  <p className="text-sm font-semibold text-primary">
                    {bill.attributes.currency}{" "}
                    {bill.attributes.amount.toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                    })}
                  </p>
                </div>
              </div>

              {/* Coverage split — payer vs patient responsibility */}
              <div className="mt-4 pt-4 border-t">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <p className="text-xs font-medium text-muted-foreground">Coverage</p>
                    {bill.attributes.coverageStatus ? (
                      <span
                        className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                          bill.attributes.coverageStatus.startsWith("ELIGIBLE") ||
                          bill.attributes.coverageStatus.startsWith("CLAIM_SUBMITTED")
                            ? "bg-green-100 text-green-700"
                            : bill.attributes.coverageStatus.startsWith("INELIGIBLE") ||
                                bill.attributes.coverageStatus.startsWith("CLAIM_FAILED")
                              ? "bg-red-100 text-danger"
                              : "bg-neutral-100 text-muted-foreground"
                        }`}
                      >
                        {bill.attributes.coverageStatus.split(":")[0].replace(/_/g, " ")}
                      </span>
                    ) : (
                      <span className="text-xs text-muted-foreground">not checked</span>
                    )}
                  </div>
                  {!["FINAL", "VOID"].includes(status) && (
                    <button
                      onClick={() => applyCoverage.mutate()}
                      disabled={applyCoverage.isPending}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-primary bg-primary-soft rounded-lg hover:bg-primary/10 disabled:opacity-50 transition-colors"
                    >
                      {applyCoverage.isPending
                        ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        : <CheckCircle className="w-3.5 h-3.5" />}
                      Apply Coverage
                    </button>
                  )}
                </div>
                {applyCoverage.isError && (
                  <p className="mb-2 text-xs text-red-600">
                    Coverage check failed — the patient may have no active cover for this facility.
                  </p>
                )}
                <div className="grid grid-cols-2 gap-4">
                  <div className="rounded-lg bg-background p-3">
                    <p className="text-xs text-muted-foreground">Insurer Payable</p>
                    <p className="text-sm font-mono font-semibold text-foreground">
                      {bill.attributes.currency}{" "}
                      {(bill.attributes.insurerPayable ?? 0).toLocaleString(undefined, {
                        minimumFractionDigits: 2,
                      })}
                    </p>
                  </div>
                  <div className="rounded-lg bg-background p-3">
                    <p className="text-xs text-muted-foreground">Patient Payable</p>
                    <p className="text-sm font-mono font-semibold text-primary">
                      {bill.attributes.currency}{" "}
                      {(bill.attributes.patientPayable ?? bill.attributes.amount).toLocaleString(undefined, {
                        minimumFractionDigits: 2,
                      })}
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* Bill Actions */}
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="text-sm font-medium text-foreground mb-3">Actions</h3>

              {billAction.isError && (
                <div className="mb-3 p-3 rounded-lg bg-danger-soft border border-danger/28">
                  <p className="text-sm text-danger">
                    Action failed. The bill may not be in the required state for this operation.
                  </p>
                </div>
              )}

              {billAction.isSuccess && (
                <div className="mb-3 p-3 rounded-lg bg-green-50 border border-green-200">
                  <p className="text-sm text-green-700">Action completed successfully.</p>
                </div>
              )}

              <div className="flex flex-wrap gap-3">
                {(status === "DRAFT" || status === "ACCUMULATING") && (
                  <button
                    onClick={() => billAction.mutate({ action: "submit" })}
                    disabled={billAction.isPending}
                    className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary-hover disabled:opacity-50 transition-colors"
                  >
                    {billAction.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                    Submit for Approval
                  </button>
                )}

                {status === "APPROVAL_PENDING" && (
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      placeholder="Approval note (optional)"
                      value={approvalNote}
                      onChange={(e) => setApprovalNote(e.target.value)}
                      className="px-3 py-2 text-sm border border-border rounded-lg focus:ring-2 focus:ring-primary/40 focus:border-impilo-400 w-64"
                    />
                    <button
                      onClick={() => billAction.mutate({ action: "approve", body: { note: approvalNote } })}
                      disabled={billAction.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-green-600 rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors"
                    >
                      {billAction.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle className="w-4 h-4" />}
                      Approve
                    </button>
                  </div>
                )}

                {status === "APPROVED" && (
                  <div className="flex flex-col gap-3 w-full">
                    {pendingLineCount > 0 && (
                      <div className="rounded-lg border border-amber-300 bg-amber-50 p-3">
                        <div className="flex items-start gap-2">
                          <AlertCircle className="mt-0.5 h-4 w-4 text-amber-600" />
                          <div className="text-xs text-amber-800">
                            <p className="font-medium">
                              {pendingLineCount} line{pendingLineCount === 1 ? "" : "s"} have no configured tariff (pending pricing).
                            </p>
                            <p className="mt-1">
                              By default the bill cannot be finalized while lines are unpriced. To proceed,
                              record an explicit override — those lines will be finalized as zero (free).
                            </p>
                          </div>
                        </div>
                        <label className="mt-2 flex items-center gap-2 text-xs font-medium text-amber-900">
                          <input
                            type="checkbox"
                            checked={allowUnpriced}
                            onChange={(e) => setAllowUnpriced(e.target.checked)}
                            className="h-3.5 w-3.5 rounded border-amber-400"
                          />
                          Override: finalize with unpriced (free) lines
                        </label>
                      </div>
                    )}
                    <button
                      onClick={() =>
                        billAction.mutate({
                          action: "finalize",
                          query: allowUnpriced ? { allowUnpriced: "true" } : undefined,
                        })
                      }
                      disabled={billAction.isPending || (pendingLineCount > 0 && !allowUnpriced)}
                      className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-purple-600 rounded-lg hover:bg-purple-700 disabled:opacity-50 transition-colors self-start"
                    >
                      {billAction.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Lock className="w-4 h-4" />}
                      Finalize Bill
                    </button>
                  </div>
                )}

                {status === "FINAL" && (
                  <div className="flex flex-col gap-3 w-full">
                    <div className="flex gap-3">
                      <button
                        onClick={() => billAction.mutate({ action: "invoice" })}
                        disabled={billAction.isPending}
                        className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-amber-600 rounded-lg hover:bg-amber-700 disabled:opacity-50 transition-colors"
                      >
                        {billAction.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileOutput className="w-4 h-4" />}
                        Issue Invoice
                      </button>
                    </div>
                    <div className="pt-3 border-t border-border">
                      <p className="text-xs font-medium text-muted-foreground mb-2">Collect Payment</p>
                      <div className="space-y-3">
                        <div className="flex items-center gap-2">
                          <select
                            value={paymentType}
                            onChange={(e) => {
                              setPaymentType(e.target.value);
                              if (e.target.value === "FULL" && bill) {
                                setPaymentAmount(bill.attributes.amount.toFixed(2));
                              }
                            }}
                            className="px-3 py-2 text-sm border border-border rounded-lg focus:ring-2 focus:ring-primary/40"
                          >
                            <option value="FULL">Full Payment</option>
                            <option value="DEPOSIT">Deposit</option>
                            <option value="REMAINDER">Remainder</option>
                            <option value="THIRD_PARTY">Third Party</option>
                            <option value="REMITTANCE">Remittance</option>
                          </select>
                          <input
                            type="number"
                            step="0.01"
                            min="0.01"
                            placeholder={serverDerivableType ? "Amount (auto if blank)" : "Amount"}
                            value={paymentAmount}
                            onChange={(e) => setPaymentAmount(e.target.value)}
                            className="px-3 py-2 text-sm border border-border rounded-lg focus:ring-2 focus:ring-primary/40 w-36"
                          />
                        </div>
                        {serverDerivableType && !paymentAmount && (
                          <p className="text-[11px] text-muted-foreground">
                            Leave the amount blank to charge the bill&apos;s own figure:{" "}
                            {paymentType === "REMAINDER" ? "the patient shortfall" : "the total payable"} is
                            derived server-side from the coverage split.
                          </p>
                        )}
                        {/* Payment method selector — Mushe + all channels */}
                        <div>
                          <p className="text-[11px] font-medium text-muted-foreground mb-1.5">Payment Method</p>
                          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-1.5">
                            {[
                              { id: "MUSHE_WALLET", label: "Mushe Wallet", color: "bg-primary-soft border-impilo-300 text-primary-hover" },
                              { id: "CASH", label: "Cash", color: "bg-green-50 border-green-300 text-green-700" },
                              { id: "ECOCASH", label: "EcoCash", color: "bg-danger-soft border-red-300 text-danger" },
                              { id: "INNBUCKS", label: "InnBucks", color: "bg-info-soft border-blue-300 text-primary-hover" },
                              { id: "ONE_MONEY", label: "OneMoney", color: "bg-yellow-50 border-yellow-300 text-yellow-700" },
                              { id: "BANK_TRANSFER", label: "Bank / ZIPIT", color: "bg-background border-border text-foreground" },
                              { id: "VISA", label: "Visa", color: "bg-info-soft border-indigo-300 text-primary-hover" },
                              { id: "MASTERCARD", label: "Mastercard", color: "bg-orange-50 border-orange-300 text-orange-700" },
                              { id: "INSURANCE", label: "Medical Aid", color: "bg-warning-soft border-purple-300 text-warning-foreground" },
                              { id: "GOVERNMENT_SUBSIDY", label: "Govt Subsidy", color: "bg-success-soft border-emerald-300 text-primary-hover" },
                            ].map((m) => (
                              <button
                                key={m.id}
                                type="button"
                                onClick={() => setPaymentMethod(m.id)}
                                className={`px-2 py-1.5 text-[11px] font-medium rounded-md border transition-all ${m.color} ${paymentMethod === m.id ? "ring-2 ring-offset-1 ring-gray-400 shadow-sm" : "opacity-60 hover:opacity-100"}`}
                              >
                                {m.label}
                              </button>
                            ))}
                          </div>
                        </div>
                        <button
                          onClick={() => createPayment.mutate({ paymentType, amount: paymentAmount || undefined, method: paymentMethod })}
                          disabled={createPayment.isPending || (!paymentAmount && !serverDerivableType)}
                          className="w-full inline-flex items-center justify-center gap-1.5 px-4 py-2.5 text-sm font-medium text-white bg-emerald-600 rounded-lg hover:bg-emerald-700 disabled:opacity-50 transition-colors"
                        >
                          {createPayment.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <CreditCard className="w-4 h-4" />}
                          Collect Payment via {paymentMethod.replace(/_/g, " ")}
                        </button>
                      </div>
                      {createPayment.isError && (
                        <p className="mt-2 text-xs text-red-600">Failed to create payment.</p>
                      )}
                      {createPayment.isSuccess && (
                        <p className="mt-2 text-xs text-green-600">
                          Payment intent recorded.
                          {paymentAmountSource === "SERVER_DERIVED_PATIENT_PAYABLE"
                            ? " Amount derived from the patient shortfall on the bill."
                            : paymentAmountSource === "SERVER_DERIVED_TOTAL_PAYABLE"
                              ? " Amount derived from the bill's total payable."
                              : ""}
                        </p>
                      )}
                    </div>
                  </div>
                )}

                {status === "VOID" && (
                  <p className="text-sm text-muted-foreground">This bill has been voided. No actions available.</p>
                )}

                {!["DRAFT", "ACCUMULATING", "APPROVAL_PENDING", "APPROVED", "FINAL", "VOID"].includes(status) && (
                  <p className="text-sm text-muted-foreground">No actions available for this bill status.</p>
                )}
              </div>
            </div>

            {/* Line Items */}
            <div className="bg-card rounded-lg border border-border">
              <div className="px-5 py-4 border-b">
                <h3 className="text-sm font-medium text-foreground">Line Items</h3>
              </div>
              {!bill.attributes.lineItems || bill.attributes.lineItems.length === 0 ? (
                <div className="p-8 text-center">
                  <FileText className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
                  <p className="text-muted-foreground text-sm">No line items posted yet</p>
                  <p className="text-muted-foreground text-xs mt-1">
                    Line items are added as orders and services are recorded during the encounter.
                  </p>
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-background">
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Code</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Description</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Kind</th>
                      <th className="text-right px-4 py-3 font-medium text-muted-foreground">Qty</th>
                      <th className="text-right px-4 py-3 font-medium text-muted-foreground">Unit Price</th>
                      <th className="text-right px-4 py-3 font-medium text-muted-foreground">Amount</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {bill.attributes.lineItems.map((line, idx) => (
                      <tr key={idx} className="hover:bg-background transition-colors">
                        <td className="px-4 py-3 font-mono text-xs text-foreground">
                          {line.msikaCode || "—"}
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          {line.description || "—"}
                          {line.pendingPricing && (
                            <span className="ml-2 inline-block px-2 py-0.5 text-[10px] font-medium rounded-full bg-amber-100 text-amber-700 align-middle">
                              No configured tariff — pending pricing
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-neutral-100 text-muted-foreground">
                            {line.kind || "—"}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right text-muted-foreground">
                          {line.qty ?? 0}
                        </td>
                        <td className="px-4 py-3 text-right font-mono text-muted-foreground">
                          {(line.unitPrice ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                        <td className="px-4 py-3 text-right font-mono text-foreground">
                          {(line.amount ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Party Allocations */}
            {bill.attributes.parties && bill.attributes.parties.length > 0 && (
              <div className="bg-card rounded-lg border border-border">
                <div className="px-5 py-4 border-b">
                  <h3 className="text-sm font-medium text-foreground">Party Allocations</h3>
                </div>
                <div className="divide-y divide-gray-100">
                  {bill.attributes.parties.map((party, idx) => (
                    <div key={idx} className="px-5 py-3 flex items-center justify-between">
                      <div>
                        <span className="text-sm font-medium text-foreground">
                          {party.partyType || "Unknown"}
                        </span>
                        {party.partyRef && (
                          <span className="ml-2 text-xs text-muted-foreground">
                            Ref: {party.partyRef}
                          </span>
                        )}
                      </div>
                      <span className="text-sm font-mono font-semibold text-foreground">
                        {bill.attributes.currency}{" "}
                        {(party.amount ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Payments */}
            <div className="bg-card rounded-lg border border-border">
              <div className="px-5 py-4 border-b flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <DollarSign className="w-4 h-4 text-muted-foreground" />
                  <h3 className="text-sm font-medium text-foreground">Payments</h3>
                  {payments.length > 0 && (
                    <span className="text-xs text-muted-foreground">({payments.length})</span>
                  )}
                </div>
                <button
                  onClick={() => refetchPayments()}
                  className="inline-flex items-center gap-1 px-2 py-1 text-xs text-muted-foreground hover:text-foreground hover:bg-neutral-100 rounded transition-colors"
                  title="Refresh payment status"
                >
                  <RefreshCw className="w-3 h-3" />
                  Refresh
                </button>
              </div>
              {payments.length === 0 ? (
                <div className="p-8 text-center">
                  <CreditCard className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
                  <p className="text-muted-foreground text-sm">No payments recorded</p>
                  <p className="text-muted-foreground text-xs mt-1">
                    {status === "FINAL"
                      ? "Use the payment form above to create a payment intent."
                      : "Payment intents can be created after the bill is finalized."}
                  </p>
                </div>
              ) : (
                <div className="divide-y divide-gray-100">
                  {payments.map((payment) => (
                    <div key={payment.id} className="px-5 py-3 flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div>
                          <p className="text-sm font-medium text-foreground">
                            {payment.attributes.paymentNumber}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {payment.attributes.method} &middot;{" "}
                            {payment.attributes.date
                              ? new Date(payment.attributes.date).toLocaleString()
                              : "—"}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                            PAYMENT_STATUS_STYLES[payment.attributes.status] ?? "bg-neutral-100 text-muted-foreground"
                          }`}
                        >
                          {payment.attributes.status}
                        </span>
                        <span className="text-sm font-mono font-semibold text-foreground">
                          {payment.attributes.currency}{" "}
                          {payment.attributes.amount.toLocaleString(undefined, {
                            minimumFractionDigits: 2,
                          })}
                        </span>
                        {payment.attributes.status === "PENDING" && (
                          <button
                            onClick={() => cancelPayment.mutate(payment.id)}
                            disabled={cancelPayment.isPending}
                            className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-red-600 bg-danger-soft rounded-lg hover:bg-red-100 disabled:opacity-50 transition-colors"
                            title="Cancel this payment"
                          >
                            {cancelPayment.isPending
                              ? <Loader2 className="w-3 h-3 animate-spin" />
                              : <XCircle className="w-3 h-3" />}
                            Cancel
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Refunds */}
            <div className="bg-card rounded-lg border border-border">
              <div className="px-5 py-4 border-b flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <RotateCcw className="w-4 h-4 text-muted-foreground" />
                  <h3 className="text-sm font-medium text-foreground">Refunds</h3>
                  {refunds.length > 0 && (
                    <span className="text-xs text-muted-foreground">({refunds.length})</span>
                  )}
                </div>
                <button
                  onClick={() => refetchRefunds()}
                  className="inline-flex items-center gap-1 px-2 py-1 text-xs text-muted-foreground hover:text-foreground hover:bg-neutral-100 rounded transition-colors"
                >
                  <RefreshCw className="w-3 h-3" />
                  Refresh
                </button>
              </div>

              {/* Refund creation form — only for bills with paid payments */}
              {hasPaidPayment && (() => {
                const totalPaid = payments
                  .filter((p) => p.attributes.status === "PAID")
                  .reduce((sum, p) => sum + (p.attributes.paidAmount ?? p.attributes.amount), 0);
                const totalRefunded = refunds
                  .filter((r) => r.attributes.status !== "FAILED")
                  .reduce((sum, r) => sum + r.attributes.amount, 0);
                const refundableBalance = Math.max(0, totalPaid - totalRefunded);
                return (
                <div className="px-5 py-3 border-b bg-background">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-xs font-medium text-muted-foreground">Request Refund</p>
                    <p className="text-xs text-muted-foreground">
                      Refundable: <span className="font-mono font-medium text-foreground">
                        {bill.attributes.currency} {refundableBalance.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </span>
                    </p>
                  </div>
                  <div className="flex items-start gap-2">
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      placeholder="Refund amount"
                      value={refundAmount}
                      onChange={(e) => setRefundAmount(e.target.value)}
                      className="px-3 py-2 text-sm border border-border rounded-lg focus:ring-2 focus:ring-primary/40 w-32"
                    />
                    <input
                      type="text"
                      placeholder="Reason for refund"
                      value={refundReason}
                      onChange={(e) => setRefundReason(e.target.value)}
                      className="px-3 py-2 text-sm border border-border rounded-lg focus:ring-2 focus:ring-primary/40 flex-1"
                    />
                    <button
                      onClick={() => createRefund.mutate({ amount: refundAmount, reason: refundReason })}
                      disabled={createRefund.isPending || !refundAmount || !refundReason}
                      className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-red-600 rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors whitespace-nowrap"
                    >
                      {createRefund.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <RotateCcw className="w-4 h-4" />}
                      Request Refund
                    </button>
                  </div>
                  {createRefund.isError && (
                    <p className="mt-2 text-xs text-red-600">Failed to create refund request.</p>
                  )}
                  {createRefund.isSuccess && (
                    <p className="mt-2 text-xs text-green-600">Refund request created successfully.</p>
                  )}
                </div>
                );
              })()}

              {refunds.length === 0 ? (
                <div className="p-8 text-center">
                  <RotateCcw className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
                  <p className="text-muted-foreground text-sm">No refunds recorded</p>
                  {!hasPaidPayment && (
                    <p className="text-muted-foreground text-xs mt-1">
                      Refunds can be requested after a payment has been completed.
                    </p>
                  )}
                </div>
              ) : (
                <div className="divide-y divide-gray-100">
                  {refunds.map((refund) => (
                    <div key={refund.id} className="px-5 py-3 flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium text-foreground">
                          {refund.attributes.refundType} Refund
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {refund.attributes.reason}
                          {refund.attributes.createdAt && (
                            <span> &middot; {new Date(refund.attributes.createdAt).toLocaleString()}</span>
                          )}
                        </p>
                      </div>
                      <div className="flex items-center gap-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                            REFUND_STATUS_STYLES[refund.attributes.status] ?? "bg-neutral-100 text-muted-foreground"
                          }`}
                        >
                          {refund.attributes.status}
                        </span>
                        <span className="text-sm font-mono font-semibold text-red-600">
                          -{bill.attributes.currency}{" "}
                          {refund.attributes.amount.toLocaleString(undefined, {
                            minimumFractionDigits: 2,
                          })}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
