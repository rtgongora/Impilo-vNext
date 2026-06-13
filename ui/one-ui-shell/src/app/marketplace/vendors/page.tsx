"use client";

import { Loader2, Star, Store } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMarketplacePartners } from "@/hooks/queries/useMarketplace";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-primary-hover",
  INACTIVE: "bg-neutral-100 text-foreground",
};

export default function VendorsPage() {
  const partnersQuery = useMarketplacePartners();
  const partners = partnersQuery.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Marketplace Partners" subtitle="Facilities and providers currently exposing real marketplace listings.">
        {partnersQuery.isLoading ? (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading partners...
          </div>
        ) : partners.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-12 text-center">
            <Store className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No marketplace partners are registered yet.</p>
          </div>
        ) : (
          <div className="rounded-2xl border border-border bg-card shadow-sm">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background text-left text-muted-foreground">
                  <th className="px-4 py-3 font-medium">Partner</th>
                  <th className="px-4 py-3 font-medium">Facility</th>
                  <th className="px-4 py-3 font-medium">Listings</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Rating</th>
                </tr>
              </thead>
              <tbody>
                {partners.map((partner) => (
                  <tr key={partner.id} className="border-b border-border last:border-b-0 hover:bg-background">
                    <td className="px-4 py-3 font-medium text-foreground">{partner.name}</td>
                    <td className="px-4 py-3 text-muted-foreground">{partner.facilityId ?? "Shared partner record"}</td>
                    <td className="px-4 py-3 text-muted-foreground">{partner.activeListings} active / {partner.serviceCount} total</td>
                    <td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${STATUS_STYLES[partner.status] ?? "bg-neutral-100 text-foreground"}`}>{partner.status}</span></td>
                    <td className="px-4 py-3 text-muted-foreground">{partner.rating != null ? <span className="inline-flex items-center gap-1"><Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" /> {partner.rating.toFixed(1)}</span> : "-"}</td>
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
