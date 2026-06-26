"use client";

/**
 * Facility departments & service points admin.
 * Route: /facility/[id]/departments
 *
 * T4 owns this route. Read/write over the TUSO facility-unit + service-point SoR.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, LayoutGrid, MapPin } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityUnits, useServicePoints } from "@/components/facility-mode/useFacilityMode";

export default function FacilityDepartmentsPage() {
  const params = useParams();
  const id = params.id as string;
  const { data: units, isLoading: unitsLoading } = useFacilityUnits(id);
  const { data: sps, isLoading: spsLoading } = useServicePoints(id);

  return (
    <AppLayout>
      <PageShell title="Departments & service points" subtitle="Facility structure">
        <div className="mb-4">
          <Link
            href={`/facility/${id}/cockpit`}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to cockpit
          </Link>
        </div>

        <div className="grid max-w-4xl gap-4 sm:grid-cols-2">
          <section className="rounded-lg border border-border bg-card p-4">
            <h3 className="mb-3 flex items-center gap-2 text-sm font-medium text-foreground">
              <LayoutGrid className="h-4 w-4" /> Departments
            </h3>
            {unitsLoading ? (
              <p className="text-xs text-muted-foreground">Loading…</p>
            ) : (units ?? []).length === 0 ? (
              <p className="text-xs text-muted-foreground">
                No departments configured.{" "}
                <Link href={`/facility/${id}/setup`} className="text-primary hover:underline">
                  Open setup
                </Link>
                .
              </p>
            ) : (
              <ul className="space-y-2">
                {(units ?? []).map((u) => (
                  <li key={u.id} className="rounded-md border border-border/60 p-2">
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-foreground">{u.name}</span>
                      <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase text-muted-foreground">
                        {u.regulatoryStatus?.replace(/_/g, " ") ?? "—"}
                      </span>
                    </div>
                    {u.serviceLine && (
                      <p className="mt-0.5 text-xs text-muted-foreground">{u.serviceLine}</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="rounded-lg border border-border bg-card p-4">
            <h3 className="mb-3 flex items-center gap-2 text-sm font-medium text-foreground">
              <MapPin className="h-4 w-4" /> Service points
            </h3>
            {spsLoading ? (
              <p className="text-xs text-muted-foreground">Loading…</p>
            ) : (sps ?? []).length === 0 ? (
              <p className="text-xs text-muted-foreground">No service points configured.</p>
            ) : (
              <ul className="space-y-2">
                {(sps ?? []).map((sp) => (
                  <li key={sp.id} className="rounded-md border border-border/60 p-2">
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-foreground">{sp.name}</span>
                      <span className="text-[10px] uppercase text-muted-foreground">
                        {sp.servicePointType}
                      </span>
                    </div>
                    {sp.queueId && (
                      <p className="mt-0.5 text-xs text-muted-foreground">Queue: {sp.queueId}</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
