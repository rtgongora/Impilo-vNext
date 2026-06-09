import type { JsonApiColumnDef } from "@/components/common/JsonApiDataTable";

/** Broad fallback columns for BFF JSON payloads. */
export const GENERIC_RECORD_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "ID", fields: ["id", "identifier", "code", "key", "uuid"] },
  { key: "name", header: "Name", fields: ["name", "title", "label", "displayName"] },
  { key: "status", header: "Status", fields: ["status", "state", "result"] },
  { key: "type", header: "Type", fields: ["type", "kind", "category"] },
  { key: "updated", header: "Updated", fields: ["updatedAt", "createdAt", "timestamp", "occurredAt"] },
];

export const INTEGRATION_ROUTE_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "Route ID", fields: ["id", "routeId"] },
  { key: "path", header: "Path", fields: ["path", "route", "pattern"] },
  { key: "method", header: "Method", fields: ["method", "httpMethod"] },
  { key: "target", header: "Target", fields: ["target", "destination", "upstream"] },
  { key: "status", header: "Status", fields: ["status", "enabled", "active"] },
];

export const INTEGRATION_DEAD_LETTER_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "ID", fields: ["id", "messageId"] },
  { key: "route", header: "Route", fields: ["route", "routeId", "path"] },
  { key: "error", header: "Error", fields: ["error", "errorMessage", "reason"] },
  { key: "retries", header: "Retries", fields: ["retryCount", "retries"] },
  { key: "created", header: "Created", fields: ["createdAt", "failedAt"] },
];

export const REGISTRY_ENTITY_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "ID", fields: ["id", "providerId", "facilityId", "termId", "code"] },
  { key: "name", header: "Name", fields: ["name", "displayName", "legalName", "term"] },
  { key: "status", header: "Status", fields: ["status", "lifecycleStatus", "verificationStatus"] },
  { key: "type", header: "Type", fields: ["type", "category", "specialty"] },
  { key: "updated", header: "Updated", fields: ["updatedAt", "lastModified"] },
];

export const MARKETPLACE_ORDER_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "Order ID", fields: ["id", "orderId", "orderNumber"] },
  { key: "status", header: "Status", fields: ["status", "state"] },
  { key: "vendor", header: "Vendor", fields: ["vendorId", "vendorName", "sellerId"] },
  { key: "total", header: "Total", fields: ["totalAmount", "amount"] },
  { key: "created", header: "Created", fields: ["createdAt", "orderedAt"] },
];

export const MARKETPLACE_PRODUCT_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "Product ID", fields: ["id", "productId", "sku"] },
  { key: "name", header: "Name", fields: ["name", "description", "title"] },
  { key: "status", header: "Status", fields: ["status", "availability"] },
  { key: "qty", header: "Qty", fields: ["quantity", "qty"] },
  { key: "price", header: "Price", fields: ["unitPrice", "price", "amount"] },
];

export const EHR_CLINICAL_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "ID", fields: ["id", "resourceId", "entryId"] },
  { key: "type", header: "Type", fields: ["type", "resourceType", "category"] },
  { key: "status", header: "Status", fields: ["status", "clinicalStatus"] },
  { key: "code", header: "Code", fields: ["code", "loinc", "snomed"] },
  { key: "date", header: "Date", fields: ["effectiveDateTime", "authoredOn", "recordedDate"] },
];

export const ERP_ASSET_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "Asset ID", fields: ["id", "assetId", "assetTag"] },
  { key: "name", header: "Name", fields: ["name", "description", "assetName"] },
  { key: "status", header: "Status", fields: ["status", "condition"] },
  { key: "value", header: "Book value", fields: ["bookValue", "netBookValue", "amount"] },
  { key: "period", header: "Period", fields: ["period", "fiscalPeriod", "depreciationPeriod"] },
];

export const DISPATCH_ENTITY_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "ID", fields: ["id", "deliveryId", "missionId", "assetId"] },
  { key: "status", header: "Status", fields: ["status", "state"] },
  { key: "route", header: "Route", fields: ["route", "origin", "destination"] },
  { key: "assignee", header: "Assignee", fields: ["courierId", "driverId", "assignee"] },
  { key: "updated", header: "Updated", fields: ["updatedAt", "lastEventAt"] },
];

export const COVERAGE_RESULT_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "ID", fields: ["id", "claimId", "coverageId", "memberId"] },
  { key: "status", header: "Status", fields: ["status", "state", "resultCode", "result_code"] },
  { key: "eligible", header: "Eligible", fields: ["eligible", "isEligible"] },
  { key: "message", header: "Message", fields: ["message", "detail", "reason"] },
  { key: "amount", header: "Amount", fields: ["amount", "totalAmount"] },
];

export const REPORT_JOB_COLUMNS: JsonApiColumnDef[] = [
  { key: "key", header: "Parameter", fields: ["key", "name", "parameter"] },
  { key: "value", header: "Value", fields: ["value", "defaultValue"] },
  { key: "type", header: "Type", fields: ["type", "dataType"] },
];

export const LANDELA_METADATA_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "Document ID", fields: ["id", "documentId", "docId"] },
  { key: "title", header: "Title", fields: ["title", "name", "filename"] },
  { key: "type", header: "Type", fields: ["type", "mimeType", "contentType"] },
  { key: "status", header: "Status", fields: ["status", "state"] },
  { key: "updated", header: "Updated", fields: ["updatedAt", "createdAt"] },
];

export const WELLNESS_CONNECT_COLUMNS: JsonApiColumnDef[] = [
  { key: "id", header: "ID", fields: ["id", "connectionId", "deviceId"] },
  { key: "provider", header: "Provider", fields: ["provider", "vendor", "source"] },
  { key: "status", header: "Status", fields: ["status", "syncStatus"] },
  { key: "lastSync", header: "Last sync", fields: ["lastSyncAt", "syncedAt"] },
  { key: "metric", header: "Metric", fields: ["metric", "dataType"] },
];
