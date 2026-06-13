"use client";

import { usePathname } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { GovernanceActionResult } from "./GovernanceActionResult";
import { ImportRowReviewTable, type ImportRowReviewItem } from "./ImportRowReviewTable";
import {
  IMPORT_TYPES,
  approveImportBatch,
  getImportTemplate,
  listImportRows,
  parseCsvPreview,
  resendImportRowInvitation,
  revokeImportRowInvitation,
  sendImportInvitations,
  uploadImportBatch,
} from "@/lib/admin-governance/api/importsApi";
import { isActionResponse, isPendingBackend } from "@/lib/admin-governance/api/client";
import type { AdminGovernanceActionResponse, LookupEnvelope } from "@/lib/admin-governance/types";

export function BulkImportPanel() {
  const pathname = usePathname();
  const orgMatch = pathname.match(/\/organisations\/([^/]+)/);
  const orgId = orgMatch && orgMatch[1] !== "new" ? orgMatch[1] : null;
  const [importType, setImportType] = useState<string>(IMPORT_TYPES[0]);
  const [fileName, setFileName] = useState("");
  const [csvContent, setCsvContent] = useState("");
  const [preview, setPreview] = useState<Array<Record<string, string>>>([]);
  const [template, setTemplate] = useState<Record<string, unknown> | null>(null);
  const [batchId, setBatchId] = useState<string | null>(null);
  const [rows, setRows] = useState<ImportRowReviewItem[]>([]);
  const [result, setResult] = useState<AdminGovernanceActionResponse | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const refreshRows = useCallback(async () => {
    if (!batchId) return;
    const response = await listImportRows(batchId);
    if (!isActionResponse(response)) {
      const items = (response as LookupEnvelope<{ items: Record<string, unknown>[] }>).data?.items ?? [];
      setRows(
        items.map((item) => ({
          id: String(item.id ?? ""),
          rowNumber: typeof item.rowNumber === "number" ? item.rowNumber : undefined,
          outcome: typeof item.outcome === "string" ? item.outcome : undefined,
          invitationId: typeof item.invitationId === "string" ? item.invitationId : undefined,
          normalizedPayloadJson: typeof item.normalizedPayloadJson === "string" ? item.normalizedPayloadJson : undefined,
          invitation: item.invitation as Record<string, unknown> | undefined,
        })),
      );
    }
  }, [batchId]);

  useEffect(() => {
    void refreshRows();
  }, [refreshRows]);

  if (!orgId) return null;

  async function loadTemplate() {
    const response = await getImportTemplate(importType);
    if (!isActionResponse(response)) setTemplate((response as { data: Record<string, unknown> }).data ?? null);
  }

  async function handleFile(file: File) {
    const text = await file.text();
    setFileName(file.name);
    setCsvContent(text);
    setPreview(parseCsvPreview(text));
  }

  async function handleUpload() {
    const response = await uploadImportBatch({
      organisationId: orgId,
      importType,
      sourceFileName: fileName || "upload.csv",
      csvContent,
    });
    if (isActionResponse(response) && isPendingBackend(response)) {
      setMessage(response.friendlyMessage);
      return;
    }
    const batch = (response as { data?: { batch?: { id?: string }; sourceOfRecordStatus?: string } }).data?.batch;
    setBatchId(batch?.id ?? null);
    if ((response as { data?: { reconciliationRequired?: boolean } }).data?.reconciliationRequired) {
      setMessage("This import is queued in BFF fallback storage and will require reconciliation with workforce-governance.");
    }
    await refreshRows();
  }

  async function handleApprove() {
    if (!batchId) return;
    setResult(await approveImportBatch(batchId));
    await refreshRows();
  }

  async function handleSendInvitations() {
    if (!batchId) return;
    setResult(await sendImportInvitations(batchId));
    await refreshRows();
  }

  async function handleResendRow(rowId: string) {
    if (!batchId) return;
    setResult(await resendImportRowInvitation(batchId, rowId));
    await refreshRows();
  }

  async function handleRevokeRow(rowId: string) {
    if (!batchId) return;
    setResult(await revokeImportRowInvitation(batchId, rowId));
    await refreshRows();
  }

  return (
    <section className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-sm">
      <h3 className="text-base font-semibold text-foreground">Bulk Import & Reconciliation</h3>
      <p className="text-sm text-muted-foreground">Uploads stage records only — no user is activated from a spreadsheet row alone.</p>
      <div className="grid gap-3 md:grid-cols-2">
        <label className="text-sm">
          Import type
          <select className="mt-1 w-full rounded-lg border px-3 py-2" value={importType} onChange={(e) => setImportType(e.target.value)}>
            {IMPORT_TYPES.map((type) => (
              <option key={type} value={type}>
                {type.replace(/_/g, " ")}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          CSV file
          <input className="mt-1 w-full text-sm" type="file" accept=".csv,text/csv,.json" onChange={(e) => e.target.files?.[0] && void handleFile(e.target.files[0])} />
        </label>
      </div>
      <button type="button" className="rounded-lg border px-3 py-1.5 text-sm" onClick={() => void loadTemplate()}>
        View template/schema
      </button>
      {template ? (
        <pre className="overflow-auto rounded-lg bg-background p-3 text-xs">{JSON.stringify(template, null, 2)}</pre>
      ) : null}
      {preview.length ? (
        <div className="overflow-auto rounded-lg border">
          <table className="min-w-full text-xs">
            <tbody>
              {preview.slice(0, 5).map((row, index) => (
                <tr key={index} className="border-t">
                  {Object.values(row).map((value, cellIndex) => (
                    <td key={cellIndex} className="px-2 py-1">
                      {value}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {message ? <div className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">{message}</div> : null}
      <div className="flex flex-wrap gap-2">
        <button type="button" className="rounded-lg bg-primary px-4 py-2 text-sm text-white" onClick={() => void handleUpload()}>
          Upload & validate
        </button>
        <button type="button" className="rounded-lg border px-4 py-2 text-sm" disabled={!batchId} onClick={() => void handleApprove()}>
          Approve staged import
        </button>
        <button type="button" className="rounded-lg border px-4 py-2 text-sm" disabled={!batchId} onClick={() => void handleSendInvitations()}>
          Send invitations
        </button>
      </div>
      <ImportRowReviewTable rows={rows} onResend={handleResendRow} onRevoke={handleRevokeRow} />
      {result ? <GovernanceActionResult result={result} /> : null}
    </section>
  );
}
