"use client";

/**
 * ABAC Policy Management — Policy list with resource types and effects.
 * Route: /admin/policies | pageTitle: "Policy Management"
 */

import Link from "next/link";
import { ArrowLeft, Loader2, ScrollText, AlertCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface PolicyResource {
  id: string;
  type: "policy";
  attributes: {
    name: string;
    resourceType: string;
    action: string;
    effect: string;
    conditionCount: number;
    [key: string]: unknown;
  };
}

type PoliciesResponse = ApiResponse<PolicyResource[]>;

function usePolicies() {
  return useQuery<PoliciesResponse>({
    queryKey: ["admin-policies"],
    queryFn: () => apiClient.get<PoliciesResponse>("/internal/v1/admin/policies"),
  });
}

const EFFECT_STYLES: Record<string, string> = {
  ALLOW: "bg-green-100 text-green-700",
  DENY: "bg-red-100 text-red-700",
};

export default function PoliciesPage() {
  const { data, isLoading, error } = usePolicies();

  const policies = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Policy Management"
        subtitle="Configure ABAC policies and access rules"
      >
        <div className="mb-4">
          <Link
            href="/admin"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to administration
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load policies</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading policies...</span>
          </div>
        ) : policies.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <ScrollText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No policies configured</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Policy Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Resource Type</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Action</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Effect</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Conditions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {policies.map((policy) => {
                  const effectStyle =
                    EFFECT_STYLES[policy.attributes.effect] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={policy.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {policy.attributes.name}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-purple-100 text-purple-700">
                          {policy.attributes.resourceType}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {policy.attributes.action}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${effectStyle}`}
                        >
                          {policy.attributes.effect}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {policy.attributes.conditionCount} condition(s)
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
