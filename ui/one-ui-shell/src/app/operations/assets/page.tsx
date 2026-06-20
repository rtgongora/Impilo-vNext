"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Loader2, Package, Plus, Search, Wrench } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { SupplyPlaneContextBar } from "@/components/experience/SupplyPlaneContextBar";
import { useAssets, useRetireAsset, useUpdateAssetStatus, useUpsertAsset } from "@/hooks/queries/useAssets";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { extractListFromEnvelope } from "@/lib/apiEnvelope";
import { randomUUID } from "@/lib/uuid";

type AssetStatus = "IN_SERVICE" | "MAINTENANCE" | "RETIRED";

const STATUS_STYLES: Record<AssetStatus, string> = {
  IN_SERVICE: "bg-emerald-100 text-primary-hover",
  MAINTENANCE: "bg-amber-100 text-warning-foreground",
  RETIRED: "bg-border text-foreground",
};

function readMeta(row: Record<string, unknown>, key: string): string {
  const meta = row.metadata;
  if (meta && typeof meta === "object" && !Array.isArray(meta)) {
    const v = (meta as Record<string, unknown>)[key];
    if (typeof v === "string" && v.trim()) return v;
  }
  return "";
}

function assetDisplayName(row: Record<string, unknown>): string {
  return readMeta(row, "name") || String(row.serialNo ?? row.type ?? "Asset");
}

function assetLocation(row: Record<string, unknown>): string {
  return readMeta(row, "location") || String(row.facilityRef ?? "—");
}

function assetAssigned(row: Record<string, unknown>): string {
  return readMeta(row, "assignedTo") || String(row.assignedTo ?? "—");
}

function normalizeStatus(raw: unknown): AssetStatus {
  const s = String(raw ?? "IN_SERVICE").toUpperCase();
  if (s === "MAINTENANCE" || s === "RETIRED") return s;
  return "IN_SERVICE";
}

