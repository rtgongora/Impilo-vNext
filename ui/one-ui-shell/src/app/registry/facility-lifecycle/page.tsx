"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { REGISTRY_ENTITY_COLUMNS } from "@/lib/json-api/generic-table-columns";
/**
 * Facility regulatory dashboard + deep links (Tuso via BFF facility-registry paths).
 */
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function FacilityLifecyclePage() {
  const { data: dashRes, isLoading } = useQuery({
    queryKey: ["facility-registry-dashboard"],
    queryFn: () => apiClient.get<ApiResponse<unknown>>("/internal/v1/facility-registry/dashboard/summary"),
  });

  return (
    <AppLayout>
      <PageShell title="Facility regulatory lifecycle" subtitle="Cohort view from Tuso facility registry">
        <div className="flex flex-wrap gap-3 mb-4">
          <Link href="/registry/facilities" className="text-sm text-primary-hover underline">
            Facility list
          </Link>
          <Link href="/registry/facilities/new" className="text-sm text-primary-hover underline">
            New facility shell
          </Link>
        </div>
        {isLoading ? <p className="text-sm text-muted-foreground">Loading dashboard…</p> : null}
        {dashRes?.data ? (
          <JsonApiDataTable data={dashRes.data} columns={REGISTRY_ENTITY_COLUMNS} emptyTitle="No dashboard summary" />
        ) : (
          !isLoading && <p className="text-sm text-muted-foreground">No dashboard payload (Tuso may be offline).</p>
        )}
      </PageShell>
    </AppLayout>
  );
}
