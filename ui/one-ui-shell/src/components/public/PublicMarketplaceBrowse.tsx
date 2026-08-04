"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, Search, Store, BadgeCheck, Package } from "lucide-react";
import { apiClient } from "@/lib/api-client";

/**
 * Public marketplace browse (PF-W3). Browse/search PUBLISHED health-product/service listings
 * and the approved-vendor directory WITHOUT sign-in, via the anonymous gateway lane. Allow-
 * listed catalogue facts only (no seller PII, no orders). Ordering/paying requires sign-in;
 * a basket is held client-side and claimed at sign-in (PD-7).
 */

interface Listing {
  id?: string;
  title?: string;
  summary?: string;
  riskClassification?: string;
  priceDisplay?: string;
  priceAmount?: number | null;
  priceCurrency?: string;
  fulfilmentMode?: string;
  status?: string;
}
interface ListingsResponse {
  results?: Listing[];
  total?: number;
}
interface Vendor {
  id?: string;
  name?: string;
  type?: string;
  status?: string;
  coverage?: string;
}
interface VendorsResponse {
  results?: Vendor[];
}

function priceOf(l: Listing): string | null {
  if (l.priceDisplay) return l.priceDisplay;
  if (l.priceAmount === null || l.priceAmount === undefined) return null;
  return `${l.priceCurrency ?? ""} ${l.priceAmount}`.trim();
}

export function PublicMarketplaceBrowse() {
  const [query, setQuery] = useState("");
  const [submitted, setSubmitted] = useState("");
  const [listings, setListings] = useState<Listing[] | null>(null);
  const [vendors, setVendors] = useState<Vendor[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError("");
      const params = new URLSearchParams({ page: "0", size: "24" });
      if (submitted) params.set("q", submitted);
      try {
        const [l, v] = await Promise.all([
          apiClient.get<ListingsResponse>(`/internal/v1/public/gateway/marketplace/listings?${params.toString()}`),
          apiClient.get<VendorsResponse>("/internal/v1/public/gateway/marketplace/vendors?page=0&size=12"),
        ]);
        if (!cancelled) {
          setListings(l?.results ?? []);
          setVendors(v?.results ?? []);
        }
      } catch {
        if (!cancelled) setError("The marketplace is temporarily unavailable. Please try again shortly.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [submitted]);

  return (
    <div className="space-y-8">
      <form
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          setSubmitted(query.trim());
        }}
        className="flex items-center gap-2 rounded-full border border-slate-300 bg-white p-2 pl-4"
      >
        <Search className="h-5 w-5 shrink-0 text-emerald-600" aria-hidden />
        <label htmlFor="mkt-q" className="sr-only">
          Search products and services
        </label>
        <input
          id="mkt-q"
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search medicines, products, services…"
          className="min-w-0 flex-1 bg-transparent py-1.5 text-[15px] focus:outline-none"
        />
        <button
          type="submit"
          className="rounded-full bg-emerald-600 px-5 py-2 text-sm font-semibold text-white hover:bg-emerald-700"
        >
          Search
        </button>
      </form>

      {loading && (
        <p className="flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading the marketplace…
        </p>
      )}
      {error && <p className="text-sm text-red-700">{error}</p>}

      {listings && (
        <section aria-labelledby="listings-h" data-testid="marketplace-listings">
          <h2 id="listings-h" className="flex items-center gap-2 text-lg font-bold text-slate-900">
            <Package className="h-5 w-5 text-emerald-600" aria-hidden /> Products &amp; services
          </h2>
          {listings.length === 0 ? (
            <p className="mt-3 text-sm text-slate-600" data-testid="marketplace-empty">
              No listings match your search yet.
            </p>
          ) : (
            <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {listings.map((l) => {
                const price = priceOf(l);
                return (
                  <div key={l.id} className="flex flex-col rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-4">
                    <div className="flex items-start justify-between gap-2">
                      <p className="font-semibold text-slate-900">{l.title}</p>
                      {l.riskClassification && (
                        <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600">
                          {l.riskClassification}
                        </span>
                      )}
                    </div>
                    {l.summary && <p className="mt-1 flex-1 text-[13px] leading-relaxed text-slate-600">{l.summary}</p>}
                    <div className="mt-3 flex items-center justify-between">
                      <span className="text-sm font-bold text-emerald-800">{price ?? "Ask for price"}</span>
                      <Link
                        href={`/auth/login?returnTo=%2Fmarketplace`}
                        className="rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-1.5 text-[13px] font-semibold text-emerald-800 hover:bg-emerald-100"
                      >
                        Sign in to buy
                      </Link>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      )}

      {vendors && vendors.length > 0 && (
        <section aria-labelledby="vendors-h" data-testid="marketplace-vendors">
          <h2 id="vendors-h" className="flex items-center gap-2 text-lg font-bold text-slate-900">
            <Store className="h-5 w-5 text-emerald-600" aria-hidden /> Approved suppliers
          </h2>
          <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {vendors.map((v) => (
              <div key={v.id} className="rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-4">
                <div className="flex items-center gap-2">
                  <p className="font-semibold text-slate-900">{v.name}</p>
                  {v.status === "ACTIVE" && (
                    <BadgeCheck className="h-4 w-4 text-emerald-600" aria-label="Verified supplier" />
                  )}
                </div>
                {v.type && <p className="text-xs text-slate-500">{v.type}</p>}
              </div>
            ))}
          </div>
        </section>
      )}

      <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
        <p className="text-sm font-medium text-emerald-900">Ready to order?</p>
        <p className="mt-1 text-sm text-emerald-800">
          Browse freely. Sign in to add to your basket, pay, submit a prescription and track
          delivery — your selections are kept while you sign in.
        </p>
        <Link
          href="/auth/login?returnTo=%2Fmarketplace"
          className="mt-2 inline-block rounded-lg bg-emerald-700 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-800"
        >
          Sign in to order
        </Link>
      </div>
    </div>
  );
}
