"use client";

/**
 * Tariff Management — Service tariff schedule table.
 * Route: /finance/tariffs | pageTitle: "Tariff Management"
 */

import Link from "next/link";
import { ArrowLeft, Loader2, BookOpen, AlertCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
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
  SUPERSEDED: "bg-blue-100 text-blue-700",
};

export default function TariffsPage() {
  const { data, isLoading, error } = useTariffs();

  const tariffs = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Tariff Management"
        subtitle="Manage service tariff schedules"
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
            <p className="text-red-600 text-sm">Failed to load tariffs</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading tariffs...</span>
          </div>
        ) : tariffs.length === 0 ? (
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
      </PageShell>
    </AppLayout>
  );
}
