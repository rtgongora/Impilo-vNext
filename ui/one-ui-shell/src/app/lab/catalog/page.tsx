"use client";

/**
 * Test Catalog — absorbs oros-web sidecar
 * Available lab tests and capabilities for this facility.
 * Route: /lab/catalog | Zone: lab | Guard: shift
 */

import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { BookOpen, Search, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useLabCatalog } from "@/hooks/queries/useLabCatalog";

const ORDER_TYPES = [
  { value: "LAB", label: "Laboratory" },
  { value: "IMAGING", label: "Imaging" },
  { value: "PHARMACY", label: "Pharmacy" },
  { value: "BLOOD", label: "Blood" },
] as const;

function matchesSearch(
  item: { code?: string; displayName?: string; category?: string },
  query: string,
): boolean {
  if (!query.trim()) return true;
  const needle = query.trim().toLowerCase();
  const haystack = [item.code, item.displayName, item.category]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  return haystack.includes(needle);
}

export default function LabCatalogPage() {
  const searchParams = useSearchParams();
  const initialOrderType = searchParams.get("order_type") ?? "LAB";
  const [search, setSearch] = useState("");
  const [orderType, setOrderType] = useState(initialOrderType);

  const catalogQ = useLabCatalog({ orderType, size: 100 });

  const summary = catalogQ.data?.data?.summary;
  const categories = useMemo(() => {
    const byCategory = summary?.by_category ?? {};
    return Object.entries(byCategory).map(([label, count]) => ({
      label,
      count,
      description: `${count} orderable item${count === 1 ? "" : "s"} in this category`,
    }));
  }, [summary?.by_category]);

  const filteredItems = useMemo(() => {
    const items = catalogQ.data?.data?.items ?? [];
    return items.filter((item) => matchesSearch(item, search));
  }, [catalogQ.data?.data?.items, search]);

  return (
    <AppLayout>
      <PageShell
        title="Test Catalog"
        subtitle="Available laboratory tests and facility capabilities"
        icon={<BookOpen className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <FeatureMaturityBadge
            status="live"
            detail="OROS catalog via /internal/v1/lab-catalog"
          />

          <div className="flex flex-wrap gap-2">
            {ORDER_TYPES.map(({ value, label }) => (
              <button
                key={value}
                type="button"
                onClick={() => setOrderType(value)}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  orderType === value
                    ? "bg-violet-600 text-white"
                    : "border border-gray-300 text-gray-700 hover:bg-gray-50"
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="relative w-full md:w-96">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search tests by name or code..."
              className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-violet-500 focus:outline-none focus:ring-1 focus:ring-violet-500"
            />
          </div>

          {catalogQ.isLoading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-gray-500">
              <Loader2 className="h-5 w-5 animate-spin" />
              Loading catalog…
            </div>
          ) : catalogQ.isError ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-800">
              Unable to load the catalog. Confirm OROS is running and the BFF proxy is configured.
            </div>
          ) : categories.length === 0 && filteredItems.length === 0 ? (
            <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
              <BookOpen className="mx-auto h-12 w-12 text-gray-400" />
              <h3 className="mt-4 text-sm font-semibold text-gray-900">No catalog items</h3>
              <p className="mt-2 text-sm text-gray-600">
                No orderable items are configured for {orderType} orders yet.
              </p>
            </div>
          ) : (
            <>
              {categories.length > 0 ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {categories.map(({ label, description, count }) => (
                    <div
                      key={label}
                      className="rounded-lg border border-gray-200 bg-white p-5 hover:border-violet-400 hover:shadow-sm transition-all"
                    >
                      <div className="flex items-center justify-between mb-1">
                        <h3 className="font-semibold text-gray-900">{label}</h3>
                        <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">
                          {count} tests
                        </span>
                      </div>
                      <p className="text-sm text-gray-600">{description}</p>
                    </div>
                  ))}
                </div>
              ) : null}

              {filteredItems.length > 0 ? (
                <div className="rounded-lg border border-gray-200 bg-white overflow-hidden">
                  <div className="border-b border-gray-200 px-4 py-3 grid grid-cols-4 gap-3 text-xs font-semibold text-gray-500 uppercase">
                    <span>Code</span>
                    <span>Display name</span>
                    <span>Category</span>
                    <span>Status</span>
                  </div>
                  <ul className="divide-y divide-gray-100">
                    {filteredItems.map((item) => (
                      <li
                        key={String(item.catalogId ?? item.code)}
                        className="px-4 py-3 grid grid-cols-4 gap-3 text-sm"
                      >
                        <span className="font-medium text-gray-900">{item.code}</span>
                        <span className="text-gray-700">{item.displayName ?? "—"}</span>
                        <span className="text-gray-600">{item.category ?? "—"}</span>
                        <span className="text-gray-600">
                          {item.active === false ? "Inactive" : "Active"}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
