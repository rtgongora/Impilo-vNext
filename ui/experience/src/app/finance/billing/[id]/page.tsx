"use client";

/**
 * Billing Detail — Single bill view with line items, party allocations,
 * and lifecycle action buttons (submit, approve, finalize, invoice).
 * Route: /finance/billing/[id] | pageTitle: "Bill Details"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft, Loader2, Receipt, AlertCircle, FileText,
  Send, CheckCircle, Lock, FileOutput, CreditCard, DollarSign,
  XCircle, RefreshCw,
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface BillLineItem {
  msikaCode?: string;
  description?: string;
  kind?: string;
  qty?: number;
  unitPrice?: number;
  amount?: number;
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
    lineItems?: BillLineItem[];
    parties?: BillParty[];
    [key: string]: unknown;
  };
}

type BillingDetailResponse = ApiResponse<BillingDetailResource>;

const STATUS_STYLES: Record<string, string> = {
  DRAFT: "bg-gray-100 text-gray-700",
  ACCUMULATING: "bg-yellow-100 text-yellow-700",
  APPROVAL_PENDING: "bg-orange-100 text-orange-700",
  APPROVED: "bg-blue-100 text-blue-700",
  FINAL: "bg-green-100 text-green-700",
  VOID: "bg-red-100 text-red-700",
  ADJUSTED: "bg-purple-100 text-purple-700",
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
  FAILED: "bg-red-100 text-red-700",
  CANCELLED: "bg-gray-100 text-gray-600",
};

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
    mutationFn: ({ action, body }: { action: string; body?: Record<string, string> }) =>
      apiClient.post(`/internal/v1/finance/billing/${id}/${action}`, body ?? {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["finance-billing", id] });
      queryClient.invalidateQueries({ queryKey: ["finance-billing"] });
    },
  });
}

export default function BillingDetailPage() {
  const params = useParams();
  const id = params.id as string;
  const [approvalNote, setApprovalNote] = useState("");
  const [paymentAmount, setPaymentAmount] = useState("");
  const [paymentType, setPaymentType] = useState("FULL");

  const { data, isLoading, error } = useBillingDetail(id);
  const { data: paymentsData, refetch: refetchPayments } = useBillPayments(id);
  const billAction = useBillAction(id);
  const queryClient = useQueryClient();
  const createPayment = useMutation({
    mutationFn: (body: { paymentType: string; amount: string }) =>
      apiClient.post(`/internal/v1/finance/billing/${id}/payment`, body),
    onSuccess: () => {
      refetchPayments();
      queryClient.invalidateQueries({ queryKey: ["finance-billing", id] });
      setPaymentAmount("");
    },
  });
  const cancelPayment = useMutation({
    mutationFn: (paymentId: string) =>
      apiClient.post(`/internal/v1/finance/billing/${id}/payments/${paymentId}/cancel`),
    onSuccess: () => {
      refetchPayments();
    },
  });
  const bill = data?.data;
  const payments: PaymentResource[] = paymentsData?.data ?? [];

  const status = bill?.attributes.status ?? "";

  return (
    <AppLayout>
      <PageShell
        title="Bill Details"
        subtitle={bill ? `Bill ${bill.attributes.invoiceNumber}` : "Loading bill..."}
      >
        <div className="mb-4">
          <Link
            href="/finance/billing"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to billing
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load bill details</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading bill details...</span>
          </div>
        ) : !bill ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Receipt className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Bill not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Bill Summary */}
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <div className="flex items-start justify-between">
                <div>
                  <h2 className="text-lg font-semibold text-gray-900">
                    {bill.attributes.invoiceNumber}
                  </h2>
                  <p className="text-sm text-gray-500 mt-1">
                    Created {bill.attributes.date ? new Date(bill.attributes.date).toLocaleDateString() : "—"}
                  </p>
                </div>
                <span
                  className={`inline-block px-2.5 py-1 text-xs rounded-full font-medium ${
                    STATUS_STYLES[status] ?? "bg-gray-100 text-gray-600"
                  }`}
                >
                  {status.replace(/_/g, " ")}
                </span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mt-4 pt-4 border-t">
                <div>
                  <p className="text-xs text-gray-500">Encounter</p>
                  <p className="text-sm font-medium text-gray-900">
                    {bill.attributes.patient || "—"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Bill Type</p>
                  <p className="text-sm font-medium text-gray-900">
                    {bill.attributes.billType || "ENCOUNTER"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Total Charge</p>
                  <p className="text-sm font-semibold text-gray-900">
                    {bill.attributes.currency}{" "}
                    {(bill.attributes.totalCharge ?? 0).toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                    })}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Total Payable</p>
                  <p className="text-sm font-semibold text-blue-600">
                    {bill.attributes.currency}{" "}
                    {bill.attributes.amount.toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                    })}
                  </p>
                </div>
              </div>
            </div>

            {/* Bill Actions */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="text-sm font-medium text-gray-900 mb-3">Actions</h3>

              {billAction.isError && (
                <div className="mb-3 p-3 rounded-lg bg-red-50 border border-red-200">
                  <p className="text-sm text-red-700">
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
                    className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
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
                      className="px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 w-64"
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
                  <button
                    onClick={() => billAction.mutate({ action: "finalize" })}
                    disabled={billAction.isPending}
                    className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-purple-600 rounded-lg hover:bg-purple-700 disabled:opacity-50 transition-colors"
                  >
                    {billAction.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Lock className="w-4 h-4" />}
                    Finalize Bill
                  </button>
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
                    <div className="pt-3 border-t border-gray-200">
                      <p className="text-xs font-medium text-gray-600 mb-2">Create Payment Intent</p>
                      <div className="flex items-center gap-2">
                        <select
                          value={paymentType}
                          onChange={(e) => setPaymentType(e.target.value)}
                          className="px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
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
                          placeholder="Amount"
                          value={paymentAmount}
                          onChange={(e) => setPaymentAmount(e.target.value)}
                          className="px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 w-36"
                        />
                        <button
                          onClick={() => createPayment.mutate({ paymentType, amount: paymentAmount })}
                          disabled={createPayment.isPending || !paymentAmount}
                          className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-emerald-600 rounded-lg hover:bg-emerald-700 disabled:opacity-50 transition-colors"
                        >
                          {createPayment.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <CreditCard className="w-4 h-4" />}
                          Create Payment
                        </button>
                      </div>
                      {createPayment.isError && (
                        <p className="mt-2 text-xs text-red-600">Failed to create payment intent.</p>
                      )}
                      {createPayment.isSuccess && (
                        <p className="mt-2 text-xs text-green-600">Payment intent created successfully.</p>
                      )}
                    </div>
                  </div>
                )}

                {status === "VOID" && (
                  <p className="text-sm text-gray-500">This bill has been voided. No actions available.</p>
                )}

                {!["DRAFT", "ACCUMULATING", "APPROVAL_PENDING", "APPROVED", "FINAL", "VOID"].includes(status) && (
                  <p className="text-sm text-gray-500">No actions available for this bill status.</p>
                )}
              </div>
            </div>

            {/* Line Items */}
            <div className="bg-white rounded-lg border border-gray-200">
              <div className="px-5 py-4 border-b">
                <h3 className="text-sm font-medium text-gray-900">Line Items</h3>
              </div>
              {!bill.attributes.lineItems || bill.attributes.lineItems.length === 0 ? (
                <div className="p-8 text-center">
                  <FileText className="w-8 h-8 text-gray-300 mx-auto mb-2" />
                  <p className="text-gray-400 text-sm">No line items posted yet</p>
                  <p className="text-gray-400 text-xs mt-1">
                    Line items are added as orders and services are recorded during the encounter.
                  </p>
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Code</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Description</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Kind</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Qty</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Unit Price</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Amount</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {bill.attributes.lineItems.map((line, idx) => (
                      <tr key={idx} className="hover:bg-gray-50 transition-colors">
                        <td className="px-4 py-3 font-mono text-xs text-gray-900">
                          {line.msikaCode || "—"}
                        </td>
                        <td className="px-4 py-3 text-gray-600">
                          {line.description || "—"}
                        </td>
                        <td className="px-4 py-3">
                          <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-gray-100 text-gray-600">
                            {line.kind || "—"}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right text-gray-600">
                          {line.qty ?? 0}
                        </td>
                        <td className="px-4 py-3 text-right font-mono text-gray-600">
                          {(line.unitPrice ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                        <td className="px-4 py-3 text-right font-mono text-gray-900">
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
              <div className="bg-white rounded-lg border border-gray-200">
                <div className="px-5 py-4 border-b">
                  <h3 className="text-sm font-medium text-gray-900">Party Allocations</h3>
                </div>
                <div className="divide-y divide-gray-100">
                  {bill.attributes.parties.map((party, idx) => (
                    <div key={idx} className="px-5 py-3 flex items-center justify-between">
                      <div>
                        <span className="text-sm font-medium text-gray-900">
                          {party.partyType || "Unknown"}
                        </span>
                        {party.partyRef && (
                          <span className="ml-2 text-xs text-gray-500">
                            Ref: {party.partyRef}
                          </span>
                        )}
                      </div>
                      <span className="text-sm font-mono font-semibold text-gray-900">
                        {bill.attributes.currency}{" "}
                        {(party.amount ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Payments */}
            <div className="bg-white rounded-lg border border-gray-200">
              <div className="px-5 py-4 border-b flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <DollarSign className="w-4 h-4 text-gray-500" />
                  <h3 className="text-sm font-medium text-gray-900">Payments</h3>
                  {payments.length > 0 && (
                    <span className="text-xs text-gray-400">({payments.length})</span>
                  )}
                </div>
                <button
                  onClick={() => refetchPayments()}
                  className="inline-flex items-center gap-1 px-2 py-1 text-xs text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded transition-colors"
                  title="Refresh payment status"
                >
                  <RefreshCw className="w-3 h-3" />
                  Refresh
                </button>
              </div>
              {payments.length === 0 ? (
                <div className="p-8 text-center">
                  <CreditCard className="w-8 h-8 text-gray-300 mx-auto mb-2" />
                  <p className="text-gray-400 text-sm">No payments recorded</p>
                  <p className="text-gray-400 text-xs mt-1">
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
                          <p className="text-sm font-medium text-gray-900">
                            {payment.attributes.paymentNumber}
                          </p>
                          <p className="text-xs text-gray-500">
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
                            PAYMENT_STATUS_STYLES[payment.attributes.status] ?? "bg-gray-100 text-gray-600"
                          }`}
                        >
                          {payment.attributes.status}
                        </span>
                        <span className="text-sm font-mono font-semibold text-gray-900">
                          {payment.attributes.currency}{" "}
                          {payment.attributes.amount.toLocaleString(undefined, {
                            minimumFractionDigits: 2,
                          })}
                        </span>
                        {payment.attributes.status === "PENDING" && (
                          <button
                            onClick={() => cancelPayment.mutate(payment.id)}
                            disabled={cancelPayment.isPending}
                            className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-red-600 bg-red-50 rounded-lg hover:bg-red-100 disabled:opacity-50 transition-colors"
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
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
