"use client";

/**
 * Claim Detail — Individual claim info, line items, adjudication history, status timeline.
 * Route: /finance/claims/[id] | pageTitle: "Claim Details"
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Loader2, FileText, AlertCircle, Clock, CheckCircle, XCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
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
  SUBMITTED: "bg-blue-100 text-blue-700",
  ADJUDICATED: "bg-yellow-100 text-yellow-700",
  PAID: "bg-green-100 text-green-700",
  REJECTED: "bg-red-100 text-red-700",
};

const STATUS_ICONS: Record<string, typeof CheckCircle> = {
  SUBMITTED: Clock,
  ADJUDICATED: Clock,
  PAID: CheckCircle,
  REJECTED: XCircle,
};

export default function ClaimDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useClaimDetail(id);
  const claim = data?.data;

  return (
    <AppLayout>
      <PageShell
        title="Claim Details"
        subtitle={claim ? `Claim ${claim.attributes.claimNumber}` : "Loading claim..."}
      >
        <div className="mb-4">
          <Link
            href="/finance/claims"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to claims
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load claim details</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading claim details...</span>
          </div>
        ) : !claim ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Claim not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Claim Summary */}
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <div className="flex items-start justify-between">
                <div>
                  <h2 className="text-lg font-semibold text-gray-900">
                    {claim.attributes.claimNumber}
                  </h2>
                  <p className="text-sm text-gray-500 mt-1">
                    Submitted {new Date(claim.attributes.date).toLocaleDateString()}
                  </p>
                </div>
                <span
                  className={`inline-block px-2.5 py-1 text-xs rounded-full font-medium ${
                    STATUS_STYLES[claim.attributes.status] ?? "bg-gray-100 text-gray-600"
                  }`}
                >
                  {claim.attributes.status}
                </span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4 pt-4 border-t">
                <div>
                  <p className="text-xs text-gray-500">Patient</p>
                  <p className="text-sm font-medium text-gray-900">{claim.attributes.patient}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Scheme</p>
                  <p className="text-sm font-medium text-gray-900">{claim.attributes.scheme}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Total Amount</p>
                  <p className="text-sm font-semibold text-gray-900">
                    {claim.attributes.currency}{" "}
                    {claim.attributes.amount.toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                    })}
                  </p>
                </div>
              </div>
            </div>

            {/* Line Items */}
            <div className="bg-white rounded-lg border border-gray-200">
              <div className="px-5 py-4 border-b">
                <h3 className="text-sm font-medium text-gray-900">Line Items</h3>
              </div>
              {!claim.attributes.lineItems || claim.attributes.lineItems.length === 0 ? (
                <div className="p-8 text-center">
                  <p className="text-gray-400 text-sm">No line items</p>
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Code</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Description</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Qty</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Unit Price</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Total</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {claim.attributes.lineItems.map((item, idx) => (
                      <tr key={idx} className="hover:bg-gray-50 transition-colors">
                        <td className="px-4 py-3 font-mono text-xs text-gray-900">{item.code}</td>
                        <td className="px-4 py-3 text-gray-600">{item.description}</td>
                        <td className="px-4 py-3 text-right text-gray-600">{item.quantity}</td>
                        <td className="px-4 py-3 text-right font-mono text-gray-600">
                          {item.unitPrice.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                        <td className="px-4 py-3 text-right font-mono text-gray-900">
                          {item.total.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Adjudication History / Status Timeline */}
            <div className="bg-white rounded-lg border border-gray-200">
              <div className="px-5 py-4 border-b">
                <h3 className="text-sm font-medium text-gray-900">Adjudication History</h3>
              </div>
              {!claim.attributes.adjudicationHistory ||
              claim.attributes.adjudicationHistory.length === 0 ? (
                <div className="p-8 text-center">
                  <p className="text-gray-400 text-sm">No adjudication events</p>
                </div>
              ) : (
                <div className="p-5">
                  <div className="space-y-4">
                    {claim.attributes.adjudicationHistory.map((event, idx) => {
                      const StatusIcon = STATUS_ICONS[event.status] ?? Clock;
                      const style =
                        STATUS_STYLES[event.status] ?? "bg-gray-100 text-gray-600";
                      return (
                        <div key={idx} className="flex gap-4">
                          <div className="flex flex-col items-center">
                            <div
                              className={`w-8 h-8 rounded-full flex items-center justify-center ${style}`}
                            >
                              <StatusIcon className="w-4 h-4" />
                            </div>
                            {idx < claim.attributes.adjudicationHistory.length - 1 && (
                              <div className="w-px h-full bg-gray-200 mt-1" />
                            )}
                          </div>
                          <div className="pb-4">
                            <div className="flex items-center gap-2">
                              <span
                                className={`inline-block px-2 py-0.5 text-xs rounded-full ${style}`}
                              >
                                {event.status}
                              </span>
                              <span className="text-xs text-gray-500">
                                {new Date(event.date).toLocaleString()}
                              </span>
                            </div>
                            <p className="text-sm text-gray-600 mt-1">{event.notes}</p>
                            <p className="text-xs text-gray-400 mt-0.5">
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
