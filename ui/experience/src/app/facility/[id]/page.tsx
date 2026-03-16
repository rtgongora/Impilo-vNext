"use client";

/**
 * Facility Detail — View facility info, workspaces, staff, services.
 * Route: /facility/[id]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Loader2,
  AlertTriangle,
  ArrowLeft,
  Building2,
  Users,
  LayoutGrid,
  Activity,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface FacilityDetail {
  id: string;
  type: "facility";
  attributes: {
    name: string;
    code: string;
    facilityType: string;
    status: string;
    capabilities: string[];
    district: string;
    province: string;
    staffCount: number;
    workspaceCount: number;
    bedCapacity: number;
    occupiedBeds: number;
    workspaces: Array<{
      id: string;
      name: string;
      workspaceType: string;
      status: string;
    }>;
    services: string[];
    [key: string]: unknown;
  };
}

export default function FacilityDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useQuery<ApiResponse<FacilityDetail>>({
    queryKey: ["facility-detail", id],
    queryFn: () => apiClient.get<ApiResponse<FacilityDetail>>(`/internal/v1/facilities/${id}`),
    enabled: !!id,
  });

  const facility = data?.data;

  return (
    <AppLayout>
      <PageShell title="Facility Details" subtitle="Facility information and capacity">
        <div className="mb-4">
          <Link
            href="/facility"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Facilities
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
          <div className="max-w-3xl space-y-6">
            {/* Header */}
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
              <dl className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
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
                    {facility.attributes.district || "\u2014"}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Province</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">
                    {facility.attributes.province || "\u2014"}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Staff</dt>
                  <dd className="font-medium text-gray-900 mt-0.5 flex items-center gap-1">
                    <Users className="w-4 h-4 text-gray-400" />
                    {facility.attributes.staffCount ?? "\u2014"}
                  </dd>
                </div>
              </dl>
            </div>

            {/* Capacity Stats */}
            <div className="grid grid-cols-3 gap-4">
              <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
                <LayoutGrid className="w-6 h-6 text-blue-500 mx-auto mb-1" />
                <p className="text-xl font-bold text-gray-900">
                  {facility.attributes.workspaceCount ?? 0}
                </p>
                <p className="text-xs text-gray-500">Workspaces</p>
              </div>
              <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
                <Activity className="w-6 h-6 text-green-500 mx-auto mb-1" />
                <p className="text-xl font-bold text-gray-900">
                  {facility.attributes.bedCapacity ?? 0}
                </p>
                <p className="text-xs text-gray-500">Bed Capacity</p>
              </div>
              <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
                <Users className="w-6 h-6 text-amber-500 mx-auto mb-1" />
                <p className="text-xl font-bold text-gray-900">
                  {facility.attributes.occupiedBeds ?? 0}
                </p>
                <p className="text-xs text-gray-500">Occupied Beds</p>
              </div>
            </div>

            {/* Workspaces */}
            {(facility.attributes.workspaces?.length ?? 0) > 0 && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-3">Workspaces</h3>
                <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
                  {facility.attributes.workspaces.map((ws) => (
                    <Link
                      key={ws.id}
                      href={`/workspace/${ws.id}`}
                      className="p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
                    >
                      <p className="text-sm font-medium text-gray-900">{ws.name}</p>
                      <div className="flex items-center justify-between mt-1">
                        <span className="text-xs text-gray-500">{ws.workspaceType}</span>
                        <span
                          className={`inline-block px-1.5 py-0.5 text-xs rounded-full ${
                            ws.status === "ACTIVE"
                              ? "bg-green-100 text-green-700"
                              : "bg-gray-100 text-gray-600"
                          }`}
                        >
                          {ws.status}
                        </span>
                      </div>
                    </Link>
                  ))}
                </div>
              </div>
            )}

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

            {/* Services */}
            {(facility.attributes.services?.length ?? 0) > 0 && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-3">Services</h3>
                <div className="flex flex-wrap gap-2">
                  {facility.attributes.services.map((svc) => (
                    <span
                      key={svc}
                      className="inline-block px-3 py-1 text-xs rounded-full bg-green-50 text-green-700 font-medium"
                    >
                      {svc}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