export default function AssetsPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityRef = facility?.id ?? "";

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"" | AssetStatus>("");

  const [tag, setTag] = useState("");
  const [name, setName] = useState("");
  const [type, setType] = useState("EQUIPMENT");
  const [location, setLocation] = useState("");
  const [assignedTo, setAssignedTo] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const assetsQ = useAssets({
    facilityId: facilityRef || undefined,
    status: statusFilter || undefined,
    limit: 200,
  });
  const upsertAsset = useUpsertAsset();
  const updateStatus = useUpdateAssetStatus();
  const retireAsset = useRetireAsset();

  const rows = useMemo(() => {
    return extractListFromEnvelope(assetsQ.data).filter(
      (item): item is Record<string, unknown> => item != null && typeof item === "object",
    );
  }, [assetsQ.data]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((a) => {
      if (!q) return true;
      const hay = [
        String(a.serialNo ?? ""),
        assetDisplayName(a),
        String(a.type ?? ""),
        assetLocation(a),
        assetAssigned(a),
      ]
        .join(" ")
        .toLowerCase();
      return hay.includes(q);
    });
  }, [rows, search]);

  async function register(e: React.FormEvent) {
    e.preventDefault();
    if (!facilityRef) {
      setFormError("Select a facility context before registering assets.");
      return;
    }
    setFormError(null);
    const loc = location.trim() || (facility ? `${facility.name} — main campus` : "Unassigned");
    const assetId = randomUUID();
    try {
      await upsertAsset.mutateAsync({
        assetId,
        body: {
          facilityRef,
          type: type.trim() || "EQUIPMENT",
          serialNo: tag.trim(),
          status: "IN_SERVICE",
          metadata: {
            name: name.trim(),
            location: loc,
            assignedTo: assignedTo.trim() || undefined,
          },
        },
      });
      setTag("");
      setName("");
      setType("EQUIPMENT");
      setLocation("");
      setAssignedTo("");
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Could not register asset");
    }
  }

  async function setStatus(assetId: string, status: AssetStatus) {
    try {
      if (status === "RETIRED") {
        await retireAsset.mutateAsync(assetId);
      } else {
        await updateStatus.mutateAsync({ assetId, body: { status } });
      }
    } catch {
      /* error surfaces via mutation state if extended */
    }
  }

  const summary = useMemo(() => {
    const total = rows.length;
    const inService = rows.filter((a) => normalizeStatus(a.status) === "IN_SERVICE").length;
    const maint = rows.filter((a) => normalizeStatus(a.status) === "MAINTENANCE").length;
    const retired = rows.filter((a) => normalizeStatus(a.status) === "RETIRED").length;
    return { total, inService, maint, retired };
  }, [rows]);

  const pending = upsertAsset.isPending || updateStatus.isPending || retireAsset.isPending;

  return (
    <AppLayout>
      <PageShell
        title="Asset registry"
        subtitle="Register fixed assets, track location and assignment, and move items through IN_SERVICE, MAINTENANCE, and RETIRED — asset-registry-service via BFF."
        icon={<Package className="h-6 w-6" />}
      >
        <SupplyPlaneContextBar />
        <div className="mb-4">
          <Link href="/inventory" className="text-sm font-medium text-muted-foreground hover:text-foreground">
            ← Inventory dashboard
          </Link>
        </div>

        {!facilityRef ? (
          <div className="mb-4 rounded-lg border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">
            Select an active facility to list and register assets against the governed registry.
          </div>
        ) : null}

        {assetsQ.isError ? (
          <div className="mb-4 flex items-center gap-2 rounded-lg border border-danger/28 bg-danger-soft px-4 py-3 text-sm text-danger">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            Asset registry could not be loaded.
          </div>
        ) : null}

        <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
          {[
            { label: "Total assets", value: summary.total },
            { label: "In service", value: summary.inService },
            { label: "Maintenance", value: summary.maint },
            { label: "Retired", value: summary.retired },
          ].map(({ label, value }) => (
            <div key={label} className="rounded-xl border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</p>
              <p className="mt-2 text-2xl font-bold text-foreground">
                {assetsQ.isLoading ? "…" : value}
              </p>
            </div>
          ))}
        </div>

        <section className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,360px)_1fr]">
          <form onSubmit={register} className="h-fit rounded-2xl border border-border bg-card p-5 shadow-sm">
            <div className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Register asset</div>
            <p className="mt-2 text-sm text-muted-foreground">
              Persists to asset-registry via <code className="text-xs">/internal/v1/asset-registry/assets</code>.
            </p>
            <div className="mt-4 space-y-3">
              <label className="block text-sm">
                <span className="mb-1 block font-medium text-foreground">Asset tag / serial</span>
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
            {formError ? <p className="mt-3 text-sm text-danger">{formError}</p> : null}
            <button
              type="submit"
              disabled={pending || !facilityRef}
              className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
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

            {assetsQ.isLoading ? (
              <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" />
                Loading assets…
              </div>
            ) : filtered.length === 0 ? (
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
                    {filtered.map((a) => {
                      const id = String(a.assetId ?? a.id ?? "");
                      const status = normalizeStatus(a.status);
                      return (
                        <tr key={id} className="border-t border-border">
                          <td className="px-3 py-2 font-mono text-xs text-foreground">{String(a.serialNo ?? "—")}</td>
                          <td className="px-3 py-2 font-medium text-foreground">{assetDisplayName(a)}</td>
                          <td className="px-3 py-2 text-muted-foreground">{String(a.type ?? "—")}</td>
                          <td className="px-3 py-2 text-muted-foreground">{assetLocation(a)}</td>
                          <td className="px-3 py-2">
                            <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_STYLES[status]}`}>{status}</span>
                          </td>
                          <td className="px-3 py-2 text-muted-foreground">{assetAssigned(a)}</td>
                          <td className="px-3 py-2 text-right">
                            <div className="inline-flex flex-wrap justify-end gap-1">
                              <button
                                type="button"
                                disabled={pending || status === "IN_SERVICE"}
                                onClick={() => void setStatus(id, "IN_SERVICE")}
                                className="rounded border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-background disabled:opacity-40"
                              >
                                In service
                              </button>
                              <button
                                type="button"
                                disabled={pending || status === "MAINTENANCE"}
                                onClick={() => void setStatus(id, "MAINTENANCE")}
                                className="inline-flex items-center gap-1 rounded border border-warning/35 bg-warning-soft px-2 py-1 text-xs font-medium text-warning-foreground hover:bg-amber-100 disabled:opacity-40"
                              >
                                <Wrench className="h-3 w-3" />
                                Maintain
                              </button>
                              <button
                                type="button"
                                disabled={pending || status === "RETIRED"}
                                onClick={() => void setStatus(id, "RETIRED")}
                                className="rounded border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-neutral-100 disabled:opacity-40"
                              >
                                Retire
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
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
