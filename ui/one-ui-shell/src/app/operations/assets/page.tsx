"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { Package, Plus, Search, Wrench } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { SupplyPlaneContextBar } from "@/components/experience/SupplyPlaneContextBar";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { randomUUID } from "@/lib/uuid";

type AssetStatus = "IN_SERVICE" | "MAINTENANCE" | "RETIRED";

interface AssetRow {
  id: string;
  tag: string;
  name: string;
  type: string;
  location: string;
  status: AssetStatus;
  assignedTo: string;
}

const STATUS_STYLES: Record<AssetStatus, string> = {
  IN_SERVICE: "bg-emerald-100 text-primary-hover",
  MAINTENANCE: "bg-amber-100 text-warning-foreground",
  RETIRED: "bg-border text-foreground",
};

function uid() {
  return randomUUID();
}

export default function AssetsPage() {
  const facility = useFacilityStore((s) => s.facility);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"" | AssetStatus>("");

  const [tag, setTag] = useState("");
  const [name, setName] = useState("");
  const [type, setType] = useState("EQUIPMENT");
  const [location, setLocation] = useState("");
  const [assignedTo, setAssignedTo] = useState("");

  const [assets, setAssets] = useState<AssetRow[]>([]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return assets.filter((a) => {
      if (statusFilter && a.status !== statusFilter) return false;
      if (!q) return true;
      return (
        a.tag.toLowerCase().includes(q) ||
        a.name.toLowerCase().includes(q) ||
        a.type.toLowerCase().includes(q) ||
        a.location.toLowerCase().includes(q) ||
        a.assignedTo.toLowerCase().includes(q)
      );
    });
  }, [assets, search, statusFilter]);

  function register(e: React.FormEvent) {
    e.preventDefault();
    const loc = location.trim() || (facility ? `${facility.name} — main campus` : "Unassigned");
    setAssets((prev) => [
      {
        id: uid(),
        tag: tag.trim(),
        name: name.trim(),
        type: type.trim() || "EQUIPMENT",
        location: loc,
        status: "IN_SERVICE",
        assignedTo: assignedTo.trim() || "—",
      },
      ...prev,
    ]);
    setTag("");
    setName("");
    setType("EQUIPMENT");
    setLocation("");
    setAssignedTo("");
  }

  function setStatus(id: string, status: AssetStatus) {
    setAssets((prev) => prev.map((a) => (a.id === id ? { ...a, status } : a)));
  }

  const summary = useMemo(() => {
    const total = assets.length;
    const inService = assets.filter((a) => a.status === "IN_SERVICE").length;
    const maint = assets.filter((a) => a.status === "MAINTENANCE").length;
    const retired = assets.filter((a) => a.status === "RETIRED").length;
    return { total, inService, maint, retired };
  }, [assets]);

  return (
    <AppLayout>
      <PageShell
        title="Asset registry"
        subtitle="Register fixed assets, track location and assignment, and move items through IN_SERVICE, MAINTENANCE, and RETIRED."
        icon={<Package className="h-6 w-6" />}
      >
        <SupplyPlaneContextBar />
        <div className="mb-4">
          <Link href="/inventory" className="text-sm font-medium text-muted-foreground hover:text-foreground">
            ← Inventory dashboard
          </Link>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
          {[
            { label: "Total assets", value: summary.total },
            { label: "In service", value: summary.inService },
            { label: "Maintenance", value: summary.maint },
            { label: "Retired", value: summary.retired },
          ].map(({ label, value }) => (
            <div key={label} className="rounded-xl border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</p>
              <p className="mt-2 text-2xl font-bold text-foreground">{value}</p>
            </div>
          ))}
        </div>

        <section className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,360px)_1fr]">
          <form onSubmit={register} className="h-fit rounded-2xl border border-border bg-card p-5 shadow-sm">
            <div className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Register asset</div>
            <p className="mt-2 text-sm text-muted-foreground">
              Client-side registry until an operations assets API is available. Data clears on full page reload.
            </p>
            <div className="mt-4 space-y-3">
              <label className="block text-sm">
                <span className="mb-1 block font-medium text-foreground">Asset tag</span>
                <input value={tag} onChange={(e) => setTag(e.target.value)} required className="w-full rounded-lg border border-border px-3 py-2" placeholder="AST-00042" />
              </label>
              <label className="block text-sm">
                <span className="mb-1 block font-medium text-foreground">Name</span>
                <input value={name} onChange={(e) => setName(e.target.value)} required className="w-full rounded-lg border border-border px-3 py-2" placeholder="Patient monitor" />
              </label>
              <label className="block text-sm">
                <span className="mb-1 block font-medium text-foreground">Type</span>
                <input value={type} onChange={(e) => setType(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2" />
              </label>
              <label className="block text-sm">
                <span className="mb-1 block font-medium text-foreground">Location</span>
                <input
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                  className="w-full rounded-lg border border-border px-3 py-2"
                  placeholder={facility ? `Default: ${facility.name}` : "Building / ward / room"}
                />
              </label>
              <label className="block text-sm">
                <span className="mb-1 block font-medium text-foreground">Assigned to</span>
                <input value={assignedTo} onChange={(e) => setAssignedTo(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2" placeholder="Team or person" />
              </label>
            </div>
            <button type="submit" className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white">
              <Plus className="h-4 w-4" />
              Register asset
            </button>
          </form>

          <div className="rounded-2xl border border-border bg-card shadow-sm">
            <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="relative min-w-[200px] flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search tag, name, type, location, assignee…"
                  className="w-full rounded-lg border border-border py-2 pl-9 pr-3 text-sm"
                />
              </div>
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as "" | AssetStatus)} className="rounded-lg border border-border px-3 py-2 text-sm">
                <option value="">All statuses</option>
                <option value="IN_SERVICE">IN_SERVICE</option>
                <option value="MAINTENANCE">MAINTENANCE</option>
                <option value="RETIRED">RETIRED</option>
              </select>
            </div>

            {filtered.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-2 p-12 text-center text-sm text-muted-foreground">
                <Package className="h-10 w-10 text-muted-foreground" />
                <p>No assets match the current filters. Register an asset to begin.</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[900px] text-sm">
                  <thead className="bg-background text-left text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2 font-medium">Tag</th>
                      <th className="px-3 py-2 font-medium">Name</th>
                      <th className="px-3 py-2 font-medium">Type</th>
                      <th className="px-3 py-2 font-medium">Location</th>
                      <th className="px-3 py-2 font-medium">Status</th>
                      <th className="px-3 py-2 font-medium">Assigned to</th>
                      <th className="px-3 py-2 font-medium text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map((a) => (
                      <tr key={a.id} className="border-t border-border">
                        <td className="px-3 py-2 font-mono text-xs text-foreground">{a.tag}</td>
                        <td className="px-3 py-2 font-medium text-foreground">{a.name}</td>
                        <td className="px-3 py-2 text-muted-foreground">{a.type}</td>
                        <td className="px-3 py-2 text-muted-foreground">{a.location}</td>
                        <td className="px-3 py-2">
                          <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_STYLES[a.status]}`}>{a.status}</span>
                        </td>
                        <td className="px-3 py-2 text-muted-foreground">{a.assignedTo}</td>
                        <td className="px-3 py-2 text-right">
                          <div className="inline-flex flex-wrap justify-end gap-1">
                            <button
                              type="button"
                              disabled={a.status === "IN_SERVICE"}
                              onClick={() => setStatus(a.id, "IN_SERVICE")}
                              className="rounded border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-background disabled:opacity-40"
                            >
                              In service
                            </button>
                            <button
                              type="button"
                              disabled={a.status === "MAINTENANCE"}
                              onClick={() => setStatus(a.id, "MAINTENANCE")}
                              className="inline-flex items-center gap-1 rounded border border-warning/35 bg-warning-soft px-2 py-1 text-xs font-medium text-warning-foreground hover:bg-amber-100 disabled:opacity-40"
                            >
                              <Wrench className="h-3 w-3" />
                              Maintain
                            </button>
                            <button
                              type="button"
                              disabled={a.status === "RETIRED"}
                              onClick={() => setStatus(a.id, "RETIRED")}
                              className="rounded border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-neutral-100 disabled:opacity-40"
                            >
                              Retire
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </section>
      </PageShell>
    </AppLayout>
  );
}
