"use client";

/** Facility/service readiness — real required-vs-available gaps, not a decorative score. */

import { ClipboardCheck, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { useReadiness } from "@/hooks/queries/useEquipment";
import { useFacilityStore } from "@/hooks/useFacilityStore";

export default function ReadinessPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityRef = facility?.id ?? "";
  const readiness = useReadiness(facilityRef || undefined);
  const data = readiness.data;

  return (
    <AppLayout>
      <PageShell title="Service Readiness" subtitle="Required equipment per service point vs operational reality" icon={<ClipboardCheck className="h-6 w-6" />}>
        <div className="space-y-6">
          {!facilityRef && (
            <div className="rounded-md border border-warning/35 bg-amber-50 px-4 py-3 text-sm text-warning-foreground">
              Select a facility context to compute readiness.
            </div>
          )}

          {readiness.isLoading ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Computing readiness…</p>
          ) : !data ? null : (
            <>
              <div className={`rounded-lg border p-4 ${data.ready ? "border-emerald-300 bg-emerald-50" : "border-red-300 bg-red-50"}`}>
                <p className="text-sm font-medium text-foreground">
                  {data.ready ? "All service points ready" : `${data.total_gaps} readiness gap(s) across service points`}
                </p>
              </div>

              {data.profiles.length === 0 ? (
                <p className="text-sm text-muted-foreground">No readiness profiles configured for this facility.</p>
              ) : (
                data.profiles.map((p) => (
                  <div key={p.service_point} className="rounded-lg border border-border bg-card p-4">
                    <div className="mb-2 flex items-center justify-between">
                      <h3 className="font-semibold text-foreground">{p.name} <span className="text-muted-foreground">({p.service_point})</span></h3>
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${p.ready ? "bg-emerald-100 text-primary-hover" : "bg-red-100 text-red-800"}`}>{p.ready ? "READY" : "GAPS"}</span>
                    </div>
                    <table className="w-full text-sm">
                      <thead className="text-left text-xs uppercase text-muted-foreground">
                        <tr><th className="py-1">Equipment type</th><th className="py-1">Required</th><th className="py-1">Available</th><th className="py-1">Gap</th></tr>
                      </thead>
                      <tbody>
                        {p.requirements.map((r) => (
                          <tr key={r.equipment_type} className="border-t border-border">
                            <td className="py-2 font-medium text-foreground">{r.equipment_type}</td>
                            <td className="py-2">{r.required}</td>
                            <td className="py-2">{r.available}</td>
                            <td className="py-2">{r.met ? <span className="text-emerald-700">OK</span> : <span className="text-red-700">{r.gap}</span>}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ))
              )}
            </>
          )}

          <NompiloContextualGuidance routePath="/operations/equipment/readiness" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
