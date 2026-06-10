"use client";

import Link from "next/link";
import { ArrowRight, CalendarDays, PackageCheck, ShoppingBag, Store } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useMarketplaceBookings,
  useMarketplaceCatalog,
  useMarketplaceOrders,
  useMarketplacePartners,
} from "@/hooks/queries/useMarketplace";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { MarketplaceOrderOrchestrationRail } from "@/components/marketplace/MarketplaceOrderOrchestrationRail";

export default function MarketplacePage() {
  const facility = useFacilityStore((state) => state.facility);
  const ordersQuery = useMarketplaceOrders(facility?.id ?? "");
  const catalogQuery = useMarketplaceCatalog("", "All");
  const partnersQuery = useMarketplacePartners();
  const bookingsQuery = useMarketplaceBookings();

  const orders = ordersQuery.data ?? [];
  const catalog = catalogQuery.data ?? [];
  const partners = partnersQuery.data ?? [];
  const bookings = bookingsQuery.data ?? [];

  const facilityId = facility?.id;
  const openOrderCount = orders.filter((order) => order.status !== "DELIVERED").length;

  return (
    <AppLayout>
      <PageShell title="Marketplace" subtitle="Procure services, track orders, and keep facility supply decisions inside the same operational experience layer." serviceSlug="msika">
        <div className="space-y-6">
          <MarketplaceOrderOrchestrationRail />
          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Operational continuity</div>
                <h2 className="mt-1 text-xl font-semibold text-slate-900">{facility ? `${facility.name} marketplace workflow` : "Marketplace across the same experience layer"}</h2>
                <p className="mt-2 max-w-3xl text-sm text-slate-600">Marketplace now stays grounded in real service catalog, booking, partner, and order data. When a facility is in scope, ordering remains tied to that facility instead of becoming a detached admin task.</p>
                <p className="mt-2 text-xs text-slate-500">
                  <Link href="/finance/commerce-integrations" className="font-medium text-indigo-700 hover:underline">
                    Commerce & payer integration map
                  </Link>{" "}
                  — MSIKA registry + commerce rails are now available via{" "}
                  <code className="text-[10px]">/internal/v1/product-registry/*</code> and{" "}
                  <code className="text-[10px]">/internal/v1/commerce/*</code>. MusheX operator surfaces remain blocked until
                  canonical <code className="text-[10px]">/internal/v1/mushex/*</code> proxies land.
                </p>
              </div>
              <div className="grid gap-2 text-sm text-slate-600 sm:grid-cols-2">
                <Link href="/marketplace/orders" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Open orders</Link>
                <Link href="/marketplace/catalog" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Browse catalog</Link>
                <Link href="/marketplace/bookings" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Review bookings</Link>
                <Link href="/inventory?source=marketplace" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Return to inventory</Link>
              </div>
            </div>
          </section>

          <p className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-2 text-xs text-slate-600">
            Tiles below count only rows returned successfully from Experience BFF{" "}
            <code className="text-[10px]">/internal/v1/marketplace/*</code>. Failed requests show “—”, not zero — zeros are
            never used to imply a successful empty snapshot.
          </p>

          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Open orders</div>
              <div className="mt-3 text-3xl font-semibold text-slate-900">
                {!facilityId ? "—" : ordersQuery.isLoading ? "…" : ordersQuery.isError ? "—" : openOrderCount}
              </div>
              <p className="mt-1 text-sm text-slate-600">
                {!facilityId
                  ? "Select a facility — orders are not requested without facility_id."
                  : ordersQuery.isError
                    ? "Could not load orders from Experience BFF."
                    : ordersQuery.isLoading
                      ? "Loading…"
                      : "For the facility currently in scope."}
              </p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Catalog items</div>
              <div className="mt-3 text-3xl font-semibold text-slate-900">
                {catalogQuery.isLoading ? "…" : catalogQuery.isError ? "—" : catalog.length}
              </div>
              <p className="mt-1 text-sm text-slate-600">
                {catalogQuery.isError ? "Could not load catalog from Experience BFF." : "Rows from GET /internal/v1/marketplace/catalog."}
              </p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Marketplace partners</div>
              <div className="mt-3 text-3xl font-semibold text-slate-900">
                {partnersQuery.isLoading ? "…" : partnersQuery.isError ? "—" : partners.length}
              </div>
              <p className="mt-1 text-sm text-slate-600">
                {partnersQuery.isError ? "Could not load vendors from Experience BFF." : "Rows from GET /internal/v1/marketplace/vendors."}
              </p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Service bookings</div>
              <div className="mt-3 text-3xl font-semibold text-slate-900">
                {bookingsQuery.isLoading ? "…" : bookingsQuery.isError ? "—" : bookings.length}
              </div>
              <p className="mt-1 text-sm text-slate-600">
                {bookingsQuery.isError ? "Could not load bookings from Experience BFF." : "Rows from GET /internal/v1/marketplace/bookings."}
              </p>
            </div>
          </section>

          <section className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-semibold text-slate-900">Next action queue</h3>
                  <p className="text-sm text-slate-600">Keep procurement decisions tied to active facility demand and existing bookings.</p>
                </div>
                <Link href="/marketplace/orders" className="inline-flex items-center gap-1 text-sm font-medium text-slate-700 hover:text-slate-900">
                  Open orders
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </div>
              <div className="mt-4 space-y-3">
                <div className="rounded-xl border border-slate-200 px-4 py-3">
                  <div className="flex items-center gap-2 text-sm font-medium text-slate-900"><ShoppingBag className="h-4 w-4 text-sky-600" /> Order follow-through</div>
                  <p className="mt-1 text-sm text-slate-600">
                    {!facilityId
                      ? "Select a facility to load orders from the BFF."
                      : ordersQuery.isError
                        ? "Orders request failed — use the orders workspace or retry after the BFF is healthy."
                        : orders.length > 0
                          ? `Latest order ${orders[0].orderNumber} is ${orders[0].status.toLowerCase()}.`
                          : "No facility-scoped orders in this response. Raise the first order from the orders workspace."}
                  </p>
                </div>
                <div className="rounded-xl border border-slate-200 px-4 py-3">
                  <div className="flex items-center gap-2 text-sm font-medium text-slate-900"><CalendarDays className="h-4 w-4 text-emerald-600" /> Booking continuity</div>
                  <p className="mt-1 text-sm text-slate-600">
                    {bookingsQuery.isError
                      ? "Bookings request failed — check BFF connectivity."
                      : bookings[0]
                        ? `${bookings[0].serviceName} is ${bookings[0].status.toLowerCase()} with ${bookings[0].providerName}.`
                        : "No bookings in this API response yet."}
                  </p>
                </div>
                <div className="rounded-xl border border-slate-200 px-4 py-3">
                  <div className="flex items-center gap-2 text-sm font-medium text-slate-900"><Store className="h-4 w-4 text-violet-600" /> Partner availability</div>
                  <p className="mt-1 text-sm text-slate-600">
                    {partnersQuery.isError
                      ? "Partners request failed — check BFF connectivity."
                      : partners[0]
                        ? `${partners[0].name} currently has ${partners[0].activeListings} active listings.`
                        : "No partners in this API response yet."}
                  </p>
                </div>
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h3 className="text-lg font-semibold text-slate-900">Cross-surface handoff</h3>
              <div className="mt-4 space-y-4 text-sm text-slate-600">
                <div className="rounded-xl border border-slate-200 p-4">
                  <div className="flex items-center gap-2 font-medium text-slate-900"><PackageCheck className="h-4 w-4 text-amber-600" /> From inventory pressure</div>
                  <p className="mt-1">Low stock or an approved requisition can hand off into marketplace orders without losing facility context.</p>
                </div>
                <div className="rounded-xl border border-slate-200 p-4">
                  <div className="font-medium text-slate-900">From org-admin oversight</div>
                  <p className="mt-1">Organization administration can review marketplace and booking throughput without forcing a clinical shift workflow.</p>
                </div>
                <div className="rounded-xl border border-slate-200 p-4">
                  <div className="font-medium text-slate-900">What remains local</div>
                  <p className="mt-1">Order creation still requires a facility in scope, because procurement actions must resolve to a real operating site.</p>
                </div>
              </div>
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
