"use client";

/**
 * Facility Registry — Browse and search registered facilities.
 * Route: /registry/facilities
 */

import { useState } from "react";
import Link from "next/link";
import { Loader2, AlertTriangle, Building2, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilities } from "@/hooks/queries/useFacilities";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-gray-100 text-gray-700",
  SUSPENDED: "bg-red-100 text-red-700",
};

export default function FacilityRegistryPage() {
  const [searchTerm, setSearchTerm] = useState("");
  const { data, isLoading, error } = useFacilities({
    search: searchTerm || undefined,
  });

  const facilities = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Facility Registry" subtitle="Browse registered healthcare facilities">
        {/* Search */}
        <div className="mb-6">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search facilities..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading facilities...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load facilities</p>
          </div>
        ) : facilities.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Building2 className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No facilities found</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Facility Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Code</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">District</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Action</th>
                </tr>
              </thead>
              <tbody>
                {facilities.map((facility) => {
                  const attrs = facility.attributes as Record<string, unknown>;
                  const statusStyle = STATUS_STYLES[facility.attributes.status] ?? "bg-gray-100 text-gray-700";
                  return (
                    <tr key={facility.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {facility.attributes.name}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-600">
                        {facility.attributes.code}
                      </td>
                      <td className="px-4 py-3 text-gray-600">{facility.attributes.facilityType}</td>
                      <td className="px-4 py-3 text-gray-600">
                        {(attrs.district as string) || "\u2014"}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                          {facility.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Link
                          href={`/registry/facilities/${facility.id}`}
                          className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                        >
                          View
                        </Link>
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
