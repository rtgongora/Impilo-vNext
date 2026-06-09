import type { JsonApiColumnDef } from "@/components/common/JsonApiDataTable";

/** Shared column sets for finance / MusheX BFF payloads surfaced as product tables. */

export const FINANCE_INTENT_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "Intent ID", fields: ["id", "intentId", "paymentIntentId"] },
  { key: "status", header: "Status", fields: ["status", "state"] },
  { key: "amount", header: "Amount", fields: ["amount", "totalAmount"] },
  { key: "currency", header: "Currency", fields: ["currency"] },
  { key: "rail", header: "Rail", fields: ["rail", "adapterType", "preferredRail"] },
  { key: "created", header: "Created", fields: ["createdAt", "created"] },
];

export const FINANCE_SETTLEMENT_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "Settlement ID", fields: ["id", "settlementId"] },
  { key: "status", header: "Status", fields: ["status", "state"] },
  { key: "periodStart", header: "Period start", fields: ["periodStart", "startDate"] },
  { key: "periodEnd", header: "Period end", fields: ["periodEnd", "endDate"] },
  { key: "total", header: "Total", fields: ["totalAmount", "amount", "netAmount"] },
  { key: "currency", header: "Currency", fields: ["currency"] },
];

export const FINANCE_LEDGER_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "Entry ID", fields: ["id", "entryId", "ledgerEntryId"] },
  { key: "type", header: "Type", fields: ["type", "entryType"] },
  { key: "amount", header: "Amount", fields: ["amount", "debit", "credit"] },
  { key: "currency", header: "Currency", fields: ["currency"] },
  { key: "status", header: "Status", fields: ["status", "state"] },
  { key: "posted", header: "Posted", fields: ["postedAt", "createdAt"] },
];

export const FINANCE_REFUND_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "Refund ID", fields: ["id", "refundId"] },
  { key: "status", header: "Status", fields: ["status", "state"] },
  { key: "amount", header: "Amount", fields: ["amount", "refundAmount"] },
  { key: "reason", header: "Reason", fields: ["reason", "description"] },
  { key: "created", header: "Created", fields: ["createdAt", "created"] },
];

export const FINANCE_TRACKING_COLUMNS: JsonApiColumnDef[] = [
  { key: "event", header: "Event", fields: ["event", "status", "type", "milestone"] },
  { key: "location", header: "Location", fields: ["location", "facilityId", "site"] },
  { key: "carrier", header: "Carrier", fields: ["carrier", "provider"] },
  { key: "timestamp", header: "Timestamp", fields: ["timestamp", "occurredAt", "updatedAt", "createdAt"] },
  { key: "note", header: "Note", fields: ["note", "message", "description"] },
];

export const FINANCE_MUTATION_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "ID", fields: ["id", "settlementId", "intentId", "invoiceId", "decisionId"] },
  { key: "status", header: "Status", fields: ["status", "state", "result"] },
  { key: "message", header: "Message", fields: ["message", "detail", "description"] },
  { key: "amount", header: "Amount", fields: ["amount", "totalAmount"] },
];
