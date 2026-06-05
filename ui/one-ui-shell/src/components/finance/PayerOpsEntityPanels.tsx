"use client";

import { DomainCollectionTable } from "@/components/common/DomainCollectionTable";
import {
  normalizePaymentAttempts,
  normalizePaymentIntent,
  normalizePaymentReceipts,
  normalizePayerAdapters,
  normalizeSettlements,
  type PayerAdapterRow,
  type PayerAttemptRow,
  type PayerIntentRow,
  type PayerReceiptRow,
  type PayerSettlementRow,
} from "@/lib/finance/normalize-payer-ops";

function IntentSummaryCard({ intent }: { intent: PayerIntentRow }) {
  const fields = [
    ["Intent ID", intent.id],
    ["Status", intent.status],
    ["Amount", `${intent.amount} ${intent.currency}`],
    ["Rail", intent.rail],
    ["Source", `${intent.sourceType} / ${intent.sourceId}`],
  ] as const;

  return (
    <div className="mt-3 grid gap-3 rounded-lg border border-slate-200 bg-slate-50/80 p-4 sm:grid-cols-2">
      {fields.map(([label, value]) => (
        <div key={label}>
          <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{label}</p>
          <p className="mt-1 font-mono text-sm text-slate-900">{value}</p>
        </div>
      ))}
    </div>
  );
}

export function PayerOpsIntentPanel({
  data,
  isLoading,
  error,
}: {
  data: unknown;
  isLoading?: boolean;
  error?: Error | null;
}) {
  if (error) {
    return <p className="mt-3 text-sm text-red-700">Intent request failed: {error.message}</p>;
  }
  if (isLoading) return null;
  const intent = normalizePaymentIntent(data);
  if (!intent) {
    return <p className="mt-3 text-xs text-slate-500">No intent payload returned.</p>;
  }
  return <IntentSummaryCard intent={intent} />;
}

export function PayerOpsAttemptsTable({
  data,
  isLoading,
  error,
}: {
  data: unknown;
  isLoading?: boolean;
  error?: Error | null;
}) {
  const rows = normalizePaymentAttempts(data);
  return (
    <DomainCollectionTable<PayerAttemptRow>
      rows={rows}
      isLoading={isLoading}
      error={error}
      emptyTitle="No payment attempts"
      emptyHint="Initiate an attempt after loading a payment intent."
      rowKey={(row) => row.id}
      columns={[
        { key: "id", header: "Attempt", render: (row) => <span className="font-mono text-xs">{row.id}</span> },
        { key: "status", header: "Status", render: (row) => row.status },
        { key: "rail", header: "Rail", render: (row) => row.rail },
        { key: "reason", header: "Reason", render: (row) => row.reason },
        { key: "createdAt", header: "Created", render: (row) => row.createdAt },
      ]}
    />
  );
}

export function PayerOpsReceiptsTable({
  data,
  isLoading,
  error,
}: {
  data: unknown;
  isLoading?: boolean;
  error?: Error | null;
}) {
  const rows = normalizePaymentReceipts(data);
  return (
    <DomainCollectionTable<PayerReceiptRow>
      rows={rows}
      isLoading={isLoading}
      error={error}
      emptyTitle="No receipts"
      emptyHint="Receipts appear after a successful payment attempt."
      rowKey={(row) => row.id}
      columns={[
        { key: "id", header: "Receipt", render: (row) => <span className="font-mono text-xs">{row.id}</span> },
        { key: "status", header: "Status", render: (row) => row.status },
        { key: "amount", header: "Amount", render: (row) => row.amount },
        { key: "providerRef", header: "Provider ref", render: (row) => row.providerRef },
        { key: "createdAt", header: "Created", render: (row) => row.createdAt },
      ]}
    />
  );
}

export function PayerOpsAdaptersTable({
  data,
  isLoading,
  error,
}: {
  data: unknown;
  isLoading?: boolean;
  error?: Error | null;
}) {
  const rows = normalizePayerAdapters(data);
  return (
    <DomainCollectionTable<PayerAdapterRow>
      rows={rows}
      isLoading={isLoading}
      error={error}
      emptyTitle="No adapters"
      emptyHint="Load adapters to inspect rail capability flags."
      rowKey={(row) => row.id}
      columns={[
        { key: "rail", header: "Rail", render: (row) => row.rail },
        { key: "status", header: "Status", render: (row) => row.status },
        {
          key: "liveCapable",
          header: "Live capable",
          render: (row) => (
            <span
              className={
                row.liveCapable === "true"
                  ? "rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800"
                  : "rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800"
              }
            >
              {row.liveCapable}
            </span>
          ),
        },
      ]}
    />
  );
}

export function PayerOpsSettlementsTable({
  data,
  isLoading,
  error,
}: {
  data: unknown;
  isLoading?: boolean;
  error?: Error | null;
}) {
  const rows = normalizeSettlements(data);
  return (
    <DomainCollectionTable<PayerSettlementRow>
      rows={rows}
      isLoading={isLoading}
      error={error}
      emptyTitle="No linked settlements"
      emptyHint="Settlements link after remittance and payout orchestration."
      rowKey={(row) => row.id}
      columns={[
        { key: "id", header: "Settlement", render: (row) => <span className="font-mono text-xs">{row.id}</span> },
        { key: "status", header: "Status", render: (row) => row.status },
        { key: "period", header: "Period", render: (row) => row.period },
        { key: "amount", header: "Amount", render: (row) => row.amount },
      ]}
    />
  );
}
