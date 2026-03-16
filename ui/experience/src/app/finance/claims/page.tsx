"use client";

/**
 * Claims List — Insurance claims table with status tracking.
 * Route: /finance/claims | pageTitle: "Claims"
 */

import Link from "next/link";
import { ArrowLeft, Loader2, FileText, AlertCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
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
  const { data, isLoading, error } = useClaims();

  const claims = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Claims"
        subtitle="Submit and track insurance claims"
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
            <p className="text-red-600 text-sm">Failed to load claims</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading claims...</span>
          </div>
        ) : claims.length === 0 ? (
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
                          href={`/finance/claims/${claim.id}`}
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
      </PageShell>
    </AppLayout>
  );
}
