"use client";

/**
 * Wallet · Payments & bills. Read-only view of outstanding bills sourced from Costa (via the
 * wallet overview). Real statuses only; no fake payment. The "Pay" action links to the existing
 * finance/wallet payment surface (MusheX rails) rather than inventing a payment flow here.
 */

import Link from "next/link";
import { useWalletOverview } from "@/hooks/queries/useWallet";

type AnyRecord = Record<string, unknown>;

function asList(v: unknown): AnyRecord[] {
  if (Array.isArray(v)) return v.filter((x): x is AnyRecord => typeof x === "object" && x !== null);
  if (v && typeof v === "object") {
    const r = v as AnyRecord;
    for (const k of ["items", "content", "data"]) {
      if (Array.isArray(r[k])) return r[k] as AnyRecord[];
    }
  }
  return [];
}

const STATUS_COLOURS: Record<string, string> = {
  INVOICED: "bg-yellow-100 text-yellow-800",
  FINALIZED: "bg-blue-100 text-blue-800",
  PAID: "bg-green-100 text-green-800",
  CANCELLED: "bg-neutral-100 text-muted-foreground",
};

export default function WalletPaymentsPage() {
  const { data, isLoading, isError } = useWalletOverview();
  const card = (data?.data?.payments ?? {}) as AnyRecord;
  const bills = asList(card.outstanding);
  const unavailable = card.unavailable === true;

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-2">
      <div>
        <h1 className="text-xl font-semibold text-foreground">Payments & bills</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Your outstanding bills and their real status. Settle a bill through your secure payment
          methods.
        </p>
        <p className="mt-1 text-[10px] uppercase tracking-wide text-muted-foreground/70">
          Source: {String(card._source ?? "Costa · MusheX")}
        </p>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading your bills…</p>
      ) : isError || unavailable ? (
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          Billing is temporarily unavailable.
        </div>
      ) : bills.length === 0 ? (
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          You have no outstanding bills.
        </div>
      ) : (
        <ul className="space-y-3">
          {bills.map((b, idx) => {
            const status = String(b.status ?? "INVOICED");
            const cls = STATUS_COLOURS[status] ?? "bg-neutral-100 text-muted-foreground";
            return (
              <li key={idx} className="rounded-lg border border-border bg-card p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium text-foreground">
                      {String(b.description ?? b.billType ?? "Bill")} #{String(b.id ?? "")}
                    </p>
                    <p className="text-sm text-foreground">
                      {String(b.currency ?? "")} {String(b.total ?? b.amount ?? "—")}
                    </p>
                    <span className={`mt-1 inline-block rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>
                      {status}
                    </span>
                  </div>
                  <Link
                    href="/finance"
                    className="rounded bg-blue-700 px-3 py-1 text-xs font-medium text-white hover:bg-blue-800"
                  >
                    Pay
                  </Link>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
