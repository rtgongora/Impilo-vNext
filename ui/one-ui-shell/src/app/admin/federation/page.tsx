"use client";

/**
 * Federation — Trusted pods and federation configuration table.
 * Route: /admin/federation | pageTitle: "Federation"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, Globe, AlertCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface FederationResource {
  id: string;
  type: "federation_pod";
  attributes: {
    podName: string;
    endpoint: string;
    status: string;
    lastSynced: string;
    [key: string]: unknown;
  };
}

type FederationResponse = ApiResponse<FederationResource[]>;

function useFederation() {
  return useQuery<FederationResponse>({
    queryKey: ["admin-federation"],
    queryFn: () => apiClient.get<FederationResponse>("/internal/v1/admin/federation"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  SUSPENDED: "bg-red-100 text-red-700",
  PENDING: "bg-yellow-100 text-yellow-700",
};

export default function FederationPage() {
  const searchParams = useSearchParams();
  const fromRegistryAdmin = searchParams.get("from") === "registry-admin";
  const { data, isLoading, error } = useFederation();

  const pods = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Federation"
        subtitle="Configure identity federation and trust relationships"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href={fromRegistryAdmin ? "/registry/trust" : "/admin"}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {fromRegistryAdmin ? "Back to trust hub" : "Back to administration"}
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load federation configuration</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading federation pods...</span>
          </div>
        ) : pods.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Globe className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No federation connections</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Pod Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Endpoint</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Last Synced</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {pods.map((pod) => {
                  const statusStyle =
                    STATUS_STYLES[pod.attributes.status] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={pod.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {pod.attributes.podName}
                      </td>
                      <td className="px-4 py-3 text-gray-600 font-mono text-xs">
                        {pod.attributes.endpoint}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {pod.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                        {new Date(pod.attributes.lastSynced).toLocaleString()}
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
