"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Loader2, Plus, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { SupplyPlaneContextBar } from "@/components/experience/SupplyPlaneContextBar";
import { useCreateInventoryItem, useInventoryItems } from "@/hooks/queries/useInventory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-primary-hover",
  LOW_STOCK: "bg-amber-100 text-warning-foreground",
  INACTIVE: "bg-neutral-100 text-foreground",
};

export default function InventoryItemsPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id ?? "";
  const itemsQuery = useInventoryItems(facilityId);
  const createItem = useCreateInventoryItem();

  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");

  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [unit, setUnit] = useState("EA");
  const [reorder, setReorder] = useState("10");
  const [onHand, setOnHand] = useState("0");

  const categories = useMemo(() => {
    const set = new Set<string>();
    for (const it of itemsQuery.data ?? []) {
      if (it.category) set.add(it.category);
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [itemsQuery.data]);

  const filtered = useMemo(() => {
    const list = itemsQuery.data ?? [];
    const q = search.trim().toLowerCase();
    const cat = categoryFilter.trim().toLowerCase();
    return list.filter((it) => {
      if (cat && it.category.toLowerCase() !== cat) return false;
      if (!q) return true;
      return (
        it.productCode.toLowerCase().includes(q) ||
        it.productName.toLowerCase().includes(q) ||
        it.category.toLowerCase().includes(q)
      );
    });
  }, [itemsQuery.data, search, categoryFilter]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!facility) return;
    await createItem.mutateAsync({
      facilityId: facility.id,
      productCode: code.trim(),
      productName: name.trim(),
      category: category.trim() || "Uncategorized",
      unit: unit.trim() || "EA",
      reorderLevel: Number(reorder) || 0,
      quantityOnHand: Number(onHand) || 0,
    });
    setCode("");
    setName("");
    setCategory("");
    setUnit("EA");
    setReorder("10");
    setOnHand("0");
  }

  return (
    <AppLayout>
      <PageShell
        title="Item catalog"
        subtitle="Search and maintain inventory items — codes, categories, units, on-hand, reorder levels, and status."
      >
        {!facility ? (
          <div className="rounded-xl border border-warning/35 bg-warning-soft p-5 text-sm text-warning-foreground">
            Select a facility to load and create catalog items.
            <div className="mt-3">
              <Link href="/workspace" className="font-medium underline">
                Choose facility work
              </Link>
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            <SupplyPlaneContextBar />
            <div className="mb-2">
              <Link href="/inventory" className="text-sm font-medium text-muted-foreground hover:text-foreground">
                ← Inventory dashboard
              </Link>
            </div>

            <section className="grid gap-6 xl:grid-cols-[minmax(0,340px)_1fr]">
              <form onSubmit={handleCreate} className="h-fit rounded-2xl border border-border bg-card p-5 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Add item</div>
                <h2 className="mt-1 text-lg font-semibold text-foreground">{facility.name}</h2>
                <p className="mt-2 text-sm text-muted-foreground">Creates a catalog line via the inventory BFF (POST /internal/v1/inventory/items).</p>
                <div className="mt-4 space-y-3">
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Item code</span>
                    <input
                      value={code}
                      onChange={(ev) => setCode(ev.target.value)}
                      required
                      className="w-full rounded-lg border border-border px-3 py-2"
                      placeholder="e.g. GLV-100"
                    />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Name</span>
                    <input
                      value={name}
                      onChange={(ev) => setName(ev.target.value)}
                      required
                      className="w-full rounded-lg border border-border px-3 py-2"
                      placeholder="Description"
                    />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Category</span>
                    <input
                      value={category}
                      onChange={(ev) => setCategory(ev.target.value)}
                      className="w-full rounded-lg border border-border px-3 py-2"
                      placeholder="Consumables, Pharmacy, …"
                    />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Unit</span>
                    <input
                      value={unit}
                      onChange={(ev) => setUnit(ev.target.value)}
                      className="w-full rounded-lg border border-border px-3 py-2"
                    />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Reorder level</span>
                    <input
                      value={reorder}
                      onChange={(ev) => setReorder(ev.target.value)}
                      type="number"
                      min={0}
                      className="w-full rounded-lg border border-border px-3 py-2"
                    />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Starting on-hand (optional)</span>
                    <input
                      value={onHand}
                      onChange={(ev) => setOnHand(ev.target.value)}
                      type="number"
                      min={0}
                      className="w-full rounded-lg border border-border px-3 py-2"
                    />
                  </label>
                </div>
                <button
                  type="submit"
                  disabled={createItem.isPending}
                  className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                >
                  {createItem.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                  Save item
                </button>
                {createItem.isError ? (
                  <p className="mt-2 text-sm text-danger">Unable to create item — the API may not support POST yet.</p>
                ) : null}
              </form>

              <div className="rounded-2xl border border-border bg-card shadow-sm">
                <div className="border-b border-border p-4 sm:flex sm:items-end sm:justify-between sm:gap-4">
                  <div>
                    <h3 className="text-lg font-semibold text-foreground">Catalog</h3>
                    <p className="text-sm text-muted-foreground">{filtered.length} row(s) after search / filter.</p>
                  </div>
                  <div className="mt-3 flex flex-col gap-3 sm:mt-0 sm:flex-row">
                    <div className="relative min-w-[200px] flex-1">
                      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                      <input
                        value={search}
                        onChange={(ev) => setSearch(ev.target.value)}
                        placeholder="Search code, name, category…"
                        className="w-full rounded-lg border border-border py-2 pl-9 pr-3 text-sm"
                      />
                    </div>
                    <select
                      value={categoryFilter}
                      onChange={(ev) => setCategoryFilter(ev.target.value)}
                      className="rounded-lg border border-border px-3 py-2 text-sm"
                    >
                      <option value="">All categories</option>
                      {categories.map((c) => (
                        <option key={c} value={c}>
                          {c}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {itemsQuery.isLoading ? (
                  <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
                    <Loader2 className="h-5 w-5 animate-spin" />
                    Loading catalog…
                  </div>
                ) : itemsQuery.isError ? (
                  <div className="flex items-start gap-2 p-6 text-sm text-rose-800">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                    Failed to load items.
                  </div>
                ) : filtered.length === 0 ? (
                  <div className="p-12 text-center text-sm text-muted-foreground">No items match the current filters.</div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[720px] text-sm">
                      <thead className="bg-background text-left text-muted-foreground">
                        <tr>
                          <th className="px-3 py-2 font-medium">Code</th>
                          <th className="px-3 py-2 font-medium">Name</th>
                          <th className="px-3 py-2 font-medium">Category</th>
                          <th className="px-3 py-2 font-medium">Unit</th>
                          <th className="px-3 py-2 font-medium">On hand</th>
                          <th className="px-3 py-2 font-medium">Reorder</th>
                          <th className="px-3 py-2 font-medium">Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filtered.map((it) => (
                          <tr key={it.id} className="border-t border-border">
                            <td className="px-3 py-2 font-mono text-xs text-foreground">{it.productCode}</td>
                            <td className="px-3 py-2 font-medium text-foreground">{it.productName}</td>
                            <td className="px-3 py-2 text-muted-foreground">{it.category}</td>
                            <td className="px-3 py-2 text-muted-foreground">{it.unit}</td>
                            <td className="px-3 py-2 text-foreground">{it.quantityOnHand}</td>
                            <td className="px-3 py-2 text-muted-foreground">{it.reorderLevel}</td>
                            <td className="px-3 py-2">
                              <span
                                className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_STYLES[it.status] ?? "bg-neutral-100 text-foreground"}`}
                              >
                                {it.status}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
