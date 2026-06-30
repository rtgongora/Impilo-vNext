"use client";

/** Calibration dashboard — due list + record a calibration outcome (FAIL flags unsafe). */

import { useState } from "react";
import { Gauge, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import {
  equipmentItems, useCalibrationDue, useRecordCalibration } from "@/hooks/queries/useEquipment";



export default function CalibrationPage() {
  const due = useCalibrationDue();
  const record = useRecordCalibration();
  const [active, setActive] = useState<string | null>(null);
  const [outcome, setOutcome] = useState("PASS");
  const [nextDue, setNextDue] = useState("");

  const rows = equipmentItems(due.data);

  return (
    <AppLayout>
      <PageShell title="Calibration" subtitle="Calibration-due schedule and outcome recording" icon={<Gauge className="h-6 w-6" />}>
        <div className="space-y-6">
          <div className="rounded-lg border border-border bg-card p-4">
            <h3 className="mb-3 font-semibold text-foreground">Calibration due</h3>
            {due.isLoading ? (
              <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</p>
            ) : rows.length === 0 ? (
              <p className="text-sm text-muted-foreground">No equipment past its calibration date.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {rows.map((r) => {
                  const id = String(r.equipment_id);
                  return (
                    <li key={id} className="rounded-md border border-border p-3">
                      <div className="flex items-center justify-between">
                        <span className="font-medium text-foreground">{String(r.name)} · due {String(r.next_calibration ?? "—")}</span>
                        <button onClick={() => setActive(active === id ? null : id)} className="rounded-md border border-border px-2 py-1 text-xs hover:bg-neutral-50">Record</button>
                      </div>
                      {active === id && (
                        <div className="mt-3 flex flex-wrap items-end gap-2">
                          <label className="text-xs">Outcome
                            <select value={outcome} onChange={(e) => setOutcome(e.target.value)} className="mt-1 block rounded-md border border-border px-2 py-1">
                              {["PASS", "FAIL", "ADJUSTED"].map((o) => <option key={o}>{o}</option>)}
                            </select>
                          </label>
                          <label className="text-xs">Next due
                            <input type="date" value={nextDue} onChange={(e) => setNextDue(e.target.value)} className="mt-1 block rounded-md border border-border px-2 py-1" />
                          </label>
                          <button
                            disabled={record.isPending}
                            onClick={() => record.mutate({ equipmentId: id, body: { outcome, nextDue: nextDue || undefined } }, { onSuccess: () => setActive(null) })}
                            className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-60"
                          >
                            Save calibration
                          </button>
                          {outcome === "FAIL" && <span className="text-xs text-red-700">FAIL marks the equipment UNSAFE.</span>}
                        </div>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
          <NompiloContextualGuidance routePath="/operations/equipment/calibration" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
