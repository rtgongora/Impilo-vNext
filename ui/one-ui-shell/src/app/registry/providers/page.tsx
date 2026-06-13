"use client";

/**
 * Provider Registry — Provider table with search.
 * Route: /registry/providers | pageTitle: "Provider Registry"
 */

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Search, Loader2, UserCheck, Plus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useProviders } from "@/hooks/queries/useRegistry";
import { ProviderRegistryOnboardingOrchestrationRail } from "@/components/registry/ProviderRegistryOnboardingOrchestrationRail";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-neutral-100 text-muted-foreground",
  SUSPENDED: "bg-red-100 text-danger",
  PENDING: "bg-yellow-100 text-yellow-700",
};

export default function ProvidersPage() {
  const searchParams = useSearchParams();
  const fromRegistryAdmin = searchParams.get("from") === "registry-admin";
  const [search, setSearch] = useState("");
  const { data, isLoading } = useProviders(search ? { search } : undefined);

  const providers = data?.data ?? [];
  const degraded = Boolean((data?.meta as { degraded?: boolean } | undefined)?.degraded);
  const guidance =
    (data?.meta as { guidance?: string } | undefined)?.guidance ??
    "Provider registry returned no rows. Use New Provider to onboard or adjust your search.";

  return (
    <AppLayout>
      <PageShell
        title="Provider Registry"
        subtitle="Registered healthcare providers"
        serviceSlug="varapi"
      >
        <RegistryPlaneContextBar />
        <ProviderRegistryOnboardingOrchestrationRail />
        <div className="mb-4">
          <Link
            href={fromRegistryAdmin ? "/registry-admin" : "/registry"}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {fromRegistryAdmin ? "Back to registry administration" : "Back to registry hub"}
          </Link>
        </div>

        <div className="mb-4 flex flex-wrap items-center justify-end gap-2">
          <Link
            href="/registry/providers/verification"
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium border border-warning/35 text-warning-foreground rounded-lg hover:bg-warning-soft transition-colors"
          >
            Verification queue
          </Link>
          <Link
            href="/registry/providers/new"
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus className="w-4 h-4" />
            New Provider
          </Link>
        </div>

        <div className="mb-4">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search providers by name, number, or speciality..."
              className="w-full pl-10 pr-4 py-2.5 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading providers...</span>
          </div>
        ) : providers.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <UserCheck className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">
              {search ? "No providers match your search" : "No providers found"}
            </p>
            {(degraded || !search) && (
              <p className="mt-3 text-sm text-warning-foreground bg-warning-soft border border-amber-100 rounded-lg px-4 py-3 max-w-lg mx-auto">
                {guidance}
              </p>
            )}
          </div>
        ) : (
          <div className="bg-card rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background">
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                    Provider #
                  </th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Name</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                    Qualification
                  </th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                    Speciality
                  </th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {providers.map((provider) => {
                  const statusStyle =
                    STATUS_STYLES[provider.attributes.status] ?? "bg-neutral-100 text-muted-foreground";
                  return (
                    <tr key={provider.id} className="hover:bg-background transition-colors">
                      <td className="px-4 py-3">
                        <Link
                          href={`/registry/providers/${provider.id}`}
                          className="font-medium text-primary hover:text-blue-800 font-mono text-xs"
                        >
                          {provider.attributes.registrationNumber}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-foreground">
                        {provider.attributes.displayName}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {(provider.attributes as Record<string, unknown>).qualification as string ??
                          "General"}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
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
