"use client";

/**
 * Mushe Wallet Dashboard — main wallet overview.
 * Route: /wallet
 *
 * Sections: balance card, quick actions, recent transactions,
 * my cards, funding sources.
 */

import Link from "next/link";
import { useMemo } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  CreditCard,
  Loader2,
  Plus,
  Wallet,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useWalletByOwner,
  useBalance,
  useTransactions,
  useCards,
  useFundingSources,
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
  if (Array.isArray(r.cards)) return r.cards;
  if (r.data != null) return unwrapItems(r.data);
  return [];
}

function formatMoney(n: number, currency = "USD") {
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(n);
  } catch {
    return `${currency} ${n.toFixed(2)}`;
  }
}

function statusBadge(status: string) {
  const s = status.toUpperCase();
  if (s === "ACTIVE" || s === "VERIFIED") return "bg-green-100 text-green-800";
  if (s === "ISSUED") return "bg-impilo-100 text-impilo-700";
  if (s === "BLOCKED") return "bg-red-100 text-red-800";
  if (s === "EXPIRED" || s === "REPLACED") return "bg-gray-100 text-gray-600";
  return "bg-gray-100 text-gray-700";
}

function channelBadge(channel: string) {
  const ch = channel.toUpperCase();
  if (ch === "MOBILE_MONEY") return "bg-yellow-100 text-yellow-800";
  if (ch === "CARD" || ch === "POS") return "bg-purple-100 text-purple-800";
  if (ch === "BANK_TRANSFER") return "bg-impilo-100 text-impilo-700";
  if (ch === "CASH") return "bg-emerald-100 text-emerald-800";
  return "bg-gray-100 text-gray-700";
}

