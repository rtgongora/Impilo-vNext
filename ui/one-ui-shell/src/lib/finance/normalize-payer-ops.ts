/** Normalize MusheX payer-ops JSON:API payloads into product table rows. */

export type PayerIntentRow = {
  id: string;
  status: string;
  amount: string;
  currency: string;
  rail: string;
  sourceType: string;
  sourceId: string;
};

export type PayerAttemptRow = {
  id: string;
  status: string;
  rail: string;
  reason: string;
  createdAt: string;
};

export type PayerReceiptRow = {
  id: string;
  status: string;
  amount: string;
  providerRef: string;
  createdAt: string;
};

export type PayerAdapterRow = {
  id: string;
  rail: string;
  liveCapable: string;
  status: string;
};

export type PayerSettlementRow = {
  id: string;
  status: string;
  period: string;
  amount: string;
};

function unwrapCollection(payload: unknown): unknown[] {
  if (Array.isArray(payload)) return payload;
  if (!payload || typeof payload !== "object") return [];
  const record = payload as Record<string, unknown>;
  if (Array.isArray(record.data)) return record.data;
  for (const key of ["items", "content", "attempts", "receipts", "settlements", "adapters"]) {
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

export function normalizePaymentIntent(payload: unknown): PayerIntentRow | null {
  const rows = unwrapCollection(payload);
  const row = rows[0];
  if (!row) return null;
  const attrs = readAttrs(row);
  const record = row as Record<string, unknown>;
  return {
    id: String(record.id ?? attrs.intentId ?? attrs.id ?? "—"),
    status: String(attrs.status ?? attrs.intentStatus ?? "UNKNOWN"),
    amount: String(attrs.amount ?? attrs.totalAmount ?? "—"),
    currency: String(attrs.currency ?? "—"),
    rail: String(attrs.rail ?? attrs.selectedRail ?? attrs.paymentRail ?? "—"),
    sourceType: String(attrs.sourceType ?? "—"),
    sourceId: String(attrs.sourceId ?? attrs.sourceRef ?? "—"),
  };
}

export function normalizePaymentAttempts(payload: unknown): PayerAttemptRow[] {
  return unwrapCollection(payload).map((row, index) => {
    const record = row as Record<string, unknown>;
    const attrs = readAttrs(row);
    return {
      id: String(record.id ?? attrs.attemptId ?? index),
      status: String(attrs.status ?? attrs.attemptStatus ?? "—"),
      rail: String(attrs.rail ?? attrs.selectedRail ?? "—"),
      reason: String(attrs.reason ?? attrs.blockReason ?? attrs.failureReason ?? "—"),
      createdAt: String(attrs.createdAt ?? attrs.created_at ?? "—"),
    };
  });
}

export function normalizePaymentReceipts(payload: unknown): PayerReceiptRow[] {
  return unwrapCollection(payload).map((row, index) => {
    const record = row as Record<string, unknown>;
    const attrs = readAttrs(row);
    return {
      id: String(record.id ?? attrs.receiptId ?? index),
      status: String(attrs.status ?? "—"),
      amount: String(attrs.amount ?? "—"),
      providerRef: String(attrs.providerRef ?? attrs.externalRef ?? "—"),
      createdAt: String(attrs.createdAt ?? attrs.created_at ?? "—"),
    };
  });
}

export function normalizePayerAdapters(payload: unknown): PayerAdapterRow[] {
  return unwrapCollection(payload).map((row, index) => {
    const record = row as Record<string, unknown>;
    const attrs = readAttrs(row);
    return {
      id: String(record.id ?? attrs.adapterId ?? attrs.rail ?? index),
      rail: String(attrs.rail ?? attrs.name ?? "—"),
      liveCapable: String(attrs.liveCapable ?? attrs.live_capable ?? "false"),
      status: String(attrs.status ?? attrs.adapterStatus ?? "—"),
    };
  });
}

export function normalizeSettlements(payload: unknown): PayerSettlementRow[] {
  return unwrapCollection(payload).map((row, index) => {
    const record = row as Record<string, unknown>;
    const attrs = readAttrs(row);
    return {
      id: String(record.id ?? attrs.settlementId ?? index),
      status: String(attrs.status ?? "—"),
      period: String(attrs.period ?? attrs.settlementPeriod ?? "—"),
      amount: String(attrs.amount ?? attrs.totalAmount ?? "—"),
    };
  });
}
