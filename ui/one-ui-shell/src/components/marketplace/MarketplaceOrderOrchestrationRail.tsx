"use client";

import Link from "next/link";
import { Loader2, PackageCheck, ShoppingBag } from "lucide-react";
import { useMarketplaceOrders } from "@/hooks/queries/useMarketplace";
import { useFacilityStore } from "@/hooks/useFacilityStore";

export function MarketplaceOrderOrchestrationRail() {
  const facility = useFacilityStore((s) => s.facility);
  const ordersQ = useMarketplaceOrders(facility?.id ?? "");
  const orders = ordersQ.data ?? [];
  const openOrders = orders.filter((o) => o.status !== "DELIVERED" && o.status !== "CANCELLED").length;

  return (
    <section
      className="mb-6 rounded-2xl border border-violet-200 bg-violet-50/80 p-4"
      data-testid="marketplace-order-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-violet-900">
            <ShoppingBag className="h-4 w-4" />
            Marketplace order orchestration
          </p>
          <p className="mt-1 text-xs text-violet-800" data-testid="marketplace-order-kpi-strip">
            {!facility
              ? "Select a facility to scope marketplace orders"
              : ordersQ.isLoading
                ? "Loading tenant-scoped orders from MSIKA Flow…"
                : `${orders.length} order(s) · ${openOrders} open for ${facility.name}`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <Link
            href="/marketplace/orders"
            className="inline-flex items-center gap-1 rounded-lg border border-violet-200 bg-white px-2.5 py-1.5 font-medium text-violet-900 hover:border-violet-300"
          >
            <PackageCheck className="h-3.5 w-3.5" />
            Order history
          </Link>
          <Link
            href="/marketplace/catalog"
            className="rounded-lg border border-violet-200 bg-white px-2.5 py-1.5 font-medium text-violet-900 hover:border-violet-300"
          >
            Browse catalog
          </Link>
        </div>
      </div>
      {ordersQ.isLoading && facility && (
        <div className="mt-2 flex items-center gap-2 text-xs text-violet-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Probing GET /internal/v1/marketplace/orders…
        </div>
      )}
    </section>
  );
}
