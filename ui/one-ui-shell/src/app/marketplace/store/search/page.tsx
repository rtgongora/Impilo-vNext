"use client";

import { useState } from "react";
import Link from "next/link";
import { Search, ArrowRight } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { AddToCartButton } from "@/components/marketplace/AddToCartButton";
import { useStoreSearch } from "@/hooks/queries/useMsikaStore";

const RISKS = ["ALL", "UNRESTRICTED", "LOW_RISK", "MODERATE_RISK", "HIGH_RISK", "REGULATED"];

export default function MarketplaceSearchPage() {
  const [query, setQuery] = useState("");
  const [risk, setRisk] = useState("ALL");
  const [active, setActive] = useState({ q: "", risk: "ALL" });
  const results = useStoreSearch(active.q, active.risk);
  const listings = results.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Search Listings" subtitle="Only published, policy-eligible listings are shown." serviceSlug="msika">
        <div className="space-y-6">
          <NompiloContextualGuidance routePath="/marketplace/store/search" />

          <form
            className="flex flex-col gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm sm:flex-row sm:items-end"
            onSubmit={(e) => {
              e.preventDefault();
              setActive({ q: query, risk });
            }}
          >
            <label className="flex-1 text-sm">
              <span className="mb-1 block font-medium text-foreground">Search</span>
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="e.g. blood pressure check, paracetamol"
                className="w-full rounded-lg border border-border bg-background px-3 py-2"
              />
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-foreground">Risk</span>
              <select
                value={risk}
                onChange={(e) => setRisk(e.target.value)}
                className="rounded-lg border border-border bg-background px-3 py-2"
              >
                {RISKS.map((r) => (
                  <option key={r} value={r}>
                    {r.replace("_", " ")}
                  </option>
                ))}
              </select>
            </label>
            <button type="submit" className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground">
              <Search className="h-4 w-4" /> Search
            </button>
          </form>

          <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
            {results.isLoading ? (
              <p className="text-sm text-muted-foreground">Searching…</p>
            ) : results.isError ? (
              <p className="text-sm text-red-600">Search is unavailable right now.</p>
            ) : listings.length === 0 ? (
              <p className="text-sm text-muted-foreground">No published listings match your search.</p>
            ) : (
              <ul className="divide-y divide-border">
                {listings.map((l) => (
                  <li key={l.listingId} className="flex items-center justify-between gap-3 py-3">
                    <Link href={`/marketplace/store/listing/${l.listingId}`} className="flex min-w-0 flex-1 items-center justify-between gap-3 hover:text-primary">
                      <div className="min-w-0">
                        <div className="font-medium text-foreground">{l.title}</div>
                        <div className="text-xs text-muted-foreground">
                          {l.riskClassification.replace("_", " ")} · {l.priceDisplay ?? "Price set by seller"}
                        </div>
                      </div>
                      <ArrowRight className="h-4 w-4 shrink-0" />
                    </Link>
                    {!l.clinicalRoute ? <AddToCartButton listingId={l.listingId} label="Add" compact /> : null}
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
