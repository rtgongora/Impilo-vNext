"use client";

/** Maintenance dashboard — due list + open tasks, complete + return-to-service. */

import { Loader2, Wrench } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  equipmentItems, useMaintenanceDue, useOpenMaintenance, useCompleteMaintenance } from "@/hooks/queries/useEquipment";



export default function MaintenancePage() {
  const due = useMaintenanceDue();
  const open = useOpenMaintenance();
  const complete = useCompleteMaintenance();

  const dueRows = equipmentItems(due.data);
  const openRows = equipmentItems(open.data);

  return (
    <AppLayout>
      <PageShell title="Maintenance" subtitle="Preventive + corrective tasks, due schedule, return-to-service" icon={<Wrench className="h-6 w-6" />}>
        <div className="space-y-6">
          <div className="rounded-lg border border-border bg-card p-4">
            <h3 className="mb-3 font-semibold text-foreground">Maintenance due</h3>
            {due.isLoading ? (
              <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</p>
            ) : dueRows.length === 0 ? (
              <p className="text-sm text-muted-foreground">No equipment past its maintenance date.</p>
            ) : (
              <ul className="space-y-1 text-sm">
                {dueRows.map((r) => (
                  <li key={String(r.equipment_id)} className="text-foreground">{String(r.name)} · {String(r.equipment_type)} · due {String(r.next_maintenance ?? "—")}</li>
                ))}
              </ul>
            )}
          </div>

          <div className="rounded-lg border border-border bg-card p-4">
            <h3 className="mb-3 font-semibold text-foreground">Open tasks</h3>
            {open.isLoading ? (
              <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</p>
            ) : openRows.length === 0 ? (
              <p className="text-sm text-muted-foreground">No open maintenance tasks.</p>
            ) : (
              <table className="w-full text-sm">
                <thead className="text-left text-xs uppercase text-muted-foreground">
                  <tr><th className="py-1">Title</th><th className="py-1">Type</th><th className="py-1">Status</th><th></th></tr>
                </thead>
                <tbody>
                  {openRows.map((r) => (
                    <tr key={String(r.task_id)} className="border-t border-border">
                      <td className="py-2 font-medium text-foreground">{String(r.title)}</td>
                      <td className="py-2">{String(r.task_type)}</td>
                      <td className="py-2">{String(r.status)}</td>
                      <td className="py-2 text-right">
                        <button
                          disabled={complete.isPending}
                          onClick={() => complete.mutate({ taskId: String(r.task_id), body: { returnToService: true, serviceReport: "Completed via maintenance dashboard" } })}
                          className="rounded-md border border-border px-2 py-1 text-xs hover:bg-neutral-50 disabled:opacity-60"
                        >
                          Complete + return to service
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
