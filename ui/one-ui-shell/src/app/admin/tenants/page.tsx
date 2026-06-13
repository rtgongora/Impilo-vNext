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
  INACTIVE: "bg-neutral-100 text-muted-foreground",
  SUSPENDED: "bg-red-100 text-danger",
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
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to administration
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load tenants</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading tenants...</span>
          </div>
        ) : tenants.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <Building className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No tenants configured</p>
          </div>
        ) : (
          <div className="bg-card rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background">
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Tenant Name</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Code</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Facilities</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Users</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {tenants.map((tenant) => {
                  const statusStyle =
                    STATUS_STYLES[tenant.attributes.status] ?? "bg-neutral-100 text-muted-foreground";
                  return (
                    <tr key={tenant.id} className="hover:bg-background transition-colors">
                      <td className="px-4 py-3 font-medium text-foreground">
                        {tenant.attributes.name}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                        {tenant.attributes.code}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {tenant.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {tenant.attributes.facilityCount}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
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
