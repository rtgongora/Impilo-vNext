"use client";

import { Loader2, Star, Store } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMarketplacePartners } from "@/hooks/queries/useMarketplace";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-800",
  INACTIVE: "bg-slate-100 text-slate-700",
};

export default function VendorsPage() {
  const partnersQuery = useMarketplacePartners();
  const partners = partnersQuery.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Marketplace Partners" subtitle="Facilities and providers currently exposing real marketplace listings.">
        {partnersQuery.isLoading ? (
          <div className="flex items-center justify-center py-16 text-sm text-gray-500">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading partners...
          </div>
        ) : partners.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
            <Store className="mx-auto mb-3 h-10 w-10 text-gray-300" />
            <p className="text-sm text-gray-500">No marketplace partners are registered yet.</p>
          </div>
        ) : (
          <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-slate-50 text-left text-slate-600">
                  <th className="px-4 py-3 font-medium">Partner</th>
                  <th className="px-4 py-3 font-medium">Facility</th>
                  <th className="px-4 py-3 font-medium">Listings</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Rating</th>
                </tr>
              </thead>
              <tbody>
                {partners.map((partner) => (
                  <tr key={partner.id} className="border-b border-slate-100 last:border-b-0 hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-900">{partner.name}</td>
                    <td className="px-4 py-3 text-slate-600">{partner.facilityId ?? "Shared partner record"}</td>
                    <td className="px-4 py-3 text-slate-600">{partner.activeListings} active / {partner.serviceCount} total</td>
                    <td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${STATUS_STYLES[partner.status] ?? "bg-slate-100 text-slate-700"}`}>{partner.status}</span></td>
                    <td className="px-4 py-3 text-slate-600">{partner.rating != null ? <span className="inline-flex items-center gap-1"><Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" /> {partner.rating.toFixed(1)}</span> : "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
