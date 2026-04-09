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

  return (
    <AppLayout>
      <PageShell title="Marketplace" subtitle="Procure services, track orders, and keep facility supply decisions inside the same operational experience layer.">
        <div className="space-y-6">
          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Operational continuity</div>
                <h2 className="mt-1 text-xl font-semibold text-slate-900">{facility ? `${facility.name} marketplace workflow` : "Marketplace across the same experience layer"}</h2>
                <p className="mt-2 max-w-3xl text-sm text-slate-600">Marketplace now stays grounded in real service catalog, booking, partner, and order data. When a facility is in scope, ordering remains tied to that facility instead of becoming a detached admin task.</p>
              </div>
              <div className="grid gap-2 text-sm text-slate-600 sm:grid-cols-2">
                <Link href="/marketplace/orders" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Open orders</Link>
                <Link href="/marketplace/catalog" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Browse catalog</Link>
                <Link href="/marketplace/bookings" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Review bookings</Link>
                <Link href="/inventory?source=marketplace" className="rounded-lg border border-slate-200 px-3 py-2 font-medium hover:bg-slate-50">Return to inventory</Link>
              </div>
            </div>
          </section>

          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Open orders</div>
              <div className="mt-3 text-3xl font-semibold text-slate-900">{orders.filter((order) => order.status !== "DELIVERED").length}</div>
              <p className="mt-1 text-sm text-slate-600">{facility ? "For the facility currently in scope." : "Select a facility to place new orders."}</p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Catalog items</div>
              <div className="mt-3 text-3xl font-semibold text-slate-900">{catalog.length}</div>
              <p className="mt-1 text-sm text-slate-600">Live services and supply-adjacent offerings from marketplace partners.</p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Marketplace partners</div>
              <div className="mt-3 text-3xl font-semibold text-slate-900">{partners.length}</div>
              <p className="mt-1 text-sm text-slate-600">Facilities and providers currently exposing active listings.</p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Service bookings</div>
              <div className="mt-3 text-3xl font-semibold text-slate-900">{bookings.length}</div>
              <p className="mt-1 text-sm text-slate-600">Delivery and servicing commitments already moving through the marketplace.</p>
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
                  <p className="mt-1 text-sm text-slate-600">{orders.length > 0 ? `Latest order ${orders[0].orderNumber} is ${orders[0].status.toLowerCase()}.` : "No facility-scoped orders yet. Select a facility and raise the first order from the orders workspace."}</p>
                </div>
                <div className="rounded-xl border border-slate-200 px-4 py-3">
                  <div className="flex items-center gap-2 text-sm font-medium text-slate-900"><CalendarDays className="h-4 w-4 text-emerald-600" /> Booking continuity</div>
                  <p className="mt-1 text-sm text-slate-600">{bookings[0] ? `${bookings[0].serviceName} is ${bookings[0].status.toLowerCase()} with ${bookings[0].providerName}.` : "Service bookings will appear here once marketplace requests are scheduled."}</p>
                </div>
                <div className="rounded-xl border border-slate-200 px-4 py-3">
                  <div className="flex items-center gap-2 text-sm font-medium text-slate-900"><Store className="h-4 w-4 text-violet-600" /> Partner availability</div>
                  <p className="mt-1 text-sm text-slate-600">{partners[0] ? `${partners[0].name} currently has ${partners[0].activeListings} active listings.` : "Partner availability updates as marketplace services are published."}</p>
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