export default function WalletDashboardPage() {
  const cpid = useAuthStore((s) => s.user?.id) ?? "";

  const walletQ = useWalletByOwner(cpid ? "PERSON" : null, cpid || null);
  const walletData = useMemo(() => {
    const raw = asRecord(walletQ.data).data ?? walletQ.data;
    return asRecord(raw);
  }, [walletQ.data]);
  const walletId = readStr(walletData, "walletId", "wallet_id", "id");
  const currency = readStr(walletData, "currency") || "USD";

  const balanceQ = useBalance(walletId || null);
  const balanceData = useMemo(() => {
    const raw = asRecord(balanceQ.data).data ?? balanceQ.data;
    return asRecord(raw);
  }, [balanceQ.data]);

  const txQ = useTransactions(walletId || null, 0, 10);
  const txRows = useMemo(() => {
    const raw = asRecord(txQ.data).data ?? txQ.data;
    return unwrapItems(raw).map(asRecord);
  }, [txQ.data]);

  const cardsQ = useCards(walletId || null);
  const cards = useMemo(() => {
    const raw = asRecord(cardsQ.data).data ?? cardsQ.data;
    return unwrapItems(raw).map(asRecord);
  }, [cardsQ.data]);

  const fundingQ = useFundingSources(walletId || null);
  const fundingSources = useMemo(() => {
    const raw = asRecord(fundingQ.data).data ?? fundingQ.data;
    if (!raw) return [];
    if (Array.isArray(raw)) return raw.map(asRecord);
    return unwrapItems(raw).map(asRecord);
  }, [fundingQ.data]);

  const availableBalance = readNum(balanceData, "availableBalance", "available_balance", "balance");
  const heldBalance = readNum(walletData, "heldBalance", "held_balance");
  const totalBalance = availableBalance + heldBalance;

  return (
    <AppLayout>
      <PageShell
        title="My Wallet"
        subtitle="Mushe digital wallet — balances, transactions, cards, and funding sources"
        icon={<Wallet className="h-6 w-6" />}
      >
        {!cpid && (
          <p className="text-sm text-amber-800 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2 mb-6">
            Sign in to view your wallet.
          </p>
        )}

        {cpid && (
          <div className="space-y-8">
            {/* Balance Card */}
            <section className="rounded-xl border border-gray-200 bg-gradient-to-br from-slate-900 to-slate-800 text-white shadow-lg p-6">
              <div className="flex items-center gap-2 mb-4">
                <Wallet className="h-5 w-5 text-emerald-400" />
                <h2 className="text-sm font-semibold text-slate-300">Wallet Balance</h2>
              </div>
              {(walletQ.isLoading || balanceQ.isLoading) && (
                <div className="flex items-center gap-2 text-slate-400 text-sm">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading...
                </div>
              )}
              {walletQ.isError && !walletQ.isLoading && (
                <p className="text-sm text-red-300">Could not load wallet. You may not have a wallet yet.</p>
              )}
              {!walletQ.isLoading && !walletQ.isError && walletId && (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <div className="text-xs text-slate-400">Available</div>
                    <div className="text-2xl font-bold text-emerald-400 tabular-nums">
                      {formatMoney(availableBalance, currency)}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs text-slate-400">On hold</div>
                    <div className="text-lg font-semibold text-amber-300 tabular-nums">
                      {formatMoney(heldBalance, currency)}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs text-slate-400">Total</div>
                    <div className="text-lg font-semibold text-white tabular-nums">
                      {formatMoney(totalBalance, currency)}
                    </div>
                  </div>
                </div>
              )}
              {!walletQ.isLoading && !walletQ.isError && !walletId && (
                <p className="text-sm text-slate-400">No wallet found for your account.</p>
              )}
            </section>

            {/* Quick Actions */}
            <section>
              <h2 className="text-sm font-semibold text-gray-900 mb-3">Quick actions</h2>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <Link
                  href="/wallet/send"
                  className="flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm font-medium text-gray-800 hover:bg-gray-50 shadow-sm transition"
                >
                  <ArrowUpRight className="h-4 w-4 text-impilo-500" />
                  Send Money
                </Link>
                <Link
                  href="/wallet/send?mode=merchant"
                  className="flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm font-medium text-gray-800 hover:bg-gray-50 shadow-sm transition"
                >
                  <CreditCard className="h-4 w-4 text-purple-600" />
                  Pay Merchant
                </Link>
                <Link
                  href="/wallet/deposit"
                  className="flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm font-medium text-gray-800 hover:bg-gray-50 shadow-sm transition"
                >
                  <ArrowDownLeft className="h-4 w-4 text-emerald-600" />
                  Deposit
                </Link>
                <Link
                  href="/wallet/cards"
                  className="flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm font-medium text-gray-800 hover:bg-gray-50 shadow-sm transition"
                >
                  <Plus className="h-4 w-4 text-amber-600" />
                  Request Card
                </Link>
              </div>
            </section>

            {/* Recent Transactions */}
            <section className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
              <div className="border-b border-gray-100 px-4 py-3 bg-slate-50/80 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <ArrowUpRight className="h-4 w-4 text-slate-600" />
                  <h2 className="text-sm font-semibold text-gray-900">Recent transactions</h2>
                </div>
                <Link
                  href="/wallet/transactions"
                  className="text-xs font-medium text-impilo-600 hover:underline"
                >
                  View all
                </Link>
              </div>
              <div className="overflow-x-auto">
                {txQ.isLoading && (
                  <div className="p-6 flex items-center gap-2 text-gray-600 text-sm">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading...
                  </div>
                )}
                {!txQ.isLoading && txQ.isError && (
                  <p className="p-4 text-sm text-red-700">Could not load transactions.</p>
                )}
                {!txQ.isLoading && !txQ.isError && (
                  <table className="min-w-full text-sm">
                    <thead className="bg-gray-50 text-left text-xs text-gray-500 uppercase tracking-wide">
                      <tr>
                        <th className="px-4 py-2">Date</th>
                        <th className="px-4 py-2">Description</th>
                        <th className="px-4 py-2 text-right">Amount</th>
                        <th className="px-4 py-2 text-right">Balance after</th>
                        <th className="px-4 py-2">Channel</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {txRows.length === 0 && (
                        <tr>
                          <td colSpan={5} className="px-4 py-6 text-center text-gray-500">
                            No transactions yet.
                          </td>
                        </tr>
                      )}
                      {txRows.map((row, i) => {
                        const direction = readStr(row, "direction", "txnType", "txn_type");
                        const isCredit = direction.toUpperCase().includes("CREDIT") || direction.toUpperCase().includes("DEPOSIT");
                        const amount = readNum(row, "amount");
                        const balAfter = readNum(row, "balanceAfter", "balance_after", "runningBalance", "running_balance");
                        const channel = readStr(row, "channel");
                        return (
                          <tr key={readStr(row, "txnId", "txn_id", "transactionId", "transaction_id") || String(i)} className="hover:bg-gray-50/80">
                            <td className="px-4 py-2 whitespace-nowrap text-gray-700">
                              {readStr(row, "createdAt", "created_at", "txnDate", "txn_date").slice(0, 16).replace("T", " ")}
                            </td>
                            <td className="px-4 py-2 text-gray-800 max-w-xs truncate">
                              {readStr(row, "description") || readStr(row, "txnType", "txn_type")}
                            </td>
                            <td className={`px-4 py-2 text-right tabular-nums font-medium ${isCredit ? "text-emerald-700" : "text-red-700"}`}>
                              {isCredit ? "+" : "-"}{formatMoney(amount, currency)}
                            </td>
                            <td className="px-4 py-2 text-right tabular-nums text-gray-900">
                              {formatMoney(balAfter, currency)}
                            </td>
                            <td className="px-4 py-2">
                              {channel && (
                                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${channelBadge(channel)}`}>
                                  {channel}
                                </span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            </section>

            {/* My Cards */}
            <section className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
              <div className="border-b border-gray-100 px-4 py-3 bg-slate-50/80 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <CreditCard className="h-4 w-4 text-slate-600" />
                  <h2 className="text-sm font-semibold text-gray-900">My Cards</h2>
                </div>
                <Link
                  href="/wallet/cards"
                  className="text-xs font-medium text-impilo-600 hover:underline"
                >
                  Manage cards
                </Link>
              </div>
              <div className="p-4">
                {cardsQ.isLoading && (
                  <div className="flex items-center gap-2 text-gray-600 text-sm">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading...
                  </div>
                )}
                {!cardsQ.isLoading && cards.length === 0 && (
                  <p className="text-sm text-gray-500">No cards issued yet.</p>
                )}
                {!cardsQ.isLoading && cards.length > 0 && (
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                    {cards.map((card, i) => {
                      const cardId = readStr(card, "cardId", "card_id");
                      const status = readStr(card, "status") || "UNKNOWN";
                      const masked = readStr(card, "maskedCardNumber", "masked_card_number");
                      const expMonth = readNum(card, "expiryMonth", "expiry_month");
                      const expYear = readNum(card, "expiryYear", "expiry_year");
                      const healthUpdated = readStr(card, "healthDataUpdatedAt", "health_data_updated_at");
                      return (
                        <div
                          key={cardId || String(i)}
                          className="rounded-lg border border-gray-100 bg-gray-50 p-3 flex flex-col gap-2"
                        >
                          <div className="flex items-center justify-between">
                            <span className="font-mono text-sm text-gray-900">{masked || "****"}</span>
                            <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusBadge(status)}`}>
                              {status}
                            </span>
                          </div>
                          <div className="text-xs text-gray-500">
                            Expires: {expMonth ? `${String(expMonth).padStart(2, "0")}/${expYear}` : "--/--"}
                          </div>
                          <div className="text-xs text-gray-500">
                            Health data: {healthUpdated ? new Date(healthUpdated).toLocaleDateString() : "Not synced"}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </section>

            {/* Funding Sources */}
            <section className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
              <div className="border-b border-gray-100 px-4 py-3 bg-slate-50/80 flex items-center gap-2">
                <ArrowDownLeft className="h-4 w-4 text-slate-600" />
                <h2 className="text-sm font-semibold text-gray-900">Funding Sources</h2>
              </div>
              <div className="p-4">
                {fundingQ.isLoading && (
                  <div className="flex items-center gap-2 text-gray-600 text-sm">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading...
                  </div>
                )}
                {!fundingQ.isLoading && fundingSources.length === 0 && (
                  <p className="text-sm text-gray-500">No linked funding sources.</p>
                )}
                {!fundingQ.isLoading && fundingSources.length > 0 && (
                  <div className="space-y-2">
                    {fundingSources.map((src, i) => {
                      const srcId = readStr(src, "sourceId", "source_id", "id");
                      const srcType = readStr(src, "sourceType", "source_type");
                      const provider = readStr(src, "provider");
                      const accName = readStr(src, "accountName", "account_name");
                      const verified = src.verified === true || src.status === "VERIFIED";
                      return (
                        <div
                          key={srcId || String(i)}
                          className="flex items-center justify-between rounded-lg border border-gray-100 bg-gray-50 px-3 py-2"
                        >
                          <div>
                            <div className="text-sm font-medium text-gray-900">{accName || provider || srcType}</div>
                            <div className="text-xs text-gray-500">{srcType} {provider ? `- ${provider}` : ""}</div>
                          </div>
                          {verified && (
                            <span className="rounded-full bg-green-100 text-green-800 px-2 py-0.5 text-xs font-medium">
                              Verified
                            </span>
                          )}
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
