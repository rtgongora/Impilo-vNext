"use client";

/**
 * Send Money / Pay Merchant — wallet transfer and merchant payment page.
 * Route: /wallet/send
 *
 * Supports two modes: transfer to another wallet, or pay a merchant by provider number.
 * Mode selected via ?mode=merchant query param or toggle.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import {
  ArrowLeft,
  ArrowUpRight,
  CheckCircle2,
  CreditCard,
  Loader2,
  Wallet,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useWalletByOwner,
  useTransfer,
  usePayMerchant,
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

type Mode = "transfer" | "merchant";

const CHANNELS = ["MOBILE_MONEY", "BANK_TRANSFER", "CARD", "CASH", "POS", "USSD"] as const;

export default function SendMoneyPage() {
  const searchParams = useSearchParams();
  const initialMode = searchParams.get("mode") === "merchant" ? "merchant" : "transfer";

  const cpid = useAuthStore((s) => s.user?.id) ?? "";
  const [mode, setMode] = useState<Mode>(initialMode);
  const [success, setSuccess] = useState<Record<string, unknown> | null>(null);

  // Transfer form
  const [recipientWalletId, setRecipientWalletId] = useState("");
  const [transferAmount, setTransferAmount] = useState("");
  const [transferDesc, setTransferDesc] = useState("");
  const [transferChannel, setTransferChannel] = useState("MOBILE_MONEY");

  // Merchant form
  const [merchantProvider, setMerchantProvider] = useState("");
  const [merchantAmount, setMerchantAmount] = useState("");
  const [merchantRef, setMerchantRef] = useState("");
  const [merchantChannel, setMerchantChannel] = useState("CARD");

  const walletQ = useWalletByOwner(cpid ? "PERSON" : null, cpid || null);
  const walletData = useMemo(() => {
    const raw = asRecord(walletQ.data).data ?? walletQ.data;
    return asRecord(raw);
  }, [walletQ.data]);
  const walletId = readStr(walletData, "walletId", "wallet_id", "id");

  const transfer = useTransfer();
  const payMerchant = usePayMerchant();

  const submitTransfer = () => {
    if (!walletId || !recipientWalletId.trim() || !transferAmount.trim()) return;
    const amount = Number(transferAmount);
    if (Number.isNaN(amount) || amount <= 0) return;

    transfer.mutate(
      {
        fromWalletId: walletId,
        toWalletId: recipientWalletId.trim(),
        amount,
        description: transferDesc.trim() || undefined,
        channel: transferChannel,
      },
      {
        onSuccess: (data) => {
          const raw = asRecord(data).data ?? data;
          setSuccess(asRecord(raw));
          setRecipientWalletId("");
          setTransferAmount("");
          setTransferDesc("");
        },
      },
    );
  };

  const submitMerchantPayment = () => {
    if (!walletId || !merchantProvider.trim() || !merchantAmount.trim()) return;
    const amount = Number(merchantAmount);
    if (Number.isNaN(amount) || amount <= 0) return;

    payMerchant.mutate(
      {
        fromWalletId: walletId,
        merchantProviderNumber: merchantProvider.trim(),
        amount,
        reference: merchantRef.trim() || undefined,
        channel: merchantChannel,
      },
      {
        onSuccess: (data) => {
          const raw = asRecord(data).data ?? data;
          setSuccess(asRecord(raw));
          setMerchantProvider("");
          setMerchantAmount("");
          setMerchantRef("");
        },
      },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Send Money"
        subtitle="Transfer funds or pay a merchant from your MusheX wallet"
        icon={<ArrowUpRight className="h-6 w-6" />}
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
            Sign in to send money.
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
            {/* Success confirmation */}
            {success && (
              <div className="rounded-xl border border-success/25 bg-success-soft p-4">
                <div className="flex items-center gap-2 mb-2">
                  <CheckCircle2 className="h-5 w-5 text-primary" />
                  <h3 className="text-sm font-semibold text-primary-hover">Transaction successful</h3>
                </div>
                <div className="text-xs text-primary-hover space-y-1">
                  <p>Transaction ID: {readStr(success, "txnId", "txn_id", "transactionId", "transaction_id") || "N/A"}</p>
                  <p>Amount: {readStr(success, "amount") || "N/A"}</p>
                </div>
                <button
                  type="button"
                  onClick={() => setSuccess(null)}
                  className="mt-2 text-xs font-medium text-primary-hover hover:underline"
                >
                  Send another
                </button>
              </div>
            )}

            {!success && (
              <>
                {/* Mode toggle */}
                <div className="flex gap-1 border-b border-border">
                  <button
                    type="button"
                    onClick={() => setMode("transfer")}
                    className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                      mode === "transfer"
                        ? "border-impilo-500 text-primary"
                        : "border-transparent text-muted-foreground hover:text-foreground"
                    }`}
                  >
                    <Wallet className="h-4 w-4" /> Transfer
                  </button>
                  <button
                    type="button"
                    onClick={() => setMode("merchant")}
                    className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                      mode === "merchant"
                        ? "border-impilo-500 text-primary"
                        : "border-transparent text-muted-foreground hover:text-foreground"
                    }`}
                  >
                    <CreditCard className="h-4 w-4" /> Pay Merchant
                  </button>
                </div>

                {mode === "transfer" && (
                  <div className="rounded-xl border border-border bg-card p-5 space-y-4">
                    <h3 className="text-sm font-semibold text-foreground">Transfer to wallet</h3>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Recipient wallet ID or CPID
                      </label>
                      <input
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="Wallet ID or CPID of recipient"
                        value={recipientWalletId}
                        onChange={(e) => setRecipientWalletId(e.target.value)}
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
                        value={transferAmount}
                        onChange={(e) => setTransferAmount(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">Description</label>
                      <input
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="What is this payment for?"
                        value={transferDesc}
                        onChange={(e) => setTransferDesc(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">Channel</label>
                      <select
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        value={transferChannel}
                        onChange={(e) => setTransferChannel(e.target.value)}
                      >
                        {CHANNELS.map((ch) => (
                          <option key={ch} value={ch}>{ch.replace(/_/g, " ")}</option>
                        ))}
                      </select>
                    </div>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={submitTransfer}
                        disabled={transfer.isPending || !recipientWalletId.trim() || !transferAmount.trim()}
                        className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
                      >
                        {transfer.isPending && <Loader2 className="h-4 w-4 animate-spin inline mr-1" />}
                        Send
                      </button>
                    </div>
                    {transfer.isError && (
                      <p className="text-xs text-red-600">Transfer failed. Check the recipient and amount.</p>
                    )}
                  </div>
                )}

                {mode === "merchant" && (
                  <div className="rounded-xl border border-border bg-card p-5 space-y-4">
                    <h3 className="text-sm font-semibold text-foreground">Pay Merchant</h3>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Merchant provider number
                      </label>
                      <input
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="Provider number of the merchant"
                        value={merchantProvider}
                        onChange={(e) => setMerchantProvider(e.target.value)}
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
                        value={merchantAmount}
                        onChange={(e) => setMerchantAmount(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">Reference</label>
                      <input
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        placeholder="Invoice or reference number"
                        value={merchantRef}
                        onChange={(e) => setMerchantRef(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">Channel</label>
                      <select
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        value={merchantChannel}
                        onChange={(e) => setMerchantChannel(e.target.value)}
                      >
                        {CHANNELS.map((ch) => (
                          <option key={ch} value={ch}>{ch.replace(/_/g, " ")}</option>
                        ))}
                      </select>
                    </div>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={submitMerchantPayment}
                        disabled={payMerchant.isPending || !merchantProvider.trim() || !merchantAmount.trim()}
                        className="rounded-lg bg-purple-600 px-4 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-50"
                      >
                        {payMerchant.isPending && <Loader2 className="h-4 w-4 animate-spin inline mr-1" />}
                        Pay
                      </button>
                    </div>
                    {payMerchant.isError && (
                      <p className="text-xs text-red-600">Payment failed. Check the provider number and amount.</p>
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
