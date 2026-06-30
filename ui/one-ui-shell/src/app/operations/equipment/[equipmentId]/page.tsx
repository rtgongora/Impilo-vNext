"use client";

/** Equipment detail / profile — status, custody, maintenance, calibration, faults, IoT alerts. */

import { useParams } from "next/navigation";
import { useState } from "react";
import { AlertTriangle, Loader2, ShieldAlert, Wrench } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import {
  useEquipmentDetail,
  useChangeEquipmentStatus,
  useReportFault,
} from "@/hooks/queries/useEquipment";

type Detail = {
  equipment?: Record<string, unknown>;
  history?: Record<string, unknown>[];
  maintenance?: Record<string, unknown>[];
  calibrations?: Record<string, unknown>[];
  faults?: Record<string, unknown>[];
  transfers?: Record<string, unknown>[];
  alerts?: Record<string, unknown>[];
};

export default function EquipmentDetailPage() {
  const params = useParams<{ equipmentId: string }>();
  const id = params?.equipmentId;
  const detail = useEquipmentDetail(id);
  const changeStatus = useChangeEquipmentStatus();
  const reportFault = useReportFault();

  const [faultSummary, setFaultSummary] = useState("");
  const [safety, setSafety] = useState(false);

  const d = (detail.data ?? {}) as Detail;
  const eq = d.equipment ?? {};

  if (detail.isLoading) {
    return (
      <AppLayout>
        <PageShell title="Equipment" icon={<Wrench className="h-6 w-6" />}>
          <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell
        title={String(eq.name ?? "Equipment")}
        subtitle={`${eq.equipment_type ?? ""} · ${eq.status ?? ""} · ${eq.criticality ?? ""}`}
        icon={<Wrench className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <Info label="Status" value={String(eq.status ?? "—")} />
            <Info label="Tag / Serial" value={String(eq.tag ?? eq.serial_number ?? "—")} />
            <Info label="Custodian" value={String(eq.custodian_ref ?? "—")} />
            <Info label="Facility" value={String(eq.facility_id ?? "—")} />
            <Info label="Device link" value={String(eq.device_id ?? "Not linked")} />
            <Info label="Next calibration" value={String(eq.next_calibration ?? "—")} />
          </div>

          {/* Status actions — real CTAs */}
          <div className="rounded-lg border border-border bg-card p-4 space-y-3">
            <h3 className="font-semibold text-foreground">Status & safety</h3>
            <div className="flex flex-wrap gap-2">
              {["OPERATIONAL", "MAINTENANCE", "UNSAFE"].map((st) => (
                <button
                  key={st}
                  disabled={changeStatus.isPending || !id}
                  onClick={() => changeStatus.mutate({ equipmentId: id!, body: { status: st } })}
                  className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-neutral-50 disabled:opacity-60"
                >
                  Mark {st}
                </button>
              ))}
            </div>
          </div>

          {/* Report fault */}
          <div className="rounded-lg border border-border bg-card p-4 space-y-3">
            <h3 className="font-semibold text-foreground flex items-center gap-2"><ShieldAlert className="h-4 w-4" /> Report fault</h3>
            <input value={faultSummary} onChange={(e) => setFaultSummary(e.target.value)} placeholder="Fault summary" className="w-full rounded-md border border-border bg-card px-3 py-2 text-sm" />
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={safety} onChange={(e) => setSafety(e.target.checked)} /> Safety concern (routes to Rito)
            </label>
            <button
              disabled={!faultSummary.trim() || reportFault.isPending || !id}
              onClick={() =>
                reportFault.mutate(
                  { equipmentId: id!, body: { summary: faultSummary.trim(), severity: safety ? "SAFETY" : "MINOR", safetyConcern: safety } },
                  { onSuccess: () => { setFaultSummary(""); setSafety(false); } },
                )
              }
              className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
            >
              {reportFault.isPending ? "Submitting…" : "Submit fault"}
            </button>
          </div>

          <Section title="Open faults" rows={d.faults} render={(f) => `${f.severity} · ${f.summary} · ${f.status}`} />
          <Section title="Maintenance" rows={d.maintenance} render={(m) => `${m.task_type} · ${m.title} · ${m.status}`} />
          <Section title="Calibration history" rows={d.calibrations} render={(c) => `${c.outcome} · ${c.performed_on} · next ${c.next_due ?? "—"}`} />
          <Section title="IoT alerts" rows={d.alerts} render={(a) => `${a.alert_type} · ${a.severity} · ${a.status}`} />
          <Section title="Lifecycle history" rows={d.history} render={(h) => `${h.event_type} · ${h.to ?? ""} · ${h.occurred_at}`} />

          <NompiloContextualGuidance routePath="/operations/equipment" />
        </div>
      </PageShell>
    </AppLayout>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-card p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium text-foreground">{value}</p>
    </div>
  );
}

function Section({ title, rows, render }: { title: string; rows?: Record<string, unknown>[]; render: (r: Record<string, unknown>) => string }) {
  const list = rows ?? [];
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <h3 className="mb-2 font-semibold text-foreground flex items-center gap-2"><AlertTriangle className="h-4 w-4 text-muted-foreground" /> {title}</h3>
      {list.length === 0 ? (
        <p className="text-sm text-muted-foreground">None.</p>
      ) : (
        <ul className="space-y-1 text-sm">
          {list.map((r, i) => (
            <li key={i} className="text-foreground">{render(r)}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
