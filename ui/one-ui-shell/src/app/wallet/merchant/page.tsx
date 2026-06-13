"use client";

/**
 * Merchant Dashboard — for providers who accept wallet payments.
 * Route: /wallet/merchant
 *
 * Sections: merchant account summary, wallet balance, recent payments,
 * settlement details, and merchant registration form.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  ArrowLeft,
  Briefcase,
  Loader2,
  Plus,
  Store,
  Wallet,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useMerchantByProvider,
  useWallet,
  useBalance,
  useTransactions,
  useRegisterMerchant,
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

function formatMoney(n: number, currency = "USD") {
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(n);
  } catch {
    return `${currency} ${n.toFixed(2)}`;
  }
}

function statusBadge(status: string) {
  const s = status.toUpperCase();
  if (s === "ACTIVE") return "bg-green-100 text-green-800";
  if (s === "SUSPENDED") return "bg-red-100 text-red-800";
  if (s === "PENDING") return "bg-amber-100 text-warning-foreground";
  return "bg-neutral-100 text-foreground";
}

const MERCHANT_TYPES = ["PHARMACY", "CLINIC", "HOSPITAL", "LABORATORY", "OTHER"] as const;

export default function MerchantDashboardPage() {
  const user = useAuthStore((s) => s.user);
  const providerId = user?.providerId ?? "";
  const [showRegisterForm, setShowRegisterForm] = useState(false);

  // Registration form state
  const [regForm, setRegForm] = useState({
    providerNumber: providerId || "",
    facilityId: "",
    merchantName: "",
    merchantType: "PHARMACY",
    settlementAccount: "",
    settlementBank: "",
  });

  const merchantQ = useMerchantByProvider(providerId || null);
  const merchantData = useMemo(() => {
    const raw = asRecord(merchantQ.data).data ?? merchantQ.data;
    return asRecord(raw);
  }, [merchantQ.data]);
  const hasMerchant = Boolean(readStr(merchantData, "merchantId", "merchant_id"));
  const merchantWalletId = readStr(merchantData, "walletId", "wallet_id");
  const merchantStatus = readStr(merchantData, "status") || "UNKNOWN";

  const walletQ = useWallet(merchantWalletId || null);
  const walletData = useMemo(() => {
    const raw = asRecord(walletQ.data).data ?? walletQ.data;
    return asRecord(raw);
  }, [walletQ.data]);
  const currency = readStr(walletData, "currency") || "USD";

  const balanceQ = useBalance(merchantWalletId || null);
  const balanceData = useMemo(() => {
    const raw = asRecord(balanceQ.data).data ?? balanceQ.data;
    return asRecord(raw);
  }, [balanceQ.data]);
  const merchantBalance = readNum(balanceData, "availableBalance", "available_balance", "balance");

  const txQ = useTransactions(merchantWalletId || null, 0, 20);
  const txRows = useMemo(() => {
    const raw = asRecord(txQ.data).data ?? txQ.data;
    return unwrapItems(raw).map(asRecord);
  }, [txQ.data]);

  const registerMerchant = useRegisterMerchant();

  const submitRegistration = () => {
    if (!regForm.providerNumber.trim() || !regForm.facilityId.trim() || !regForm.merchantName.trim()) return;
    const body: Record<string, unknown> = {
      providerNumber: regForm.providerNumber.trim(),
      facilityId: regForm.facilityId.trim(),
      merchantName: regForm.merchantName.trim(),
      merchantType: regForm.merchantType,
    };
    if (regForm.settlementAccount.trim()) body.settlementAccount = regForm.settlementAccount.trim();
    if (regForm.settlementBank.trim()) body.settlementBank = regForm.settlementBank.trim();

    registerMerchant.mutate(body, {
      onSuccess: () => setShowRegisterForm(false),
    });
  };

  return (
    <AppLayout>
      <PageShell
        title="Merchant Dashboard"
        subtitle="Manage your merchant account and view incoming payments"
        icon={<Store className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/wallet"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to wallet
          </Link>
        </div>

        {!providerId && (
          <p className="text-sm text-warning-foreground bg-warning-soft border border-amber-100 rounded-lg px-3 py-2">
            Activate a Provider ID to access the merchant dashboard. Merchant accounts are linked to your provider number.
          </p>
        )}

        {providerId && merchantQ.isLoading && (
          <div className="flex items-center gap-2 text-muted-foreground text-sm py-8">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading merchant account...
          </div>
        )}

        {providerId && !merchantQ.isLoading && !hasMerchant && (
          <div className="space-y-6">
            <div className="rounded-xl border border-warning/35 bg-warning-soft p-4">
              <h3 className="text-sm font-semibold text-warning-foreground mb-1">No merchant account</h3>
              <p className="text-xs text-warning-foreground mb-3">
                You are not registered as a merchant yet. Register to start accepting wallet payments from patients.
              </p>
              <button
                type="button"
                onClick={() => setShowRegisterForm(!showRegisterForm)}
                className="inline-flex items-center gap-1 rounded-lg bg-amber-600 px-3 py-2 text-sm font-medium text-white hover:bg-amber-700"
              >
                <Plus className="h-4 w-4" /> Register as Merchant
              </button>
            </div>

            {showRegisterForm && (
              <div className="rounded-xl border border-border bg-card p-5 space-y-4 max-w-xl">
                <h3 className="text-sm font-semibold text-foreground">Merchant registration</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Provider number *</label>
                    <input
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                      value={regForm.providerNumber}
                      onChange={(e) => setRegForm({ ...regForm, providerNumber: e.target.value })}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Facility ID *</label>
                    <input
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                      placeholder="Facility identifier"
                      value={regForm.facilityId}
                      onChange={(e) => setRegForm({ ...regForm, facilityId: e.target.value })}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Merchant name *</label>
                    <input
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                      placeholder="Business display name"
                      value={regForm.merchantName}
                      onChange={(e) => setRegForm({ ...regForm, merchantName: e.target.value })}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Merchant type</label>
                    <select
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                      value={regForm.merchantType}
                      onChange={(e) => setRegForm({ ...regForm, merchantType: e.target.value })}
                    >
                      {MERCHANT_TYPES.map((t) => (
                        <option key={t} value={t}>{t}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Settlement account</label>
                    <input
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                      placeholder="Bank account number"
                      value={regForm.settlementAccount}
                      onChange={(e) => setRegForm({ ...regForm, settlementAccount: e.target.value })}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Settlement bank</label>
                    <input
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                      placeholder="Bank name"
                      value={regForm.settlementBank}
                      onChange={(e) => setRegForm({ ...regForm, settlementBank: e.target.value })}
                    />
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setShowRegisterForm(false)}
                    className="rounded-lg border border-border bg-card px-4 py-2 text-sm"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={submitRegistration}
                    disabled={registerMerchant.isPending}
                    className="rounded-lg bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700 disabled:opacity-50"
                  >
                    {registerMerchant.isPending && <Loader2 className="h-4 w-4 animate-spin inline mr-1" />}
                    Register
                  </button>
                </div>
                {registerMerchant.isError && (
                  <p className="text-xs text-red-600">Registration failed. Check all required fields.</p>
                )}
              </div>
            )}
          </div>
        )}

        {providerId && !merchantQ.isLoading && hasMerchant && (
          <div className="space-y-8">
            {/* Merchant Summary */}
            <section className="rounded-xl border border-border bg-card shadow-sm p-5">
              <h2 className="text-sm font-semibold text-foreground flex items-center gap-2 mb-4">
                <Briefcase className="h-4 w-4 text-violet-600" />
                Merchant account
              </h2>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                <div className="rounded-lg bg-background p-3">
                  <div className="text-muted-foreground text-xs">Provider number</div>
                  <div className="font-mono font-semibold text-foreground">
                    {readStr(merchantData, "providerNumber", "provider_number")}
                  </div>
                </div>
                <div className="rounded-lg bg-background p-3">
                  <div className="text-muted-foreground text-xs">Merchant name</div>
                  <div className="font-semibold text-foreground">
                    {readStr(merchantData, "merchantName", "merchant_name")}
                  </div>
                </div>
                <div className="rounded-lg bg-background p-3">
                  <div className="text-muted-foreground text-xs">Type</div>
                  <div className="font-semibold text-foreground">
                    {readStr(merchantData, "merchantType", "merchant_type")}
                  </div>
                </div>
                <div className="rounded-lg bg-background p-3">
                  <div className="text-muted-foreground text-xs">Status</div>
                  <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusBadge(merchantStatus)}`}>
                    {merchantStatus}
                  </span>
                </div>
              </div>
            </section>

            {/* Merchant Wallet Balance */}
            <section className="rounded-xl border border-border bg-gradient-to-br from-violet-900 to-violet-800 text-white shadow-lg p-5">
              <div className="flex items-center gap-2 mb-3">
                <Wallet className="h-5 w-5 text-violet-300" />
                <h2 className="text-sm font-semibold text-violet-200">Merchant wallet balance</h2>
              </div>
              {(walletQ.isLoading || balanceQ.isLoading) && (
                <div className="flex items-center gap-2 text-violet-300 text-sm">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading...
                </div>
              )}
              {!walletQ.isLoading && !balanceQ.isLoading && (
                <div className="text-2xl font-bold text-white tabular-nums">
                  {formatMoney(merchantBalance, currency)}
                </div>
              )}
            </section>

            {/* Recent Payments Received */}
            <section className="rounded-xl border border-border bg-card shadow-sm overflow-hidden">
              <div className="border-b border-border px-4 py-3 bg-background/80 flex items-center gap-2">
                <Store className="h-4 w-4 text-muted-foreground" />
                <h2 className="text-sm font-semibold text-foreground">Recent payments received</h2>
              </div>
              <div className="overflow-x-auto">
                {txQ.isLoading && (
                  <div className="p-6 flex items-center gap-2 text-muted-foreground text-sm">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading...
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
                        <th className="px-4 py-2">Counterparty</th>
                        <th className="px-4 py-2">Description</th>
                        <th className="px-4 py-2 text-right">Amount</th>
                        <th className="px-4 py-2">Channel</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {txRows.length === 0 && (
                        <tr>
                          <td colSpan={5} className="px-4 py-6 text-center text-muted-foreground">
                            No payments received yet.
                          </td>
                        </tr>
                      )}
                      {txRows.map((row, i) => (
                        <tr key={readStr(row, "txnId", "txn_id", "transactionId", "transaction_id") || String(i)} className="hover:bg-background/80">
                          <td className="px-4 py-2 whitespace-nowrap text-foreground">
                            {readStr(row, "createdAt", "created_at").slice(0, 16).replace("T", " ")}
                          </td>
                          <td className="px-4 py-2 text-foreground">
                            {readStr(row, "counterpartyName", "counterparty_name") || readStr(row, "counterpartyRef", "counterparty_ref") || "--"}
                          </td>
                          <td className="px-4 py-2 text-foreground max-w-xs truncate">
                            {readStr(row, "description") || readStr(row, "reference")}
                          </td>
                          <td className="px-4 py-2 text-right tabular-nums font-medium text-primary-hover">
                            +{formatMoney(readNum(row, "amount"), currency)}
                          </td>
                          <td className="px-4 py-2">
                            <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-medium text-foreground">
                              {readStr(row, "channel") || "--"}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </section>

            {/* Settlement Account */}
            <section className="rounded-xl border border-border bg-card shadow-sm p-5">
              <h2 className="text-sm font-semibold text-foreground mb-3">Settlement account</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg bg-background p-3">
                  <div className="text-muted-foreground text-xs">Account</div>
                  <div className="font-mono font-medium text-foreground">
                    {readStr(merchantData, "settlementAccount", "settlement_account") || "Not configured"}
                  </div>
                </div>
                <div className="rounded-lg bg-background p-3">
                  <div className="text-muted-foreground text-xs">Bank</div>
                  <div className="font-medium text-foreground">
                    {readStr(merchantData, "settlementBank", "settlement_bank") || "Not configured"}
                  </div>
                </div>
              </div>
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
