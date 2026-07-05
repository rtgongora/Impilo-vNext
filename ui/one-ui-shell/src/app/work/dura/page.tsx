"use client";

import { useState } from "react";
import Link from "next/link";
import {
  Boxes,
  Search,
  CalendarClock,
  AlertOctagon,
  ThermometerSnowflake,
  Loader2,
  Snowflake,
  Lock,
  PackageX,
  RefreshCw,
  Warehouse,
  ScrollText,
  ArrowUpRight,
} from "lucide-react";
import {
  useDuraCommodities,
  useDuraNearExpiryBatches,
  useDuraRecalls,
  useDuraColdChainExcursions,
  useDuraStockouts,
  useDuraExternalSync,
  useDuraOnHand,
  useDuraLedger,
} from "@/hooks/queries/useDura";
import { useFacilityStore } from "@/hooks/useFacilityStore";

/**
 * Dura — Stock & Supply operations surface.
 *
 * Live view of the Dura sovereign stock brain (inventory-service via BFF):
 * commodity catalogue search, facility stock balance, append-only ledger,
 * stockouts, near-expiry batches, open recalls, cold-chain excursions, and
 * eLMIS/NatPharm adapter sync status. Movements (receive/issue/transfer/
 * adjust, counts, reconciliation, requisitions) run on the linked
 * /inventory operational pages.
 */
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const OPS_LINKS: { href: string; label: string }[] = [
  { href: "/inventory/stock-management", label: "Receive / Issue / Transfer / Adjust" },
  { href: "/inventory/counts", label: "Stock counts" },
  { href: "/inventory/reconciliation", label: "Reconciliation" },
  { href: "/inventory/requisitions", label: "Requisitions" },
  { href: "/inventory/items", label: "Item registry" },
];

