"use client";

/**
 * Equipment & device registry — WS#5 assets/devices/equipment/IoT.
 * Wired to asset-registry-service via the experience-bff equipment lane.
 * Route: /operations/equipment | Zone: operations | Guard: role ADMIN
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Loader2, Plus, Search, Wrench } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import {
  equipmentItems,
  useEquipmentList,
  useRegisterEquipment,
  type EquipmentRow,
} from "@/hooks/queries/useEquipment";
import { useFacilityStore } from "@/hooks/useFacilityStore";



const STATUS_STYLE: Record<string, string> = {
  OPERATIONAL: "bg-emerald-100 text-primary-hover",
  AVAILABLE: "bg-emerald-100 text-primary-hover",
  IN_USE: "bg-sky-100 text-sky-800",
  MAINTENANCE: "bg-amber-100 text-warning-foreground",
  UNSAFE: "bg-red-100 text-red-800",
  BROKEN: "bg-red-100 text-red-800",
  LOST: "bg-neutral-200 text-foreground",
  RETIRED: "bg-border text-foreground",
};

export default function EquipmentPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id ?? "";

  const list = useEquipmentList({ facilityId: facilityId || undefined, size: 100 });
  const register = useRegisterEquipment();

  const [search, setSearch] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    name: "",
    equipmentType: "",
    serialNumber: "",
    tag: "",
    criticality: "STANDARD",
    assetClass: "INFRASTRUCTURE",
  });

  const rows = useMemo(() => equipmentItems(list.data) as unknown as EquipmentRow[], [list.data]);
  const filtered = useMemo(() => {
    const s = search.trim().toLowerCase();
    if (!s) return rows;
    return rows.filter(
      (r) =>
        r.name?.toLowerCase().includes(s) ||
        r.equipment_type?.toLowerCase().includes(s) ||
        (r.serial_number ?? "").toLowerCase().includes(s) ||
        (r.tag ?? "").toLowerCase().includes(s),
    );
  }, [rows, search]);

  const counts = useMemo(() => {
    const total = rows.length;
    const operational = rows.filter((r) => ["OPERATIONAL", "AVAILABLE", "IN_USE"].includes(r.status)).length;
    const maintenance = rows.filter((r) => r.status === "MAINTENANCE").length;
    const unsafe = rows.filter((r) => ["UNSAFE", "BROKEN"].includes(r.status)).length;
    return { total, operational, maintenance, unsafe };
  }, [rows]);

  const canSubmit = form.name.trim() && form.equipmentType.trim() && !!facilityId;

  function submit() {
    if (!canSubmit) return;
    register.mutate(
      {
        facilityId,
        name: form.name.trim(),
        equipmentType: form.equipmentType.trim().toUpperCase(),
        serialNumber: form.serialNumber.trim() || undefined,
        tag: form.tag.trim() || undefined,
        criticality: form.criticality,
        assetClass: form.assetClass,
      },
      {
        onSuccess: () => {
          setShowForm(false);
          setForm({ name: "", equipmentType: "", serialNumber: "", tag: "", criticality: "STANDARD", assetClass: "INFRASTRUCTURE" });
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Equipment & Devices"
        subtitle="Operational registry: criticality, custody, maintenance, calibration, IoT status"
        icon={<Wrench className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <Kpi label="Total Equipment" value={counts.total} />
            <Kpi label="Operational" value={counts.operational} />
            <Kpi label="Under Maintenance" value={counts.maintenance} alert={counts.maintenance > 0} />
            <Kpi label="Unsafe / Broken" value={counts.unsafe} alert={counts.unsafe > 0} />
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[220px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search name, type, serial or tag"
                className="w-full rounded-md border border-border bg-card pl-9 pr-3 py-2 text-sm"
              />
            </div>
            <Link href="/operations/equipment/maintenance" className="rounded-md border border-border px-3 py-2 text-sm font-medium hover:bg-neutral-50">
              Maintenance
            </Link>
            <Link href="/operations/equipment/calibration" className="rounded-md border border-border px-3 py-2 text-sm font-medium hover:bg-neutral-50">
              Calibration
            </Link>
            <Link href="/operations/equipment/readiness" className="rounded-md border border-border px-3 py-2 text-sm font-medium hover:bg-neutral-50">
              Readiness
            </Link>
            <button
              onClick={() => setShowForm((v) => !v)}
              className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:bg-primary-hover"
            >
              <Plus className="h-4 w-4" /> Register
            </button>
          </div>

          {!facilityId && (
            <div className="rounded-md border border-warning/35 bg-amber-50 px-4 py-3 text-sm text-warning-foreground">
              Select a facility context to register and scope equipment.
            </div>
          )}

          {showForm && (
            <div className="rounded-lg border border-border bg-card p-4 space-y-3">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <Field label="Name" value={form.name} onChange={(v) => setForm({ ...form, name: v })} />
                <Field label="Type" value={form.equipmentType} onChange={(v) => setForm({ ...form, equipmentType: v })} placeholder="e.g. VENTILATOR" />
                <Field label="Serial #" value={form.serialNumber} onChange={(v) => setForm({ ...form, serialNumber: v })} />
                <Field label="Asset tag" value={form.tag} onChange={(v) => setForm({ ...form, tag: v })} />
                <Select label="Criticality" value={form.criticality} onChange={(v) => setForm({ ...form, criticality: v })} options={["STANDARD", "ELEVATED", "CRITICAL"]} />
                <Select label="Class" value={form.assetClass} onChange={(v) => setForm({ ...form, assetClass: v })} options={["INFRASTRUCTURE", "CLINICAL", "PUBLIC_HEALTH", "EMERGENCY", "REGULATED"]} />
              </div>
              {register.isError && (
                <p className="text-sm text-red-700">Registration failed (duplicate serial/tag or validation). Check inputs.</p>
              )}
              <div className="flex gap-2">
                <button disabled={!canSubmit || register.isPending} onClick={submit} className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60">
                  {register.isPending ? "Registering…" : "Save equipment"}
                </button>
                <button onClick={() => setShowForm(false)} className="rounded-md border border-border px-3 py-2 text-sm">Cancel</button>
              </div>
            </div>
          )}

          <div className="rounded-lg border border-border bg-card overflow-hidden">
            {list.isLoading ? (
              <div className="flex items-center gap-2 p-6 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading equipment…</div>
            ) : list.isError ? (
              <div className="p-6 text-sm text-red-700">Could not load equipment registry.</div>
            ) : filtered.length === 0 ? (
              <div className="p-6 text-sm text-muted-foreground">No equipment found for this facility.</div>
            ) : (
              <table className="w-full text-sm">
                <thead className="bg-neutral-50 text-left text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-4 py-2">Name</th>
                    <th className="px-4 py-2">Type</th>
                    <th className="px-4 py-2">Tag / Serial</th>
                    <th className="px-4 py-2">Criticality</th>
                    <th className="px-4 py-2">Status</th>
                    <th className="px-4 py-2"></th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((r) => (
                    <tr key={r.equipment_id} className="border-t border-border">
                      <td className="px-4 py-2 font-medium text-foreground">{r.name}</td>
                      <td className="px-4 py-2">{r.equipment_type}</td>
                      <td className="px-4 py-2 text-muted-foreground">{r.tag || r.serial_number || "—"}</td>
                      <td className="px-4 py-2">
                        {r.criticality === "CRITICAL" ? (
                          <span className="inline-flex items-center gap-1 text-red-700"><AlertTriangle className="h-3 w-3" /> {r.criticality}</span>
                        ) : (
                          r.criticality
                        )}
                      </td>
                      <td className="px-4 py-2">
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[r.status] ?? "bg-border text-foreground"}`}>{r.status}</span>
                      </td>
                      <td className="px-4 py-2 text-right">
                        <Link href={`/operations/equipment/${r.equipment_id}`} className="text-sm font-medium text-primary hover:underline">Open</Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <NompiloContextualGuidance routePath="/operations/equipment" />
        </div>
      </PageShell>
    </AppLayout>
  );
}

function Kpi({ label, value, alert }: { label: string; value: number; alert?: boolean }) {
  return (
    <div className={`rounded-lg border bg-card p-4 ${alert ? "border-warning/35" : "border-border"}`}>
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">{label}</p>
        {alert && <AlertTriangle className="h-4 w-4 text-amber-500" />}
      </div>
      <p className="text-2xl font-bold text-foreground">{value}</p>
    </div>
  );
}

function Field({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <label className="block text-sm">
      <span className="text-muted-foreground">{label}</span>
      <input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} className="mt-1 w-full rounded-md border border-border bg-card px-3 py-2" />
    </label>
  );
}

function Select({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: string[] }) {
  return (
    <label className="block text-sm">
      <span className="text-muted-foreground">{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)} className="mt-1 w-full rounded-md border border-border bg-card px-3 py-2">
        {options.map((o) => (
          <option key={o} value={o}>{o}</option>
        ))}
      </select>
    </label>
  );
}
