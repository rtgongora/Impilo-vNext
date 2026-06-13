"use client";

import Link from "next/link";
import { ExternalLink, Loader2, RefreshCw, Wallet } from "lucide-react";
import {
  type CouncilObligationRow,
  useCreateCouncilMusheXIntent,
  useSyncCouncilObligationPayment,
} from "@/hooks/queries/useProviderCouncil";

function obligationId(row: CouncilObligationRow): number | null {
  const raw = row.id;
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string" && raw.trim()) {
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function mushexIntentId(row: CouncilObligationRow): string | null {
  const value = row.mushexIntentId ?? row.mushex_intent_id;
  return value == null || String(value).trim() === "" ? null : String(value);
}

function statusBadgeClass(status: string): string {
  const normalized = status.toUpperCase();
  if (normalized === "SETTLED" || normalized === "PAID") {
    return "bg-emerald-100 text-primary-hover";
  }
  if (normalized === "PENDING_PAYMENT" || normalized === "OPEN") {
    return "bg-amber-100 text-warning-foreground";
  }
  if (normalized === "FAILED" || normalized === "CANCELLED" || normalized === "REFUNDED") {
    return "bg-red-100 text-red-800";
  }
  return "bg-neutral-100 text-foreground";
}

interface CouncilObligationPaymentPanelProps {
  providerId?: string;
  obligations: CouncilObligationRow[];
  isLoading?: boolean;
}

export function CouncilObligationPaymentPanel({
  providerId,
  obligations,
  isLoading = false,
}: CouncilObligationPaymentPanelProps) {
  const createIntent = useCreateCouncilMusheXIntent(providerId);
  const syncPayment = useSyncCouncilObligationPayment(providerId);
  const busy = createIntent.isPending || syncPayment.isPending;

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground" data-testid="council-obligation-payment-panel">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading obligations…
      </div>
    );
  }

  if (obligations.length === 0) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="council-obligation-payment-panel">
        No council fee obligations for this provider.
      </p>
    );
  }

  return (
    <div className="space-y-3" data-testid="council-obligation-payment-panel">
      {obligations.map((row) => {
        const id = obligationId(row);
        const intentId = mushexIntentId(row);
        const status = String(row.status ?? "OPEN");
        const amount = row.amount != null ? String(row.amount) : "—";
        const currency = String(row.currency ?? "USD");

        return (
          <div
            key={String(id ?? row.obligationType)}
            className="rounded-lg border border-border bg-background/80 p-3"
            data-testid={id ? `council-obligation-row-${id}` : undefined}
          >
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-sm font-medium text-foreground">
                  {String(row.obligationType ?? "Council fee")} · {amount} {currency}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Obligation #{id ?? "—"}
                  {row.dueDate ? ` · due ${row.dueDate}` : ""}
                </p>
                <span
                  className={`mt-2 inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${statusBadgeClass(status)}`}
                >
                  {status}
                </span>
                {intentId ? (
                  <p className="mt-2 text-xs text-violet-700">
                    MusheX intent <code className="rounded bg-card px-1">{intentId}</code>
                  </p>
                ) : null}
              </div>

              <div className="flex flex-wrap gap-2">
                {id && !intentId ? (
                  <button
                    type="button"
                    disabled={busy}
                    data-testid={`council-create-intent-${id}`}
                    onClick={() => createIntent.mutate(id)}
                    className="inline-flex items-center gap-1 rounded-lg bg-violet-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-50"
                  >
                    {createIntent.isPending ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <Wallet className="h-3.5 w-3.5" />
                    )}
                    Create MusheX intent
                  </button>
                ) : null}
                {id && intentId ? (
                  <>
                    <button
                      type="button"
                      disabled={busy}
                      data-testid={`council-sync-payment-${id}`}
                      onClick={() => syncPayment.mutate(id)}
                      className="inline-flex items-center gap-1 rounded-lg border border-border bg-card px-2.5 py-1 text-xs font-medium text-foreground hover:bg-background disabled:opacity-50"
                    >
                      {syncPayment.isPending ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <RefreshCw className="h-3.5 w-3.5" />
                      )}
                      Sync payment
                    </button>
                    <Link
                      href={`/wallet?intentId=${encodeURIComponent(intentId)}`}
                      data-testid={`council-open-wallet-${id}`}
                      className="inline-flex items-center gap-1 rounded-lg border border-success/25 bg-success-soft px-2.5 py-1 text-xs font-medium text-primary-hover hover:bg-emerald-100"
                    >
                      <ExternalLink className="h-3.5 w-3.5" />
                      Open MusheX wallet
                    </Link>
                  </>
                ) : null}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
