"use client";

import Link from "next/link";
import { Loader2, Layers } from "lucide-react";
import { extractCount, useMushexPlatformWallets, type MushexPlatformPayload } from "@/hooks/queries/useMushexPlatformAdmin";

function extractWalletRows(payload: MushexPlatformPayload): Array<Record<string, unknown>> {
  if (payload == null) return [];
  if (Array.isArray(payload)) return payload.filter((row): row is Record<string, unknown> => typeof row === "object" && row !== null);
  if (typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    for (const key of ["data", "items", "content"]) {
      const value = obj[key];
      if (Array.isArray(value)) {
        return value.filter((row): row is Record<string, unknown> => typeof row === "object" && row !== null);
      }
    }
  }
  return [];
}

function readWalletField(row: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (value != null && String(value).trim()) return String(value);
  }
  return "";
}

function isProgrammeAttributionWallet(row: Record<string, unknown>): boolean {
  const ownerType = readWalletField(row, "owner_type", "ownerType").toUpperCase();
  return ownerType.includes("PROGRAMME") || ownerType.includes("SPONSOR") || ownerType.includes("PAYER");
}

export function MusheXProgrammeAttributionPanel() {
  const walletsQ = useMushexPlatformWallets();
  const rows = extractWalletRows(walletsQ.data);
  const programmeWallets = rows.filter(isProgrammeAttributionWallet);
  const totalCount = extractCount(walletsQ.data);

  return (
    <section
      className="rounded-xl border border-violet-200 bg-violet-50/60 p-5 shadow-sm"
      data-testid="mushex-programme-attribution-panel"
    >
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-violet-100 text-violet-700">
          <Layers className="h-5 w-5" />
        </div>
        <div className="flex-1 min-w-0">
          <h2 className="text-sm font-semibold text-slate-900">Programme attribution</h2>
          <p className="mt-1 text-xs text-slate-600">
            Custodial wallets with programme, sponsor, or payer owner types from{" "}
            <code className="text-[10px]">GET /internal/v1/finance/mushex-platform/wallets</code>
            — used to trace subsidy and remittance attribution without fabricating balances.
          </p>

          {walletsQ.isLoading ? (
            <p className="mt-3 flex items-center gap-2 text-xs text-slate-500">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading programme-attributed wallets…
            </p>
          ) : walletsQ.isError ? (
            <p className="mt-3 text-xs text-red-700">Could not load MusheX wallet list from the BFF.</p>
          ) : programmeWallets.length === 0 ? (
            <p className="mt-3 text-xs text-slate-600">
              No programme/sponsor/payer custodial wallets returned
              {totalCount != null ? ` (${totalCount} total wallet row(s) in envelope).` : "."}
            </p>
          ) : (
            <>
              <p className="mt-3 text-xs text-slate-700">
                <span className="font-medium">{programmeWallets.length}</span> programme-attributed wallet
                {programmeWallets.length === 1 ? "" : "s"}
                {totalCount != null ? ` of ${totalCount} total` : ""}.
              </p>
              <div className="mt-3 overflow-x-auto rounded-lg border border-violet-100 bg-white">
                <table className="w-full text-xs">
                  <thead className="bg-violet-50/80 text-left text-slate-600">
                    <tr>
                      <th className="px-3 py-2 font-medium">Owner type</th>
                      <th className="px-3 py-2 font-medium">Owner ref</th>
                      <th className="px-3 py-2 font-medium">Wallet</th>
                      <th className="px-3 py-2 font-medium text-right">Balance</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-violet-50">
                    {programmeWallets.slice(0, 8).map((row, idx) => {
                      const walletId = readWalletField(row, "wallet_id", "walletId", "id") || `row-${idx}`;
                      return (
                        <tr key={walletId}>
                          <td className="px-3 py-2 text-slate-800">
                            {readWalletField(row, "owner_type", "ownerType") || "—"}
                          </td>
                          <td className="px-3 py-2 font-mono text-[10px] text-slate-600">
                            {readWalletField(row, "owner_ref", "ownerRef") || "—"}
                          </td>
                          <td className="px-3 py-2">
                            <Link
                              href={`/finance/mushex-platform/wallets/${encodeURIComponent(walletId)}`}
                              className="font-mono text-[10px] text-violet-700 hover:underline"
                            >
                              {walletId}
                            </Link>
                          </td>
                          <td className="px-3 py-2 text-right text-slate-800">
                            {readWalletField(row, "available_balance", "availableBalance", "balance") || "—"}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
