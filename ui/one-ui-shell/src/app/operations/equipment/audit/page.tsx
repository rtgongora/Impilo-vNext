"use client";

/** Asset audit / verification — open a facility audit, confirm items, flag exceptions. */

import { useState } from "react";
import { ClipboardList, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  equipmentItems, useAudits, useOpenAudit } from "@/hooks/queries/useEquipment";
import { useFacilityStore } from "@/hooks/useFacilityStore";



export default function AuditPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityRef = facility?.id ?? "";
  const audits = useAudits(facilityRef || undefined);
  const open = useOpenAudit();
  const [name, setName] = useState("");

  const rows = equipmentItems(audits.data);

  return (
    <AppLayout>
      <PageShell title="Asset Audit" subtitle="Physical verification — scan/confirm, exception capture" icon={<ClipboardList className="h-6 w-6" />}>
        <div className="space-y-6">
          {!facilityRef && (
            <div className="rounded-md border border-warning/35 bg-amber-50 px-4 py-3 text-sm text-warning-foreground">Select a facility context to run an audit.</div>
          )}

          <div className="rounded-lg border border-border bg-card p-4 flex flex-wrap items-end gap-2">
            <label className="text-sm flex-1 min-w-[200px]">
              <span className="text-muted-foreground">Audit name</span>
              <input value={name} onChange={(e) => setName(e.target.value)} className="mt-1 w-full rounded-md border border-border px-3 py-2" />
            </label>
            <button
              disabled={!facilityRef || open.isPending}
              onClick={() => open.mutate({ facilityRef, name: name.trim() || undefined }, { onSuccess: () => setName("") })}
              className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
            >
              {open.isPending ? "Opening…" : "Open audit"}
            </button>
          </div>

          {audits.isLoading ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading audits…</p>
          ) : rows.length === 0 ? (
            <p className="text-sm text-muted-foreground">No audits for this facility yet.</p>
          ) : (
            <ul className="space-y-2">
              {rows.map((a) => (
                <li key={String(a.audit_id)} className="rounded-lg border border-border bg-card p-4">
                  <span className="font-medium text-foreground">{String(a.name)} · <span className="text-muted-foreground">{String(a.status)}</span></span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