export default function DuraStockPage() {
  const [query, setQuery] = useState("");
  const [manualFacilityId, setManualFacilityId] = useState("");

  const facility = useFacilityStore((s) => s.facility);
  // Dura stock is keyed by facility UUID; the session facility context is used
  // when it carries one, otherwise the operator can enter it explicitly.
  const contextFacilityId = facility?.id && UUID_RE.test(facility.id) ? facility.id : "";
  const facilityId = contextFacilityId || (UUID_RE.test(manualFacilityId) ? manualFacilityId : "");

  const commodities = useDuraCommodities(query ? { q: query } : undefined);
  const nearExpiry = useDuraNearExpiryBatches(90);
  const recalls = useDuraRecalls("OPEN");
  const excursions = useDuraColdChainExcursions("OPEN");
  const stockouts = useDuraStockouts(facilityId || undefined);
  const externalSync = useDuraExternalSync();
  const onHand = useDuraOnHand(facilityId || undefined, { size: 20 });
  const ledger = useDuraLedger(facilityId || undefined, { size: 20 });

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Boxes className="h-7 w-7 text-emerald-600" />
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Dura — Stock &amp; Supply</h1>
            <p className="text-sm text-slate-500">
              Native commodity, stock, inventory &amp; supply management. Live from inventory-service.
            </p>
          </div>
        </div>
        <nav className="flex flex-wrap gap-2" aria-label="Stock operations">
          {OPS_LINKS.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-600 shadow-sm hover:border-emerald-400 hover:text-emerald-700"
            >
              {l.label} <ArrowUpRight className="h-3 w-3" />
            </Link>
          ))}
        </nav>
      </header>

      {!facilityId ? (
        <section className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
          <p className="mb-2">
            Stock balance, ledger, and stockouts are per facility. No facility UUID in the current
            session context — enter one to query facility-scoped stock truth.
          </p>
          <input
            value={manualFacilityId}
            onChange={(e) => setManualFacilityId(e.target.value.trim())}
            placeholder="Facility UUID…"
            className="w-full max-w-md rounded-md border border-amber-300 bg-white px-3 py-2 text-sm text-slate-700 focus:border-emerald-500 focus:outline-none"
          />
        </section>
      ) : null}

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

      {/* Facility stock balance (ledger-derived on-hand projection) */}
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-3 flex items-center gap-2 text-base font-medium text-slate-800">
          <Warehouse className="h-5 w-5 text-emerald-600" /> Facility stock balance
        </h2>
        {!facilityId ? (
          <Empty message="Awaiting facility context (see banner above)." />
        ) : onHand.isLoading ? (
          <Loading />
        ) : onHand.isError ? (
          <Empty message="Stock balance query failed — inventory-service unavailable." />
        ) : !onHand.data || onHand.data.length === 0 ? (
          <Empty message="No stock recorded at this facility yet. Balances appear after the first ledger movement (receipt / opening balance)." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-100 text-xs uppercase text-slate-400">
                  <th className="py-2 pr-4">Item</th>
                  <th className="py-2 pr-4">Batch</th>
                  <th className="py-2 pr-4">Expiry</th>
                  <th className="py-2 pr-4 text-right">On hand</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {onHand.data.map((r) => (
                  <tr key={r.id}>
                    <td className="py-2 pr-4 font-medium text-slate-700">{r.itemCode}</td>
                    <td className="py-2 pr-4 text-slate-500">{r.batch ?? "—"}</td>
                    <td className="py-2 pr-4 text-slate-500">{r.expiry ?? "—"}</td>
                    <td
                      className={`py-2 pr-4 text-right font-medium ${
                        r.qtyOnHand <= 0 ? "text-red-600" : "text-slate-700"
                      }`}
                    >
                      {r.qtyOnHand}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Append-only stock ledger */}
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-1 flex items-center gap-2 text-base font-medium text-slate-800">
          <ScrollText className="h-5 w-5 text-slate-500" /> Stock ledger
        </h2>
        <p className="mb-3 text-xs text-slate-500">
          Append-only movement truth — every balance above derives from these events. No silent
          stock changes.
        </p>
        {!facilityId ? (
          <Empty message="Awaiting facility context (see banner above)." />
        ) : ledger.isLoading ? (
          <Loading />
        ) : ledger.isError ? (
          <Empty message="Ledger query failed — inventory-service unavailable." />
        ) : !ledger.data || ledger.data.length === 0 ? (
          <Empty message="No ledger events recorded for this facility yet." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-100 text-xs uppercase text-slate-400">
                  <th className="py-2 pr-4">Type</th>
                  <th className="py-2 pr-4">Item</th>
                  <th className="py-2 pr-4 text-right">Δ Qty</th>
                  <th className="py-2 pr-4">Ref</th>
                  <th className="py-2 pr-4">Actor</th>
                  <th className="py-2 pr-4">When</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {ledger.data.map((e) => (
                  <tr key={e.eventId}>
                    <td className="py-2 pr-4">
                      <span
                        className={`rounded px-2 py-0.5 text-xs ${
                          e.qtyDelta >= 0
                            ? "bg-emerald-50 text-emerald-700"
                            : "bg-amber-50 text-amber-700"
                        }`}
                      >
                        {e.eventType}
                      </span>
                    </td>
                    <td className="py-2 pr-4 font-medium text-slate-700">
                      {e.itemCode}
                      {e.batch ? <span className="ml-1 text-xs text-slate-400">{e.batch}</span> : null}
                    </td>
                    <td
                      className={`py-2 pr-4 text-right font-medium ${
                        e.qtyDelta >= 0 ? "text-emerald-700" : "text-amber-700"
                      }`}
                    >
                      {e.qtyDelta > 0 ? `+${e.qtyDelta}` : e.qtyDelta}
                    </td>
                    <td className="py-2 pr-4 text-slate-500">
                      {e.refType ? `${e.refType} · ${e.refId ?? "—"}` : "—"}
                    </td>
                    <td className="py-2 pr-4 text-slate-500">{e.actorId ?? "—"}</td>
                    <td className="py-2 pr-4 text-slate-500">
                      {e.createdAt ? new Date(e.createdAt).toLocaleString() : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Stockouts */}
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-base font-medium text-slate-800">
            <PackageX className="h-5 w-5 text-red-500" /> Stockouts
          </h2>
          {!facilityId ? (
            <Empty message="Awaiting facility context (see banner above)." />
          ) : stockouts.isLoading ? (
            <Loading />
          ) : stockouts.isError ? (
            <Empty message="Stockout query failed — inventory-service unavailable." />
          ) : !stockouts.data || stockouts.data.length === 0 ? (
            <Empty message="No stocked-out items at this facility." />
          ) : (
            <ul className="space-y-2 text-sm">
              {stockouts.data.slice(0, 20).map((s) => (
                <li key={s.id} className="flex items-center justify-between">
                  <span className="text-slate-700">
                    {s.itemCode}
                    {s.batch ? ` · ${s.batch}` : ""}
                  </span>
                  <span className="rounded bg-red-50 px-2 py-0.5 text-xs text-red-700">
                    on hand {s.qtyOnHand}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* eLMIS / NatPharm sync status */}
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-base font-medium text-slate-800">
            <RefreshCw className="h-5 w-5 text-slate-500" /> eLMIS / NatPharm sync
          </h2>
          <p className="mb-3 text-xs text-slate-500">
            Dura is the native stock truth; eLMIS/NatPharm are external adapters. Pharmacy
            dispense sync is a recorded deferred seam — no sync success is shown unless a real
            sync happened.
          </p>
          {externalSync.isLoading ? (
            <Loading />
          ) : externalSync.isError ? (
            <Empty message="Sync status unavailable — inventory-service unreachable." />
          ) : !externalSync.data || externalSync.data.length === 0 ? (
            <Empty message="No external sync activity recorded." />
          ) : (
            <ul className="space-y-2 text-sm">
              {externalSync.data.slice(0, 20).map((s) => (
                <li key={s.syncId} className="flex items-center justify-between gap-2">
                  <span className="min-w-0 truncate text-slate-700">
                    {s.externalSystem} · {s.entityType} · {s.entityRef}
                  </span>
                  <span
                    className={`shrink-0 rounded px-2 py-0.5 text-xs ${
                      s.status === "SYNCED"
                        ? "bg-emerald-50 text-emerald-700"
                        : s.status === "FAILED"
                          ? "bg-red-50 text-red-700"
                          : "bg-amber-50 text-amber-700"
                    }`}
                    title={s.lastError ?? undefined}
                  >
                    {s.status}
                    {s.attempts > 0 ? ` · ${s.attempts} att.` : ""}
                  </span>
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
