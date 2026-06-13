"use client";

/**
 * Custodial wallet detail — read-only drill-down on MusheX platform admin.
 * Route: /finance/mushex-platform/wallets/[walletId] | pageTitle: "Custodial Wallet"
 *
 * Phase 4 of the MusheX/COSTA roadmap — surfaces per-record detail for an
 * individual custodial wallet without introducing any write action. Backed by
 * the already-existing BFF passthroughs:
 *
 *   GET /internal/v1/finance/mushex-platform/wallets/{walletId}/transactions
 *   GET /internal/v1/finance/mushex-platform/card-profiles?walletId={walletId}
 *
 * Doctrine: {@link ../../../../../../docs/doctrine/mushex-gateway-neutrality.md}
 * Phase 4 design: {@link ../../../../../../docs/design/phase-4-mushex-platform-detail.md}
 */

import Link from "next/link";
import { useParams } from "next/navigation";
import { AlertCircle, ArrowLeft, CreditCard, Loader2, Shield, Wallet } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  extractRows,
  useMushexPlatformCardProfilesForWallet,
  useMushexPlatformWalletTransactions,
} from "@/hooks/queries/useMushexPlatformAdmin";

function asString(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return "—";
  }
}

export default function CustodialWalletDetailPage() {
  const params = useParams();
  const walletId = typeof params.walletId === "string" ? params.walletId : "";

  const txnsQ = useMushexPlatformWalletTransactions(walletId);
  const cardsQ = useMushexPlatformCardProfilesForWallet(walletId);

  const txnRows = extractRows(txnsQ.data);
  const cardRows = extractRows(cardsQ.data);

  return (
    <AppLayout>
      <PageShell
        title="Custodial wallet"
        subtitle={
          walletId
            ? `Read-only detail for wallet ${walletId}`
            : "Read-only detail for a single custodial wallet"
        }
      >
        <div className="mb-4">
          <Link
            href="/finance/mushex-platform"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to MusheX platform
          </Link>
        </div>

        <div className="space-y-6">
          {/* Identity card */}
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-100 text-primary-hover">
                <Wallet className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <h2 className="text-sm font-semibold text-foreground">Wallet identity</h2>
                <dl className="mt-3 grid gap-2 text-xs sm:grid-cols-2">
                  <div>
                    <dt className="text-muted-foreground">Wallet ID</dt>
                    <dd className="font-mono text-[11px] text-foreground">{walletId || "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Source</dt>
                    <dd className="text-foreground">
                      <code className="text-[10px]">
                        /internal/v1/finance/mushex-platform/wallets/{walletId || "{walletId}"}
                      </code>
                    </dd>
                  </div>
                </dl>
                <p className="mt-3 inline-flex items-center gap-2 rounded-md border border-border bg-background px-2 py-1 text-[11px] text-muted-foreground">
                  <Shield className="h-3.5 w-3.5" />
                  Read-only view — no credit / debit / block / reverse actions are surfaced on this
                  page.
                </p>
              </div>
            </div>
          </section>

          {/* Transactions table */}
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-emerald-100 text-primary-hover">
                <Wallet className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <h2 className="text-sm font-semibold text-foreground">Transaction history</h2>
                <p className="mt-1 text-xs text-muted-foreground">
                  All transactions recorded against this wallet by the MusheX platform admin API.
                </p>

                {txnsQ.isLoading && (
                  <p className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
                    <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading transactions…
                  </p>
                )}
                {txnsQ.isError && (
                  <p className="mt-3 flex items-center gap-2 text-xs text-danger">
                    <AlertCircle className="h-3.5 w-3.5" /> Could not load transactions for this
                    wallet from MusheX.
                  </p>
                )}
                {!txnsQ.isLoading && !txnsQ.isError && txnRows.length === 0 && (
                  <p className="mt-3 text-xs text-muted-foreground">
                    No transactions returned for this wallet.
                  </p>
                )}
                {!txnsQ.isLoading && !txnsQ.isError && txnRows.length > 0 && (
                  <div className="mt-3 overflow-x-auto rounded-lg border border-border">
                    <table className="w-full text-xs">
                      <thead className="bg-background text-muted-foreground">
                        <tr>
                          <th className="px-3 py-2 text-left font-medium">ID</th>
                          <th className="px-3 py-2 text-left font-medium">Type</th>
                          <th className="px-3 py-2 text-right font-medium">Amount</th>
                          <th className="px-3 py-2 text-left font-medium">Reference</th>
                          <th className="px-3 py-2 text-left font-medium">Timestamp</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {txnRows.map((row, idx) => (
                          <tr key={asString(row.id) + idx} className="bg-card">
                            <td className="px-3 py-2 font-mono text-[10px] text-foreground">
                              {asString(row.id)}
                            </td>
                            <td className="px-3 py-2 text-foreground">
                              {asString(row.transactionType ?? row.type)}
                            </td>
                            <td className="px-3 py-2 text-right text-foreground">
                              {asString(row.amount)}
                            </td>
                            <td className="px-3 py-2 text-muted-foreground">
                              {asString(row.referenceCode ?? row.reference)}
                            </td>
                            <td className="px-3 py-2 text-muted-foreground">
                              {asString(row.createdAt ?? row.timestamp ?? row.recordedAt)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                <p className="mt-2 text-[11px] text-muted-foreground">
                  BFF route:{" "}
                  <code className="text-[10px]">
                    /internal/v1/finance/mushex-platform/wallets/{walletId || "{walletId}"}/transactions
                  </code>
                </p>
              </div>
            </div>
          </section>

          {/* Linked card profiles */}
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-warning-foreground">
                <CreditCard className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <h2 className="text-sm font-semibold text-foreground">Linked card profiles</h2>
                <p className="mt-1 text-xs text-muted-foreground">
                  Tokenised card profiles registered against this wallet. Card lifecycle actions
                  (issue, suspend, retire) are backend-only in this stage.
                </p>

                {cardsQ.isLoading && (
                  <p className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
                    <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading card profiles…
                  </p>
                )}
                {cardsQ.isError && (
                  <p className="mt-3 flex items-center gap-2 text-xs text-danger">
                    <AlertCircle className="h-3.5 w-3.5" /> Could not load card profiles for this
                    wallet from MusheX.
                  </p>
                )}
                {!cardsQ.isLoading && !cardsQ.isError && cardRows.length === 0 && (
                  <p className="mt-3 text-xs text-muted-foreground">
                    No card profiles linked to this wallet.
                  </p>
                )}
                {!cardsQ.isLoading && !cardsQ.isError && cardRows.length > 0 && (
                  <div className="mt-3 overflow-x-auto rounded-lg border border-border">
                    <table className="w-full text-xs">
                      <thead className="bg-background text-muted-foreground">
                        <tr>
                          <th className="px-3 py-2 text-left font-medium">ID</th>
                          <th className="px-3 py-2 text-left font-medium">Brand</th>
                          <th className="px-3 py-2 text-left font-medium">Masked PAN</th>
                          <th className="px-3 py-2 text-left font-medium">Status</th>
                          <th className="px-3 py-2 text-left font-medium">Created</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {cardRows.map((row, idx) => (
                          <tr key={asString(row.id) + idx} className="bg-card">
                            <td className="px-3 py-2 font-mono text-[10px] text-foreground">
                              {asString(row.id)}
                            </td>
                            <td className="px-3 py-2 text-foreground">
                              {asString(row.brand ?? row.network)}
                            </td>
                            <td className="px-3 py-2 font-mono text-[10px] text-foreground">
                              {asString(row.maskedPan ?? row.lastFour)}
                            </td>
                            <td className="px-3 py-2 text-foreground">{asString(row.status)}</td>
                            <td className="px-3 py-2 text-muted-foreground">
                              {asString(row.createdAt ?? row.issuedAt)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                <p className="mt-2 text-[11px] text-muted-foreground">
                  BFF route:{" "}
                  <code className="text-[10px]">
                    /internal/v1/finance/mushex-platform/card-profiles?walletId={walletId || "{walletId}"}
                  </code>
                </p>
              </div>
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
