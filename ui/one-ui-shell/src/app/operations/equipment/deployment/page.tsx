"use client";

/** Field deployment kits — prepare/deploy/return with loss/damage capture. */

import { useState } from "react";
import { Loader2, Truck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  equipmentItems, useKits, useCreateKit, useReturnKit } from "@/hooks/queries/useEquipment";



export default function DeploymentPage() {
  const kits = useKits();
  const create = useCreateKit();
  const ret = useReturnKit();
  const [name, setName] = useState("");

  const rows = equipmentItems(kits.data);

  return (
    <AppLayout>
      <PageShell title="Field Deployment Kits" subtitle="Outreach/emergency kits — prepare, deploy, return & reconcile" icon={<Truck className="h-6 w-6" />}>
        <div className="space-y-6">
          <div className="rounded-lg border border-border bg-card p-4 flex flex-wrap items-end gap-2">
            <label className="text-sm flex-1 min-w-[200px]">
              <span className="text-muted-foreground">Kit name</span>
              <input value={name} onChange={(e) => setName(e.target.value)} className="mt-1 w-full rounded-md border border-border px-3 py-2" />
            </label>
            <button
              disabled={!name.trim() || create.isPending}
              onClick={() => create.mutate({ name: name.trim() }, { onSuccess: () => setName("") })}
              className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
            >
              {create.isPending ? "Creating…" : "Prepare kit"}
            </button>
          </div>

          {kits.isLoading ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading kits…</p>
          ) : rows.length === 0 ? (
            <p className="text-sm text-muted-foreground">No deployment kits yet.</p>
          ) : (
            <ul className="space-y-2">
              {rows.map((k) => {
                const id = String(k.kit_id);
                return (
                  <li key={id} className="rounded-lg border border-border bg-card p-4 flex items-center justify-between">
                    <span className="font-medium text-foreground">{String(k.name)} · <span className="text-muted-foreground">{String(k.status)}</span></span>
                    {k.status !== "RETURNED" && k.status !== "RECONCILED" && (
                      <button disabled={ret.isPending} onClick={() => ret.mutate({ kitId: id, body: { items: [] } })} className="rounded-md border border-border px-2 py-1 text-xs hover:bg-neutral-50 disabled:opacity-60">Return kit</button>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
