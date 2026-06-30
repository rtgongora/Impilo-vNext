"use client";

import Link from "next/link";
import { ArrowRight, Search, Heart, ClipboardList, Store, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { useStoreHome, type MsikaListing } from "@/hooks/queries/useMsikaStore";

const RISK_BADGE: Record<string, string> = {
  UNRESTRICTED: "bg-emerald-50 text-emerald-700 border-emerald-200",
  LOW_RISK: "bg-sky-50 text-sky-700 border-sky-200",
  MODERATE_RISK: "bg-amber-50 text-amber-700 border-amber-200",
  HIGH_RISK: "bg-orange-50 text-orange-700 border-orange-200",
  REGULATED: "bg-red-50 text-red-700 border-red-200",
};

function ListingCard({ listing }: { listing: MsikaListing }) {
  const badge = RISK_BADGE[listing.riskClassification] ?? "bg-muted text-muted-foreground border-border";
  return (
    <Link
      href={`/marketplace/store/listing/${listing.listingId}`}
      className="block rounded-2xl border border-border bg-card p-4 shadow-sm transition hover:border-primary"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="font-semibold text-foreground">{listing.title}</div>
        <span className={`shrink-0 rounded-full border px-2 py-0.5 text-[10px] font-medium ${badge}`}>
          {listing.riskClassification.replace("_", " ")}
        </span>
      </div>
      {listing.summary ? <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{listing.summary}</p> : null}
      <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground">
        <span>{listing.priceDisplay ?? "Price via Costa"}</span>
        <span className="inline-flex items-center gap-1 text-primary">
          View <ArrowRight className="h-3 w-3" />
        </span>
      </div>
    </Link>
  );
}

export default function MarketplaceStorePage() {
  const home = useStoreHome();
  const featured = home.data?.featured ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Marketplace Store"
        subtitle="Discover and request approved health products and services from verified sellers. Billing stays with Costa, payments with MusheX, and clinical items route through your care team."
        serviceSlug="msika"
      >
        <div className="space-y-6">
          <NompiloContextualGuidance routePath="/marketplace/store" />

          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Link href="/marketplace/store/search" className="rounded-2xl border border-border bg-card p-4 shadow-sm hover:border-primary">
              <Search className="h-5 w-5 text-primary" />
              <div className="mt-2 font-semibold text-foreground">Search & browse</div>
              <p className="text-sm text-muted-foreground">Find products and services near you.</p>
            </Link>
            <Link href="/marketplace/store/activity" className="rounded-2xl border border-border bg-card p-4 shadow-sm hover:border-primary">
              <ClipboardList className="h-5 w-5 text-primary" />
              <div className="mt-2 font-semibold text-foreground">My activity</div>
              <p className="text-sm text-muted-foreground">Track requests, bookings and orders.</p>
            </Link>
            <Link href="/marketplace/seller" className="rounded-2xl border border-border bg-card p-4 shadow-sm hover:border-primary">
              <Store className="h-5 w-5 text-primary" />
              <div className="mt-2 font-semibold text-foreground">Seller Centre</div>
              <p className="text-sm text-muted-foreground">List and manage what you offer.</p>
            </Link>
            <div className="rounded-2xl border border-border bg-card p-4 shadow-sm">
              <ShieldCheck className="h-5 w-5 text-emerald-600" />
              <div className="mt-2 font-semibold text-foreground">Trusted & governed</div>
              <p className="text-sm text-muted-foreground">Sellers are verified; regulated items are gated for safety.</p>
            </div>
          </section>

          <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-foreground">Featured & recently published</h2>
              <Link href="/marketplace/store/search" className="text-sm text-primary hover:underline">
                See all
              </Link>
            </div>
            {home.isLoading ? (
              <p className="mt-4 text-sm text-muted-foreground">Loading listings…</p>
            ) : home.isError ? (
              <p className="mt-4 text-sm text-red-600">Could not load listings right now.</p>
            ) : featured.length === 0 ? (
              <p className="mt-4 text-sm text-muted-foreground">
                No published listings yet. Sellers can publish via the Seller Centre.
              </p>
            ) : (
              <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                {featured.map((l) => (
                  <ListingCard key={l.listingId} listing={l} />
                ))}
              </div>
            )}
          </section>

          <p className="flex items-center gap-2 rounded-lg border border-border bg-background px-4 py-2 text-xs text-muted-foreground">
            <Heart className="h-3 w-3" /> Listings come from msika-service via the Experience BFF. Sections that fail
            upstream are shown as unavailable — never as an empty success.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
