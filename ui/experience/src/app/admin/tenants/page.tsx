"use client";

/**
 * Tenant Management — Multi-tenant configuration table.
 * Route: /admin/tenants | pageTitle: "Tenant Management"
 */

import Link from "next/link";
import { ArrowLeft, Loader2, Building, AlertCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface TenantResource {
  id: string;
  type: "tenant";
  attributes: {
    name: string;
    code: string;
    status: string;
    facilityCount: number;
    userCount: number;
    [key: string]: unknown;
  };
}

type TenantsResponse = ApiResponse<TenantResource[]>;

function useTenants() {
  return useQuery<TenantsResponse>({
    queryKey: ["admin-tenants"],
    queryFn: () => apiClient.get<TenantsResponse>("/internal/v1/admin/tenants"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-gray-100 text-gray-600",
  SUSPENDED: "bg-red-100 text-red-700",
  PROVISIONING: "bg-yellow-100 text-yellow-700",
};

export default function TenantsPage() {
  const { data, isLoading, error } = useTenants();

  const tenants = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Tenant Management"
        subtitle="Multi-tenant configuration and management"
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
            <p className="text-red-600 text-sm">Failed to load tenants</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading tenants...</span>
          </div>
        ) : tenants.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Building className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No tenants configured</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Tenant Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Code</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Facilities</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Users</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {tenants.map((tenant) => {
                  const statusStyle =
                    STATUS_STYLES[tenant.attributes.status] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={tenant.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {tenant.attributes.name}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-600">
                        {tenant.attributes.code}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {tenant.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {tenant.attributes.facilityCount}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {tenant.attributes.userCount}
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
