"use client";

/**
 * Full Transaction History — paginated, filterable transaction table.
 * Route: /wallet/transactions
 *
 * Filters: date range, transaction type, channel, direction (credit/debit).
 * Paginated via page/size parameters.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Filter,
  Loader2,
  Receipt,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useWalletByOwner,
  useTransactions,
} from "@/hooks/queries/useMusheWallet";

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

function readStr(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "string" && x.length > 0) return x;
  }
  return "";
}

function readNum(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "number") return x;
    if (typeof x === "string" && x.trim()) {
      const n = Number(x);
      if (!Number.isNaN(n)) return n;
    }
  }
  return 0;
}

function unwrapItems(v: unknown): unknown[] {
  if (!v) return [];
  if (Array.isArray(v)) return v;
  const r = v as Record<string, unknown>;
  if (Array.isArray(r.items)) return r.items;
  if (Array.isArray(r.content)) return r.content;
  if (r.data != null) return unwrapItems(r.data);
  return [];
}

function unwrapMeta(v: unknown): Record<string, unknown> {
  if (!v || typeof v !== "object") return {};
  const r = v as Record<string, unknown>;
  if (r.meta && typeof r.meta === "object") return r.meta as Record<string, unknown>;
  return {};
}

function formatMoney(n: number, currency = "USD") {
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(n);
  } catch {
    return `${currency} ${n.toFixed(2)}`;
  }
}

function channelBadge(channel: string) {
  const ch = channel.toUpperCase();
  if (ch === "MOBILE_MONEY") return "bg-yellow-100 text-yellow-800";
  if (ch === "CARD" || ch === "POS") return "bg-purple-100 text-purple-800";
  if (ch === "BANK_TRANSFER") return "bg-primary-soft text-primary-hover";
  if (ch === "CASH") return "bg-emerald-100 text-primary-hover";
  return "bg-neutral-100 text-foreground";
}

function statusBadge(status: string) {
  const s = status.toUpperCase();
  if (s === "COMPLETED" || s === "SUCCESS") return "bg-green-100 text-green-800";
  if (s === "PENDING") return "bg-amber-100 text-warning-foreground";
  if (s === "FAILED" || s === "REVERSED") return "bg-red-100 text-red-800";
  return "bg-neutral-100 text-foreground";
}

const CHANNELS = ["", "MOBILE_MONEY", "BANK_TRANSFER", "CARD", "POS", "CASH", "USSD"] as const;
const DIRECTIONS = ["", "CREDIT", "DEBIT"] as const;

export default function TransactionHistoryPage() {
  const cpid = useAuthStore((s) => s.user?.id) ?? "";
  const [page, setPage] = useState(0);
  const pageSize = 20;

  // Filters (client-side until backend supports query params)
  const [showFilters, setShowFilters] = useState(false);
  const [filterChannel, setFilterChannel] = useState("");
  const [filterDirection, setFilterDirection] = useState("");
  const [filterDateFrom, setFilterDateFrom] = useState("");
  const [filterDateTo, setFilterDateTo] = useState("");
  const [filterTxnType, setFilterTxnType] = useState("");

  const walletQ = useWalletByOwner(cpid ? "PERSON" : null, cpid || null);
  const walletData = useMemo(() => {
    const raw = asRecord(walletQ.data).data ?? walletQ.data;
    return asRecord(raw);
  }, [walletQ.data]);
  const walletId = readStr(walletData, "walletId", "wallet_id", "id");
  const currency = readStr(walletData, "currency") || "USD";

  const txQ = useTransactions(walletId || null, page, pageSize);
  const rawMeta = useMemo(() => {
    const raw = txQ.data;
    const r = asRecord(raw);
    if (!raw || typeof raw !== "object") return {};
    // Try paged response format
    if (r.data && typeof r.data === "object") {
      const d = asRecord(r.data);
      if (typeof d.totalElements === "number") return d;
    }
    return unwrapMeta(raw);
  }, [txQ.data]);

  const allRows = useMemo(() => {
    const raw = asRecord(txQ.data).data ?? txQ.data;
    return unwrapItems(raw).map(asRecord);
  }, [txQ.data]);

  // Apply client-side filters
  const txRows = useMemo(() => {
    return allRows.filter((row) => {
      if (filterChannel) {
        const ch = readStr(row, "channel").toUpperCase();
        if (ch !== filterChannel.toUpperCase()) return false;
      }
      if (filterDirection) {
        const dir = readStr(row, "direction", "txnType", "txn_type").toUpperCase();
        if (filterDirection === "CREDIT" && !dir.includes("CREDIT") && !dir.includes("DEPOSIT")) return false;
        if (filterDirection === "DEBIT" && !dir.includes("DEBIT") && !dir.includes("PAYMENT") && !dir.includes("TRANSFER")) return false;
      }
      if (filterDateFrom) {
        const d = readStr(row, "createdAt", "created_at", "txnDate", "txn_date").slice(0, 10);
        if (d < filterDateFrom) return false;
      }
      if (filterDateTo) {
        const d = readStr(row, "createdAt", "created_at", "txnDate", "txn_date").slice(0, 10);
        if (d > filterDateTo) return false;
      }
      if (filterTxnType) {
        const t = readStr(row, "txnType", "txn_type").toUpperCase();
        if (!t.includes(filterTxnType.toUpperCase())) return false;
      }
      return true;
    });
  }, [allRows, filterChannel, filterDirection, filterDateFrom, filterDateTo, filterTxnType]);

  const totalElements = readNum(rawMeta as Record<string, unknown>, "totalElements", "total_elements", "total");
  const totalPages = totalElements > 0 ? Math.ceil(totalElements / pageSize) : 1;

  return (
    <AppLayout>
      <PageShell
        title="Transaction History"
        subtitle="Full transaction history for your MusheX wallet"
        icon={<Receipt className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/wallet"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to wallet
          </Link>
        </div>

        {!cpid && (
          <p className="text-sm text-warning-foreground bg-warning-soft border border-amber-100 rounded-lg px-3 py-2">
            Sign in to view your transaction history.
          </p>
        )}

        {cpid && walletQ.isLoading && (
          <div className="flex items-center gap-2 text-muted-foreground text-sm py-8">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading wallet...
          </div>
        )}

        {cpid && !walletQ.isLoading && !walletId && (
          <p className="text-sm text-danger bg-danger-soft border border-red-100 rounded-lg px-3 py-2">
            No wallet found.
          </p>
        )}

        {cpid && walletId && (
          <div className="space-y-4">
            {/* Filter toggle */}
            <div className="flex items-center justify-between">
              <button
                type="button"
                onClick={() => setShowFilters(!showFilters)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-2 text-sm font-medium text-foreground hover:bg-background"
              >
                <Filter className="h-4 w-4" /> {showFilters ? "Hide filters" : "Filters"}
              </button>
              <div className="text-xs text-muted-foreground">
                {totalElements > 0 && `${totalElements} total transactions`}
              </div>
            </div>

            {/* Filters */}
            {showFilters && (
              <div className="rounded-lg border border-border bg-background p-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
                <div>
                  <label className="block text-xs font-medium text-muted-foreground mb-1">Date from</label>
                  <input
                    type="date"
                    className="w-full rounded-lg border border-border px-2 py-2 text-sm"
                    value={filterDateFrom}
                    onChange={(e) => setFilterDateFrom(e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-muted-foreground mb-1">Date to</label>
                  <input
                    type="date"
                    className="w-full rounded-lg border border-border px-2 py-2 text-sm"
                    value={filterDateTo}
                    onChange={(e) => setFilterDateTo(e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-muted-foreground mb-1">Transaction type</label>
                  <input
                    className="w-full rounded-lg border border-border px-2 py-2 text-sm"
                    placeholder="e.g. TRANSFER"
                    value={filterTxnType}
                    onChange={(e) => setFilterTxnType(e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-muted-foreground mb-1">Channel</label>
                  <select
                    className="w-full rounded-lg border border-border px-2 py-2 text-sm"
                    value={filterChannel}
                    onChange={(e) => setFilterChannel(e.target.value)}
                  >
                    {CHANNELS.map((ch) => (
                      <option key={ch} value={ch}>{ch ? ch.replace(/_/g, " ") : "All"}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-muted-foreground mb-1">Direction</label>
                  <select
                    className="w-full rounded-lg border border-border px-2 py-2 text-sm"
                    value={filterDirection}
                    onChange={(e) => setFilterDirection(e.target.value)}
                  >
                    {DIRECTIONS.map((d) => (
                      <option key={d} value={d}>{d || "All"}</option>
                    ))}
                  </select>
                </div>
              </div>
            )}

            {/* Transactions table */}
            <div className="rounded-xl border border-border bg-card shadow-sm overflow-hidden">
              <div className="overflow-x-auto">
                {txQ.isLoading && (
                  <div className="p-6 flex items-center gap-2 text-muted-foreground text-sm">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading transactions...
                  </div>
                )}
                {!txQ.isLoading && txQ.isError && (
                  <p className="p-4 text-sm text-danger">Could not load transactions.</p>
                )}
                {!txQ.isLoading && !txQ.isError && (
                  <table className="min-w-full text-sm">
                    <thead className="bg-background text-left text-xs text-muted-foreground uppercase tracking-wide">
                      <tr>
                        <th className="px-4 py-2">Date</th>
                        <th className="px-4 py-2">Description</th>
                        <th className="px-4 py-2">Counterparty</th>
                        <th className="px-4 py-2 text-right">Amount</th>
                        <th className="px-4 py-2 text-right">Fee</th>
                        <th className="px-4 py-2 text-right">Net</th>
                        <th className="px-4 py-2 text-right">Balance after</th>
                        <th className="px-4 py-2">Channel</th>
                        <th className="px-4 py-2">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {txRows.length === 0 && (
                        <tr>
                          <td colSpan={9} className="px-4 py-6 text-center text-muted-foreground">
                            {allRows.length > 0 ? "No transactions match the current filters." : "No transactions yet."}
                          </td>
                        </tr>
                      )}
                      {txRows.map((row, i) => {
                        const direction = readStr(row, "direction", "txnType", "txn_type");
                        const isCredit = direction.toUpperCase().includes("CREDIT") || direction.toUpperCase().includes("DEPOSIT");
                        const amount = readNum(row, "amount");
                        const fee = readNum(row, "fee", "feeAmount", "fee_amount");
                        const net = amount - fee;
                        const balAfter = readNum(row, "balanceAfter", "balance_after", "runningBalance", "running_balance");
                        const channel = readStr(row, "channel");
                        const txnStatus = readStr(row, "status") || "COMPLETED";
                        const counterparty = readStr(row, "counterpartyName", "counterparty_name") ||
                          readStr(row, "counterpartyRef", "counterparty_ref");

                        return (
                          <tr key={readStr(row, "txnId", "txn_id", "transactionId", "transaction_id") || String(i)} className="hover:bg-background/80">
                            <td className="px-4 py-2 whitespace-nowrap text-foreground">
                              {readStr(row, "createdAt", "created_at", "txnDate", "txn_date").slice(0, 16).replace("T", " ")}
                            </td>
                            <td className="px-4 py-2 text-foreground max-w-xs truncate">
                              {readStr(row, "description") || readStr(row, "txnType", "txn_type")}
                            </td>
                            <td className="px-4 py-2 text-foreground text-xs">
                              {counterparty || "--"}
                            </td>
                            <td className={`px-4 py-2 text-right tabular-nums font-medium ${isCredit ? "text-primary-hover" : "text-danger"}`}>
                              {isCredit ? "+" : "-"}{formatMoney(amount, currency)}
                            </td>
                            <td className="px-4 py-2 text-right tabular-nums text-muted-foreground">
                              {fee > 0 ? formatMoney(fee, currency) : "--"}
                            </td>
                            <td className="px-4 py-2 text-right tabular-nums text-foreground">
                              {fee > 0 ? formatMoney(net, currency) : "--"}
                            </td>
                            <td className="px-4 py-2 text-right tabular-nums font-medium text-foreground">
                              {formatMoney(balAfter, currency)}
                            </td>
                            <td className="px-4 py-2">
                              {channel && (
                                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${channelBadge(channel)}`}>
                                  {channel}
                                </span>
                              )}
                            </td>
                            <td className="px-4 py-2">
                              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusBadge(txnStatus)}`}>
                                {txnStatus}
                              </span>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            </div>

            {/* Pagination */}
            <div className="flex items-center justify-between">
              <div className="text-xs text-muted-foreground">
                Page {page + 1} of {totalPages}
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="inline-flex items-center gap-1 rounded-lg border border-border bg-card px-3 py-1.5 text-sm text-foreground hover:bg-background disabled:opacity-40"
                >
                  <ChevronLeft className="h-4 w-4" /> Previous
                </button>
                <button
                  type="button"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={page + 1 >= totalPages}
                  className="inline-flex items-center gap-1 rounded-lg border border-border bg-card px-3 py-1.5 text-sm text-foreground hover:bg-background disabled:opacity-40"
                >
                  Next <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
