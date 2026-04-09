"use client";

import { useState } from "react";
import { Loader2, Package, Search, ShoppingBag } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMarketplaceCatalog } from "@/hooks/queries/useMarketplace";

const CATEGORIES = ["All", "Logistics", "Biomedical", "Laboratory", "Engineering", "GENERAL"];

export default function CatalogPage() {
  const [searchTerm, setSearchTerm] = useState("");
  const [category, setCategory] = useState("All");
  const catalogQuery = useMarketplaceCatalog(searchTerm, category);
  const items = catalogQuery.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Marketplace Catalog" subtitle="Real marketplace service and supply listings available across the operating network.">
        <div className="mb-6 flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              placeholder="Search services or supplies..."
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <select
            value={category}
            onChange={(event) => setCategory(event.target.value)}
            className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {CATEGORIES.map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </div>

        {catalogQuery.isLoading ? (
          <div className="flex items-center justify-center py-16 text-sm text-gray-500">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading catalog...
          </div>
        ) : items.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
            <ShoppingBag className="mx-auto mb-3 h-10 w-10 text-gray-300" />
            <p className="text-sm text-gray-500">No marketplace listings matched the current search.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {items.map((item) => (
              <div key={item.id} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="mb-3 flex h-32 items-center justify-center rounded-xl bg-slate-100">
                  <Package className="h-10 w-10 text-slate-300" />
                </div>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="text-sm font-semibold text-slate-900">{item.name}</h3>
                    <p className="mt-1 text-xs text-slate-500">{item.category} • {item.facilityName}</p>
                  </div>
                  <span className={`rounded-full px-2 py-1 text-xs font-semibold ${item.availability === "AVAILABLE" ? "bg-emerald-100 text-emerald-800" : "bg-slate-100 text-slate-700"}`}>
                    {item.availability}
                  </span>
                </div>
                <p className="mt-3 text-sm text-slate-600">{item.description}</p>
                <div className="mt-4 flex items-center justify-between text-sm">
                  <span className="font-semibold text-slate-900">{item.currency} {item.price.toFixed(2)}</span>
                  <span className="text-slate-500">{item.rating ? `${item.rating.toFixed(1)} rating` : "No rating yet"}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
