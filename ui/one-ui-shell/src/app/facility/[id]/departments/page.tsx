"use client";

/**
 * Facility departments & service points admin.
 * Route: /facility/[id]/departments
 *
 * T4 owns this route. Read/write over the TUSO facility-unit + service-point SoR.
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, LayoutGrid, MapPin, Pencil, Check, X } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useFacilityUnits,
  useServicePoints,
  useUpdateFacilityUnit,
  useRetireFacilityUnit,
} from "@/components/facility-mode/useFacilityMode";

export default function FacilityDepartmentsPage() {
  const params = useParams();
  const id = params.id as string;
  const { data: units, isLoading: unitsLoading } = useFacilityUnits(id);
  const { data: sps, isLoading: spsLoading } = useServicePoints(id);
  const updateUnit = useUpdateFacilityUnit(id);
  const retireUnit = useRetireFacilityUnit(id);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editName, setEditName] = useState("");
  const [editServiceLine, setEditServiceLine] = useState("");

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
                {(units ?? []).map((u) => {
                  const retired =
                    u.regulatoryStatus === "VOLUNTARILY_CLOSED" || u.regulatoryStatus === "CLOSED";
                  return (
                    <li key={u.id} className="rounded-md border border-border/60 p-2">
                      {editingId === u.id ? (
                        <div className="flex flex-wrap items-center gap-2">
                          <input
                            value={editName}
                            onChange={(e) => setEditName(e.target.value)}
                            className="flex-1 rounded-md border border-border bg-background px-2 py-1 text-sm"
                          />
                          <input
                            value={editServiceLine}
                            onChange={(e) => setEditServiceLine(e.target.value)}
                            placeholder="Service line"
                            className="w-32 rounded-md border border-border bg-background px-2 py-1 text-sm"
                          />
                          <button
                            type="button"
                            aria-label="Save department"
                            disabled={!editName.trim() || updateUnit.isPending}
                            onClick={() =>
                              updateUnit.mutate(
                                {
                                  unitId: u.id,
                                  name: editName.trim(),
                                  serviceLine: editServiceLine.trim() || undefined,
                                },
                                { onSuccess: () => setEditingId(null) },
                              )
                            }
                            className="rounded p-1 text-emerald-600 hover:bg-muted disabled:opacity-50"
                          >
                            <Check className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            aria-label="Cancel edit"
                            onClick={() => setEditingId(null)}
                            className="rounded p-1 text-muted-foreground hover:bg-muted"
                          >
                            <X className="h-4 w-4" />
                          </button>
                        </div>
                      ) : (
                        <>
                          <div className="flex items-center justify-between gap-2">
                            <span
                              className={`text-sm ${retired ? "text-muted-foreground line-through" : "text-foreground"}`}
                            >
                              {u.name}
                            </span>
                            <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase text-muted-foreground">
                              {u.regulatoryStatus?.replace(/_/g, " ") ?? "—"}
                            </span>
                          </div>
                          {u.serviceLine && (
                            <p className="mt-0.5 text-xs text-muted-foreground">{u.serviceLine}</p>
                          )}
                          {!retired && (
                            <div className="mt-1 flex items-center gap-3">
                              <button
                                type="button"
                                onClick={() => {
                                  setEditingId(u.id);
                                  setEditName(u.name);
                                  setEditServiceLine(u.serviceLine ?? "");
                                }}
                                className="inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
                              >
                                <Pencil className="h-3 w-3" /> Edit
                              </button>
                              <button
                                type="button"
                                disabled={retireUnit.isPending}
                                onClick={() => retireUnit.mutate(u.id)}
                                className="text-xs font-medium text-red-600 hover:underline disabled:opacity-50"
                              >
                                Retire
                              </button>
                            </div>
                          )}
                        </>
                      )}
                    </li>
                  );
                })}
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
