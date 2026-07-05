"use client";

/**
 * Wallet · Payments & bills. Read-only view of outstanding bills sourced from Costa (via the
 * wallet overview). Real statuses only; no fake payment. Shows the coverage split honestly:
 * what coverage pays vs the patient's own shortfall. The "Pay" action links to the existing
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

function asMoney(v: unknown): number | null {
  if (typeof v === "number" && Number.isFinite(v)) return v;
  if (typeof v === "string" && v.trim() !== "" && !Number.isNaN(Number(v))) return Number(v);
  return null;
}

// Real COSTA BillStatus values only (DRAFT/ACCUMULATING/APPROVAL_PENDING/APPROVED/FINAL/VOID/ADJUSTED).
const STATUS_LABELS: Record<string, string> = {
  DRAFT: "Being prepared",
  ACCUMULATING: "Being prepared",
  APPROVAL_PENDING: "Awaiting approval",
  APPROVED: "Approved",
  FINAL: "Ready to settle",
  ADJUSTED: "Adjusted",
  VOID: "Cancelled",
};

const STATUS_COLOURS: Record<string, string> = {
  DRAFT: "bg-neutral-100 text-muted-foreground",
  ACCUMULATING: "bg-neutral-100 text-muted-foreground",
  APPROVAL_PENDING: "bg-yellow-100 text-yellow-800",
  APPROVED: "bg-blue-100 text-blue-800",
  FINAL: "bg-amber-100 text-amber-800",
  ADJUSTED: "bg-blue-100 text-blue-800",
  VOID: "bg-neutral-100 text-muted-foreground",
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
          Your outstanding bills and their real status, including what your coverage pays and
          what remains for you to settle.
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
            const status = String(b.status ?? "FINAL");
            const cls = STATUS_COLOURS[status] ?? "bg-neutral-100 text-muted-foreground";
            const currency = String(b.currency ?? "");
            const totalPayable = asMoney(b.totalPayable) ?? asMoney(b.total) ?? asMoney(b.amount);
            const coveragePays = asMoney(b.insurerPayable);
            const yourShare = asMoney(b.patientPayable);
            const coverageStatus = b.coverageStatus == null ? null : String(b.coverageStatus);
            return (
              <li key={String(b.billId ?? b.id ?? idx)} className="rounded-lg border border-border bg-card p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-foreground">
                      {String(b.description ?? b.billType ?? "Bill")} #{String(b.billId ?? b.id ?? "")}
                    </p>
                    <p className="text-sm text-foreground">
                      {currency} {totalPayable != null ? totalPayable.toFixed(2) : "—"}
                    </p>
                    <span className={`mt-1 inline-block rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>
                      {STATUS_LABELS[status] ?? status}
                    </span>
                    {(coveragePays != null || yourShare != null) && (
                      <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-0.5 text-xs">
                        <div>
                          <dt className="text-muted-foreground">Coverage pays</dt>
                          <dd className="font-medium text-foreground">
                            {coveragePays != null ? `${currency} ${coveragePays.toFixed(2)}` : "—"}
                          </dd>
                        </div>
                        <div>
                          <dt className="text-muted-foreground">You pay</dt>
                          <dd className={`font-medium ${yourShare != null && yourShare > 0 ? "text-amber-800" : "text-foreground"}`}>
                            {yourShare != null ? `${currency} ${yourShare.toFixed(2)}` : "—"}
                          </dd>
                        </div>
                        {coverageStatus ? (
                          <div className="col-span-2">
                            <dt className="sr-only">Coverage status</dt>
                            <dd className="text-muted-foreground">Coverage status: {coverageStatus}</dd>
                          </div>
                        ) : null}
                      </dl>
                    )}
                  </div>
                  <Link
                    href="/finance"
                    className="shrink-0 rounded bg-blue-700 px-3 py-1 text-xs font-medium text-white hover:bg-blue-800"
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
