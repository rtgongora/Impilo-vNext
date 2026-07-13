"use client";

/**
 * Deposit Money — fund wallet from linked source or cash at a facility.
 * Route: /wallet/deposit
 *
 * Two tabs: "From Linked Source" and "Cash Deposit".
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  ArrowDownLeft,
  ArrowLeft,
  Banknote,
  CheckCircle2,
  Landmark,
  Loader2,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useWalletByOwner,
  useFundingSources,
  useDeposit,
  useCashDeposit,
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

function unwrapList(v: unknown): unknown[] {
  if (!v) return [];
  if (Array.isArray(v)) return v;
  const r = v as Record<string, unknown>;
  if (Array.isArray(r.items)) return r.items;
  if (Array.isArray(r.data)) return unwrapList(r.data);
  return [];
}

type Tab = "linked" | "cash";

export default function DepositPage() {
  const cpid = useAuthStore((s) => s.user?.id) ?? "";
  const [tab, setTab] = useState<Tab>("linked");
  const [success, setSuccess] = useState<Record<string, unknown> | null>(null);

  // Linked source form
  const [selectedSourceId, setSelectedSourceId] = useState("");
  const [linkedAmount, setLinkedAmount] = useState("");
  const [linkedRef, setLinkedRef] = useState("");

  // Cash deposit form
  const [cashFacilityId, setCashFacilityId] = useState("");
  const [cashCashierId, setCashCashierId] = useState("");
  const [cashAmount, setCashAmount] = useState("");
  const [cashRef, setCashRef] = useState("");

  const walletQ = useWalletByOwner(cpid ? "PERSON" : null, cpid || null);
  const walletData = useMemo(() => {
    const raw = asRecord(walletQ.data).data ?? walletQ.data;
    return asRecord(raw);
  }, [walletQ.data]);
  const walletId = readStr(walletData, "walletId", "wallet_id", "id");

  const fundingQ = useFundingSources(walletId || null);
  const sources = useMemo(() => {
    const raw = asRecord(fundingQ.data).data ?? fundingQ.data;
    return unwrapList(raw).map(asRecord);
  }, [fundingQ.data]);

  const deposit = useDeposit();
  const cashDeposit = useCashDeposit();

  const submitLinkedDeposit = () => {
    if (!walletId || !selectedSourceId || !linkedAmount.trim()) return;
    const amount = Number(linkedAmount);
    if (Number.isNaN(amount) || amount <= 0) return;

    deposit.mutate(
      {
        walletId,
        body: {
          sourceId: selectedSourceId,
          amount,
          reference: linkedRef.trim() || undefined,
        },
      },
      {
        onSuccess: (data) => {
          const raw = asRecord(data).data ?? data;
          setSuccess(asRecord(raw));
          setLinkedAmount("");
          setLinkedRef("");
        },
      },
    );
  };

  const submitCashDeposit = () => {
    if (!walletId || !cashFacilityId.trim() || !cashCashierId.trim() || !cashAmount.trim()) return;
    const amount = Number(cashAmount);
    if (Number.isNaN(amount) || amount <= 0) return;

    cashDeposit.mutate(
      {
        walletId,
        body: {
          facilityId: cashFacilityId.trim(),
          cashierId: cashCashierId.trim(),
          amount,
          reference: cashRef.trim() || undefined,
        },
      },
      {
        onSuccess: (data) => {
          const raw = asRecord(data).data ?? data;
          setSuccess(asRecord(raw));
          setCashAmount("");
          setCashRef("");
        },
      },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Deposit Money"
        subtitle="Add funds to your MusheX wallet from a linked source or cash at a facility"
        icon={<ArrowDownLeft className="h-6 w-6" />}
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
            Sign in to deposit funds.
          </p>
        )}

        {cpid && walletQ.isLoading && (
          <div className="flex items-center gap-2 text-muted-foreground text-sm py-8">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading wallet...
          </div>
        )}

        {cpid && !walletQ.isLoading && !walletId && (
          <p className="text-sm text-danger bg-danger-soft border border-red-100 rounded-lg px-3 py-2">
            No wallet found. Please set up your wallet first.
          </p>
        )}

        {cpid && walletId && (
          <div className="space-y-6 max-w-xl">
            {/* Success */}
            {success && (
              <div className="rounded-xl border border-success/25 bg-success-soft p-4">
                {readStr(success, "status") === "PENDING" ? (
                  <>
                    {/* Honest collection-intent state: the balance rises only when the
                        money's arrival is confirmed — never on request. */}
                    <div className="flex items-center gap-2 mb-2">
                      <Loader2 className="h-5 w-5 text-primary" />
                      <h3 className="text-sm font-semibold text-primary-hover">
                        Deposit requested — awaiting your transfer
                      </h3>
                    </div>
                    <div className="text-xs text-primary-hover space-y-1">
                      <p>
                        Send the money from your linked account and quote this reference
                        so we can match it:
                      </p>
                      <p className="font-mono text-base font-bold tracking-wider">
                        {readStr(success, "referenceCode", "reference_code") || "—"}
                      </p>
                      <p>
                        Your wallet balance will rise as soon as the transfer is confirmed.
                        You can check progress under deposit history.
                      </p>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="flex items-center gap-2 mb-2">
                      <CheckCircle2 className="h-5 w-5 text-primary" />
                      <h3 className="text-sm font-semibold text-primary-hover">Deposit successful</h3>
                    </div>
                    <div className="text-xs text-primary-hover space-y-1">
                      <p>Transaction ID: {readStr(success, "txnId", "txn_id", "transactionId", "transaction_id") || "N/A"}</p>
                    </div>
                  </>
                )}
                <button
                  type="button"
                  onClick={() => setSuccess(null)}
                  className="mt-2 text-xs font-medium text-primary-hover hover:underline"
                >
                  Make another deposit
                </button>
              </div>
            )}

            {!success && (
              <>
                {/* Tab toggle */}
                <div className="flex gap-1 border-b border-border">
                  <button
                    type="button"
                    onClick={() => setTab("linked")}
                    className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                      tab === "linked"
                        ? "border-emerald-600 text-primary"
                        : "border-transparent text-muted-foreground hover:text-foreground"
                    }`}
                  >
                    <Landmark className="h-4 w-4" /> From Linked Source
                  </button>
                  <button
                    type="button"
                    onClick={() => setTab("cash")}
                    className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                      tab === "cash"
                        ? "border-emerald-600 text-primary"
                        : "border-transparent text-muted-foreground hover:text-foreground"
                    }`}
                  >
                    <Banknote className="h-4 w-4" /> Cash Deposit
                  </button>
                </div>

                {tab === "linked" && (
                  <div className="rounded-xl border border-border bg-card p-5 space-y-4">
                    <h3 className="text-sm font-semibold text-foreground">Deposit from linked source</h3>

                    {fundingQ.isLoading && (
                      <div className="flex items-center gap-2 text-muted-foreground text-sm">
                        <Loader2 className="h-4 w-4 animate-spin" /> Loading sources...
                      </div>
                    )}

                    {!fundingQ.isLoading && sources.length === 0 && (
                      <p className="text-sm text-muted-foreground">
                        No linked funding sources. Add a bank account or mobile money wallet first.
                      </p>
                    )}

                    {!fundingQ.isLoading && sources.length > 0 && (
                      <>
                        <div>
                          <label className="block text-xs font-medium text-muted-foreground mb-1">Source</label>
                          <select
                            className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                            value={selectedSourceId}
                            onChange={(e) => setSelectedSourceId(e.target.value)}
                          >
                            <option value="">Select a source</option>
                            {sources.map((src, i) => {
                              const id = readStr(src, "sourceId", "source_id", "id");
                              const name = readStr(src, "accountName", "account_name") ||
                                readStr(src, "provider") ||
                                readStr(src, "sourceType", "source_type");
                              return (
                                <option key={id || String(i)} value={id}>
                                  {name} ({readStr(src, "sourceType", "source_type")})
                                </option>
                              );
                            })}
                          </select>
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-muted-foreground mb-1">Amount</label>
                          <input
                            type="number"
                            min="0.01"
                            step="0.01"
                            className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                            placeholder="0.00"
                            value={linkedAmount}
                            onChange={(e) => setLinkedAmount(e.target.value)}
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-muted-foreground mb-1">Reference (optional)</label>
                          <input
                            className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                            placeholder="Reference number"
                            value={linkedRef}
                            onChange={(e) => setLinkedRef(e.target.value)}
                          />
                        </div>
                        <button
                          type="button"
                          onClick={submitLinkedDeposit}
                          disabled={deposit.isPending || !selectedSourceId || !linkedAmount.trim()}
                          className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                        >
                          {deposit.isPending && <Loader2 className="h-4 w-4 animate-spin inline mr-1" />}
                          Deposit
                        </button>
                        {deposit.isError && (
                          <p className="text-xs text-red-600">Deposit failed. Please try again.</p>
                        )}
                      </>
                    )}
                  </div>
                )}

                {tab === "cash" && (
                  <div className="rounded-xl border border-border bg-card p-5 space-y-4">
                    <h3 className="text-sm font-semibold text-foreground">Cash deposit at facility</h3>
                    <p className="text-xs text-muted-foreground">
                      Deposit cash at a health facility cashier point.
                    </p>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">Facility ID</label>
                      <input
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="Facility identifier"
                        value={cashFacilityId}
                        onChange={(e) => setCashFacilityId(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">Cashier ID</label>
                      <input
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="Cashier staff identifier"
                        value={cashCashierId}
                        onChange={(e) => setCashCashierId(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">Amount</label>
                      <input
                        type="number"
                        min="0.01"
                        step="0.01"
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="0.00"
                        value={cashAmount}
                        onChange={(e) => setCashAmount(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">Reference (optional)</label>
                      <input
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="Receipt or reference number"
                        value={cashRef}
                        onChange={(e) => setCashRef(e.target.value)}
                      />
                    </div>
                    <button
                      type="button"
                      onClick={submitCashDeposit}
                      disabled={cashDeposit.isPending || !cashFacilityId.trim() || !cashCashierId.trim() || !cashAmount.trim()}
                      className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                    >
                      {cashDeposit.isPending && <Loader2 className="h-4 w-4 animate-spin inline mr-1" />}
                      Deposit Cash
                    </button>
                    {cashDeposit.isError && (
                      <p className="text-xs text-red-600">Cash deposit failed. Please try again.</p>
                    )}
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
