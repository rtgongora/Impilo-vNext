"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { AlertCircle, ArrowLeft, Loader2, Send, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  extractRecord,
  useMushexPlatformRemittanceTransferById,
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

export default function RemittanceTransferDetailPage() {
  const params = useParams();
  const transferId = typeof params.transferId === "string" ? params.transferId : "";
  const transferQ = useMushexPlatformRemittanceTransferById(transferId);
  const transfer = extractRecord(transferQ.data);

  return (
    <AppLayout>
      <PageShell
        title="Remittance transfer"
        subtitle={
          transferId
            ? `Read-only detail for transfer ${transferId}`
            : "Read-only detail for a remittance transfer"
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

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-emerald-100 text-primary-hover">
              <Send className="h-5 w-5" />
            </div>
            <div className="flex-1">
              <h2 className="text-sm font-semibold text-foreground">Transfer detail</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                Read-only remittance transfer detail from MusheX platform admin.
              </p>

              {transferQ.isLoading && (
                <p className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading transfer…
                </p>
              )}
              {transferQ.isError && (
                <p className="mt-3 flex items-center gap-2 text-xs text-danger">
                  <AlertCircle className="h-3.5 w-3.5" /> Could not load remittance transfer
                  from MusheX.
                </p>
              )}
              {!transferQ.isLoading && !transferQ.isError && !transfer && (
                <p className="mt-3 text-xs text-muted-foreground">
                  No transfer record returned for this ID.
                </p>
              )}
              {!transferQ.isLoading && !transferQ.isError && transfer && (
                <dl className="mt-3 grid gap-2 text-xs sm:grid-cols-2">
                  <div>
                    <dt className="text-muted-foreground">Transfer ID</dt>
                    <dd className="font-mono text-[11px] text-foreground">
                      {asString(transfer.remittanceRequestId ?? transfer.id)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Code</dt>
                    <dd className="text-foreground">{asString(transfer.remittanceCode)}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Sender</dt>
                    <dd className="text-foreground">{asString(transfer.senderRef)}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Recipient</dt>
                    <dd className="text-foreground">{asString(transfer.recipientRef)}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Amount</dt>
                    <dd className="text-foreground">
                      {asString(transfer.amount)} {asString(transfer.currency)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Status</dt>
                    <dd className="text-foreground">{asString(transfer.status)}</dd>
                  </div>
                </dl>
              )}

              <p className="mt-3 text-[11px] text-muted-foreground">
                BFF route:{" "}
                <code className="text-[10px]">
                  /internal/v1/finance/mushex-platform/remittance-transfers/{transferId || "{transferId}"}
                </code>
              </p>
              <p className="mt-2 inline-flex items-center gap-2 rounded-md border border-border bg-background px-2 py-1 text-[11px] text-muted-foreground">
                <Shield className="h-3.5 w-3.5" />
                Read-only view — no release / reverse / write actions are surfaced on this page.
              </p>
            </div>
          </div>
        </section>
      </PageShell>
    </AppLayout>
  );
}

