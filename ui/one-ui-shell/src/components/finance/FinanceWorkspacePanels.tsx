"use client";

import { DomainCollectionTable } from "@/components/common/DomainCollectionTable";

type InvoiceRow = {
  id: string;
  status: string;
  billId: string;
  total: string;
  dueDate: string;
};

type ChargeRow = {
  id: string;
  code: string;
  description: string;
  amount: string;
  status: string;
};

type WalletRow = {
  id: string;
  ownerRef: string;
  balance: string;
  currency: string;
  status: string;
};

function unwrapCollection(payload: unknown): unknown[] {
  if (Array.isArray(payload)) return payload;
  if (!payload || typeof payload !== "object") return [];
  const record = payload as Record<string, unknown>;
  if (Array.isArray(record.data)) return record.data;
  for (const key of ["items", "content", "charges", "wallets"]) {
    const nested = record[key];
    if (Array.isArray(nested)) return nested;
  }
  return [record];
}

function readAttrs(row: unknown): Record<string, unknown> {
  if (!row || typeof row !== "object") return {};
  const record = row as Record<string, unknown>;
  return (record.attributes as Record<string, unknown> | undefined) ?? record;
}

function normalizeInvoice(payload: unknown): InvoiceRow | null {
  const row = unwrapCollection(payload)[0];
  if (!row) return null;
  const record = row as Record<string, unknown>;
  const attrs = readAttrs(row);
  return {
    id: String(record.id ?? attrs.invoiceId ?? "—"),
    status: String(attrs.status ?? attrs.invoiceStatus ?? "—"),
    billId: String(attrs.billId ?? attrs.bill_id ?? "—"),
    total: String(attrs.total ?? attrs.totalAmount ?? attrs.amount ?? "—"),
    dueDate: String(attrs.dueDate ?? attrs.due_at ?? "—"),
  };
}

function normalizeCharges(payload: unknown): ChargeRow[] {
  return unwrapCollection(payload).map((row, index) => {
    const record = row as Record<string, unknown>;
    const attrs = readAttrs(row);
    return {
      id: String(record.id ?? attrs.chargeId ?? index),
      code: String(attrs.code ?? attrs.chargeCode ?? "—"),
      description: String(attrs.description ?? attrs.label ?? "—"),
      amount: String(attrs.amount ?? "—"),
      status: String(attrs.status ?? "—"),
    };
  });
}

function normalizeWallets(payload: unknown): WalletRow[] {
  return unwrapCollection(payload).map((row, index) => {
    const record = row as Record<string, unknown>;
    const attrs = readAttrs(row);
    return {
      id: String(record.id ?? attrs.walletId ?? index),
      ownerRef: String(attrs.ownerRef ?? attrs.owner_ref ?? "—"),
      balance: String(attrs.balance ?? attrs.availableBalance ?? "—"),
      currency: String(attrs.currency ?? "—"),
      status: String(attrs.status ?? "—"),
    };
  });
}

export function FinanceInvoicePanel({
  data,
  isLoading,
  error,
}: {
  data: unknown;
  isLoading?: boolean;
  error?: Error | null;
}) {
  if (error) {
    return <p className="mt-3 text-sm text-red-700">Could not load invoice: {error.message}</p>;
  }
  if (isLoading) return null;
  const invoice = normalizeInvoice(data);
  if (!invoice) {
    return <p className="mt-3 text-xs text-slate-500">No invoice data returned.</p>;
  }

  const fields = [
    ["Invoice", invoice.id],
    ["Status", invoice.status],
    ["Bill", invoice.billId],
    ["Total", invoice.total],
    ["Due", invoice.dueDate],
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

export function FinanceChargesTable({
  data,
  isLoading,
  error,
}: {
  data: unknown;
  isLoading?: boolean;
  error?: Error | null;
}) {
  const rows = normalizeCharges(data);
  return (
    <DomainCollectionTable<ChargeRow>
      rows={rows}
      isLoading={isLoading}
      error={error}
      emptyTitle="No charges"
      emptyHint="Load charges with a bill id."
      rowKey={(row) => row.id}
      columns={[
        { key: "code", header: "Code", render: (row) => row.code },
        { key: "description", header: "Description", render: (row) => row.description },
        { key: "amount", header: "Amount", render: (row) => row.amount },
        { key: "status", header: "Status", render: (row) => row.status },
      ]}
    />
  );
}

export function FinanceWalletsTable({
  data,
  isLoading,
  error,
}: {
  data: unknown;
  isLoading?: boolean;
  error?: Error | null;
}) {
  const rows = normalizeWallets(data);
  return (
    <DomainCollectionTable<WalletRow>
      rows={rows}
      isLoading={isLoading}
      error={error}
      emptyTitle="No wallets"
      emptyHint="Load wallets with an owner reference."
      rowKey={(row) => row.id}
      columns={[
        { key: "ownerRef", header: "Owner", render: (row) => row.ownerRef },
        { key: "balance", header: "Balance", render: (row) => row.balance },
        { key: "currency", header: "Currency", render: (row) => row.currency },
        { key: "status", header: "Status", render: (row) => row.status },
      ]}
    />
  );
}
