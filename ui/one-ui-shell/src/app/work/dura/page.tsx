"use client";

import { useState } from "react";
import {
  Boxes,
  Search,
  CalendarClock,
  AlertOctagon,
  ThermometerSnowflake,
  Loader2,
  Snowflake,
  Lock,
} from "lucide-react";
import {
  useDuraCommodities,
  useDuraNearExpiryBatches,
  useDuraRecalls,
  useDuraColdChainExcursions,
} from "@/hooks/queries/useDura";

/**
 * Dura — Stock & Supply operations surface.
 *
 * Live view of the Dura sovereign stock brain (inventory-service via BFF):
 * commodity catalogue search, near-expiry batches, open recalls, and open
 * cold-chain excursions.
 */
export default function DuraStockPage() {
  const [query, setQuery] = useState("");

  const commodities = useDuraCommodities(query ? { q: query } : undefined);
  const nearExpiry = useDuraNearExpiryBatches(90);
  const recalls = useDuraRecalls("OPEN");
  const excursions = useDuraColdChainExcursions("OPEN");

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header className="flex items-center gap-3">
        <Boxes className="h-7 w-7 text-emerald-600" />
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Dura — Stock &amp; Supply</h1>
          <p className="text-sm text-slate-500">
            Native commodity, stock, inventory &amp; supply management. Live from inventory-service.
          </p>
        </div>
      </header>

      {/* Commodity catalogue */}
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center justify-between gap-4">
          <h2 className="flex items-center gap-2 text-lg font-medium text-slate-800">
            <Boxes className="h-5 w-5 text-slate-500" /> Commodity catalogue
          </h2>
          <div className="relative w-72 max-w-full">
            <Search className="pointer-events-none absolute left-2 top-2.5 h-4 w-4 text-slate-400" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search commodities…"
              className="w-full rounded-md border border-slate-300 py-2 pl-8 pr-3 text-sm focus:border-emerald-500 focus:outline-none"
            />
          </div>
        </div>
        {commodities.isLoading ? (
          <Loading />
        ) : !commodities.data || commodities.data.length === 0 ? (
          <Empty message={query ? "No commodities match your search." : "No commodities in the catalogue yet."} />
        ) : (
          <ul className="divide-y divide-slate-100">
            {commodities.data.slice(0, 50).map((c) => (
              <li key={c.itemId} className="flex items-center justify-between py-2 text-sm">
                <div>
                  <span className="font-medium text-slate-800">{c.name}</span>
                  <span className="ml-2 text-slate-400">{c.itemCode}</span>
                </div>
                <div className="flex items-center gap-2 text-xs">
                  {c.programmeArea ? (
                    <span className="rounded bg-slate-100 px-2 py-0.5 text-slate-600">{c.programmeArea}</span>
                  ) : null}
                  {c.coldChainRequired ? (
                    <span className="flex items-center gap-1 rounded bg-sky-50 px-2 py-0.5 text-sky-700">
                      <Snowflake className="h-3 w-3" /> Cold chain
                    </span>
                  ) : null}
                  {c.controlled ? (
                    <span className="flex items-center gap-1 rounded bg-amber-50 px-2 py-0.5 text-amber-700">
                      <Lock className="h-3 w-3" /> Controlled
                    </span>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Near-expiry batches */}
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-base font-medium text-slate-800">
            <CalendarClock className="h-5 w-5 text-amber-500" /> Near-expiry (90d)
          </h2>
          {nearExpiry.isLoading ? (
            <Loading />
          ) : !nearExpiry.data || nearExpiry.data.length === 0 ? (
            <Empty message="No batches nearing expiry." />
          ) : (
            <ul className="space-y-2 text-sm">
              {nearExpiry.data.slice(0, 20).map((b) => (
                <li key={b.batchLotId} className="flex items-center justify-between">
                  <span className="text-slate-700">
                    {b.itemCode} · {b.batchNumber}
                  </span>
                  <span className="text-amber-600">{b.expiryDate ?? "—"}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Open recalls */}
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-base font-medium text-slate-800">
            <AlertOctagon className="h-5 w-5 text-red-500" /> Open recalls
          </h2>
          {recalls.isLoading ? (
            <Loading />
          ) : !recalls.data || recalls.data.length === 0 ? (
            <Empty message="No open recalls." />
          ) : (
            <ul className="space-y-2 text-sm">
              {recalls.data.slice(0, 20).map((r) => (
                <li key={r.recallId} className="flex items-center justify-between">
                  <span className="text-slate-700">
                    {r.itemCode ?? "—"}
                    {r.batchNumber ? ` · ${r.batchNumber}` : ""}
                  </span>
                  <span className="rounded bg-red-50 px-2 py-0.5 text-xs text-red-700">
                    {r.severity} · {r.affectedBatches}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Open cold-chain excursions */}
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-base font-medium text-slate-800">
            <ThermometerSnowflake className="h-5 w-5 text-sky-500" /> Cold-chain excursions
          </h2>
          {excursions.isLoading ? (
            <Loading />
          ) : !excursions.data || excursions.data.length === 0 ? (
            <Empty message="No open excursions." />
          ) : (
            <ul className="space-y-2 text-sm">
              {excursions.data.slice(0, 20).map((e) => (
                <li key={e.excursionId} className="flex items-center justify-between">
                  <span className="text-slate-700">{e.temperature}&deg;C</span>
                  <span className="rounded bg-sky-50 px-2 py-0.5 text-xs text-sky-700">{e.breach}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}

function Loading() {
  return (
    <div className="flex items-center gap-2 py-6 text-sm text-slate-400">
      <Loader2 className="h-4 w-4 animate-spin" /> Loading…
    </div>
  );
}

function Empty({ message }: { message: string }) {
  return <p className="py-6 text-sm text-slate-400">{message}</p>;
}
