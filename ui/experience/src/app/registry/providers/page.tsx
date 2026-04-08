"use client";

/**
 * Provider Registry — Provider table with search.
 * Route: /registry/providers | pageTitle: "Provider Registry"
 */

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Search, Loader2, UserCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useProviders } from "@/hooks/queries/useRegistry";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-gray-100 text-gray-600",
  SUSPENDED: "bg-red-100 text-red-700",
  PENDING: "bg-yellow-100 text-yellow-700",
};

export default function ProvidersPage() {
  const searchParams = useSearchParams();
  const fromRegistryAdmin = searchParams.get("from") === "registry-admin";
  const [search, setSearch] = useState("");
  const { data, isLoading } = useProviders(search ? { search } : undefined);

  const providers = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Provider Registry"
        subtitle="Registered healthcare providers"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href={fromRegistryAdmin ? "/registry-admin" : "/registry"}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {fromRegistryAdmin ? "Back to registry administration" : "Back to registry hub"}
          </Link>
        </div>

        <div className="mb-4">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search providers by name, number, or speciality..."
              className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading providers...</span>
          </div>
        ) : providers.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <UserCheck className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">
              {search ? "No providers match your search" : "No providers found"}
            </p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">
                    Provider #
                  </th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">
                    Qualification
                  </th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">
                    Speciality
                  </th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {providers.map((provider) => {
                  const statusStyle =
                    STATUS_STYLES[provider.attributes.status] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={provider.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3">
                        <Link
                          href={`/registry/providers/${provider.id}`}
                          className="font-medium text-blue-600 hover:text-blue-800 font-mono text-xs"
                        >
                          {provider.attributes.registrationNumber}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-gray-900">
                        {provider.attributes.displayName}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {(provider.attributes as Record<string, unknown>).qualification as string ??
                          "General"}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {provider.attributes.speciality}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {provider.attributes.status}
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
