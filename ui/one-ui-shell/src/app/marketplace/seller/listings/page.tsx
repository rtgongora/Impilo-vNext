"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { PlusCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { useSellerListings } from "@/hooks/queries/useMsikaStore";
import { useLinkedIds } from "@/hooks/queries/useLinkedIds";
import { useAuthStore } from "@/hooks/useAuthStore";
import { matchesRequiredRole } from "@/lib/auth/role-groups";

const STATUS_BADGE: Record<string, string> = {
  DRAFT: "bg-muted text-muted-foreground",
  AWAITING_APPROVAL: "bg-amber-50 text-amber-700",
  APPROVED: "bg-sky-50 text-sky-700",
  PUBLISHED: "bg-emerald-50 text-emerald-700",
  REJECTED: "bg-red-50 text-red-700",
  SUSPENDED: "bg-red-50 text-red-700",
  UNPUBLISHED: "bg-muted text-muted-foreground",
};

export default function SellerListingsPage() {
  const hasRole = useAuthStore((s) => s.hasRole);
  const isOperator = matchesRequiredRole(hasRole, "MARKETPLACE_OPS");

  // Seller identity is derived from the signed-in person's linked IDs (Provider ID);
  // operators may override to inspect another seller's listings.
  const linkedIds = useLinkedIds();
  const derivedSellerId = linkedIds.data?.data?.attributes?.providerId ?? "";

  const [sellerType, setSellerType] = useState("PROVIDER");
  const [sellerId, setSellerId] = useState("");
  const [active, setActive] = useState<{ type: string; id: string } | null>(null);

  // Auto-load the caller's own listings once their Provider ID resolves.
  useEffect(() => {
    if (derivedSellerId && !active) {
      setSellerId(derivedSellerId);
      setActive({ type: "PROVIDER", id: derivedSellerId });
    }
  }, [derivedSellerId, active]);

  const query = useSellerListings(active?.type ?? "", active?.id ?? "");
  const listings = query.data?.listings ?? [];

  return (
    <AppLayout>
      <PageShell title="My Listings" subtitle="Your storefront listings and their lifecycle status." serviceSlug="msika">
        <div className="space-y-6">
          <NompiloContextualGuidance routePath="/marketplace/seller/listings" />

          <form
            className="flex flex-col gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm sm:flex-row sm:items-end"
            onSubmit={(e) => {
              e.preventDefault();
              setActive({ type: sellerType, id: sellerId });
            }}
          >
            <label className="text-sm">
              <span className="mb-1 block font-medium text-foreground">Seller type</span>
              <select value={sellerType} onChange={(e) => setSellerType(e.target.value)} disabled={!isOperator} className="rounded-lg border border-border bg-background px-3 py-2 disabled:opacity-60">
                {["PROVIDER", "FACILITY", "SITE", "PROGRAMME", "VENDOR"].map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </label>
            <label className="flex-1 text-sm">
              <span className="mb-1 block font-medium text-foreground">Seller ID</span>
              <input
                value={sellerId}
                onChange={(e) => setSellerId(e.target.value)}
                readOnly={!isOperator}
                placeholder={isOperator ? "provider/facility ref to inspect" : "resolved from your sign-in"}
                className={`w-full rounded-lg border border-border px-3 py-2 ${isOperator ? "bg-background" : "bg-muted text-muted-foreground"}`}
              />
            </label>
            {isOperator ? <button type="submit" className="rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground">Load</button> : null}
            <Link href="/marketplace/seller/listings/new" className="inline-flex items-center gap-1 rounded-lg border border-border px-4 py-2 font-medium text-foreground hover:bg-background">
              <PlusCircle className="h-4 w-4" /> New
            </Link>
          </form>

          <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
            {!active ? (
              <p className="text-sm text-muted-foreground">
                {isOperator ? "Enter a seller ID to load listings." : "Resolving your seller identity…"}
              </p>
            ) : query.isLoading ? (
              <p className="text-sm text-muted-foreground">Loading…</p>
            ) : query.isError ? (
              <p className="text-sm text-red-600">Could not load your listings.</p>
            ) : listings.length === 0 ? (
              <p className="text-sm text-muted-foreground">No listings yet for this seller.</p>
            ) : (
              <ul className="divide-y divide-border">
                {listings.map((l) => (
                  <li key={l.listingId} className="flex items-center justify-between gap-3 py-3">
                    <div>
                      <div className="font-medium text-foreground">{l.title}</div>
                      <div className="text-xs text-muted-foreground">{l.riskClassification.replace("_", " ")}</div>
                    </div>
                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${STATUS_BADGE[l.status] ?? "bg-muted text-muted-foreground"}`}>
                      {l.status.replace("_", " ")}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
