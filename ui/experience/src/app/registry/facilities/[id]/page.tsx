"use client";

/**
 * Facility Detail — View facility profile, capabilities, and workspaces.
 * Route: /registry/facilities/[id]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, ArrowLeft, Building2, Users, LayoutGrid, Pencil } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { FacilityResource } from "@/hooks/queries/useFacilities";

export default function FacilityDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useQuery<ApiResponse<FacilityResource>>({
    queryKey: ["facilities", id],
    queryFn: () => apiClient.get<ApiResponse<FacilityResource>>(`/internal/v1/facilities/${id}`),
    enabled: !!id,
  });

  const facility = data?.data;
  const attrs = facility?.attributes as Record<string, unknown> | undefined;

  return (
    <AppLayout>
      <PageShell title="Facility Profile" subtitle="Facility details and capabilities">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <Link
            href="/registry/facilities"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Facilities
          </Link>
          <Link
            href={`/registry/facilities/${id}/edit`}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-blue-700 bg-blue-50 border border-blue-200 rounded-lg hover:bg-blue-100 transition-colors"
          >
            <Pencil className="w-4 h-4" />
            Edit
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading facility...</span>
          </div>
        ) : error || !facility ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load facility</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            {/* Basic Info */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start gap-4 mb-4">
                <div className="w-12 h-12 rounded-lg bg-blue-100 flex items-center justify-center shrink-0">
                  <Building2 className="w-6 h-6 text-blue-600" />
                </div>
                <div>
                  <h3 className="text-lg font-medium text-gray-900">{facility.attributes.name}</h3>
                  <p className="text-sm text-gray-500">
                    {facility.attributes.code} &middot; {facility.attributes.facilityType}
                  </p>
                </div>
              </div>
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <dt className="text-gray-500">Status</dt>
                  <dd className="mt-0.5">
                    <span
                      className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                        facility.attributes.status === "ACTIVE"
                          ? "bg-green-100 text-green-700"
                          : "bg-gray-100 text-gray-700"
                      }`}
                    >
                      {facility.attributes.status}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">District</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">
                    {(attrs?.district as string) || "\u2014"}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Staff Count</dt>
                  <dd className="font-medium text-gray-900 mt-0.5 flex items-center gap-1">
                    <Users className="w-4 h-4 text-gray-400" />
                    {(attrs?.staffCount as number) ?? "\u2014"}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Workspaces</dt>
                  <dd className="font-medium text-gray-900 mt-0.5 flex items-center gap-1">
                    <LayoutGrid className="w-4 h-4 text-gray-400" />
                    {(attrs?.workspaceCount as number) ?? "\u2014"}
                  </dd>
                </div>
              </dl>
            </div>

            {/* Capabilities */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3">Capabilities</h3>
              {facility.attributes.capabilities.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {facility.attributes.capabilities.map((cap) => (
                    <span
                      key={cap}
                      className="inline-block px-3 py-1 text-xs rounded-full bg-blue-50 text-blue-700 font-medium"
                    >
                      {cap}
                    </span>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-400">No capabilities listed</p>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
