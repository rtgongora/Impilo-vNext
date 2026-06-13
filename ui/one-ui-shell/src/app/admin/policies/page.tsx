"use client";

/**
 * ABAC Policy Management — Policy list with resource types and effects.
 * Route: /admin/policies | pageTitle: "Policy Management"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, ScrollText, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { usePolicyRules } from "@/hooks/queries/useTrustAdmin";

const EFFECT_STYLES: Record<string, string> = {
  ALLOW: "bg-green-100 text-green-700",
  DENY: "bg-red-100 text-danger",
};

export default function PoliciesPage() {
  const searchParams = useSearchParams();
  const fromOrgAdmin = searchParams.get("from") === "organization-admin";
  const { data, isLoading, error } = usePolicyRules();

  const policies = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Policy Management"
        subtitle="Configure ABAC policies and access rules"
      >
        <OrganizationPlaneContextBar />
        <div className="mb-4">
          <Link
            href={fromOrgAdmin ? "/organization-admin" : "/admin"}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {fromOrgAdmin ? "Back to organization administration" : "Back to administration"}
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load policies</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading policies...</span>
          </div>
        ) : policies.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <ScrollText className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No policies configured</p>
          </div>
        ) : (
          <div className="bg-card rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background">
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Policy Name</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Resource Type</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Action</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Effect</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Conditions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {policies.map((policy) => {
                  const effectStyle =
                    EFFECT_STYLES[policy.attributes.effect] ?? "bg-neutral-100 text-muted-foreground";
                  return (
                    <tr key={policy.id} className="hover:bg-background transition-colors">
                      <td className="px-4 py-3 font-medium text-foreground">
                        {policy.attributes.name}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-purple-100 text-warning-foreground">
                          {policy.attributes.resourceType}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {policy.attributes.action}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${effectStyle}`}
                        >
                          {policy.attributes.effect}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {policy.attributes.conditions?.length ?? 0} condition(s)
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
