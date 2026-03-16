"use client";

/**
 * Payments — Payment records table with status tracking.
 * Route: /finance/payments | pageTitle: "Payments"
 */

import Link from "next/link";
import { ArrowLeft, Loader2, CreditCard, AlertCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
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
  COMPLETED: "bg-green-100 text-green-700",
  PENDING: "bg-yellow-100 text-yellow-700",
  FAILED: "bg-red-100 text-red-700",
  REFUNDED: "bg-gray-100 text-gray-600",
};

export default function PaymentsPage() {
  const { data, isLoading, error } = usePayments();

  const payments = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Payments"
        subtitle="View payment records and transactions"
      >
        <div className="mb-4">
          <Link
            href="/finance"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to finance
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load payments</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading payments...</span>
          </div>
        ) : payments.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <CreditCard className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No payments recorded</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Payment #</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Payer</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Amount</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Method</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {payments.map((payment) => {
                  const statusStyle =
                    STATUS_STYLES[payment.attributes.status] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={payment.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {payment.attributes.paymentNumber}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {payment.attributes.payer}
                      </td>
                      <td className="px-4 py-3 text-right font-mono text-gray-900">
                        {payment.attributes.currency}{" "}
                        {payment.attributes.amount.toLocaleString(undefined, {
                          minimumFractionDigits: 2,
                        })}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {payment.attributes.method}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {payment.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                        {new Date(payment.attributes.date).toLocaleDateString()}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
