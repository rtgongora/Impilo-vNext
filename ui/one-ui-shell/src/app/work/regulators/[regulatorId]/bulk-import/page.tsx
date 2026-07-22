"use client";

/**
 * Council bulk-import console (ROM ingest rail). A council uploads its existing register — the
 * people and conditions of practice it already holds — so that data becomes part of the governed
 * record. The rail is deliberately staged: STAGE → MATCH → REVIEW → APPLY. Applying a batch writes
 * REGISTER DATA ONLY. It never provisions a login and never grants authority: a matched row updates
 * the person's register facts; an unmatched row is staged for onboarding but confers nothing.
 *
 * The gateway scopes the officer to their own council from the trust context, so the [regulatorId]
 * route param is context only — it is never sent as a query.
 *
 * Route: /work/regulators/[regulatorId]/bulk-import
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import {
  CheckCircle2,
  FileSpreadsheet,
  FileUp,
  Layers,
  Loader2,
  PlayCircle,
  ShieldAlert,
  ShieldCheck,
  Upload,
  XCircle,
} from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface ImportBatch {
  id: number;
  recordType?: string | null;
  sourceLabel?: string | null;
  filename?: string | null;
  status?: string | null;
  totalRows?: number | null;
  appliedRows?: number | null;
}
interface ImportRow {
  id: number;
  rowIndex?: number | null;
  raw?: Record<string, unknown> | null;
  matchStatus?: string | null;
  matchedRef?: string | null;
  reviewStatus?: string | null;
  applyStatus?: string | null;
  message?: string | null;
}

/** Tolerate raw-or-{data} and array-or-{data:array} envelopes from the BFF. */
function unwrapBatches(payload: unknown): ImportBatch[] {
  const p = (payload as { data?: unknown })?.data ?? payload;
  if (!Array.isArray(p)) return [];
  return p.filter((x): x is ImportBatch => typeof x === "object" && x !== null && "id" in x);
}
function unwrapBatch(payload: unknown): ImportBatch | null {
  const p = (payload as { data?: unknown })?.data ?? payload;
  if (typeof p !== "object" || p === null || !("id" in p)) return null;
  return p as ImportBatch;
}
function unwrapRows(payload: unknown): ImportRow[] {
  const p = (payload as { data?: unknown })?.data ?? payload;
  if (!Array.isArray(p)) return [];
  return p.filter((x): x is ImportRow => typeof x === "object" && x !== null && "id" in x);
}

/**
 * Parse CSV text into an array of row objects. First non-empty line is the header. Splitting is
 * loosely quote-aware (a comma inside "double quotes" does not split), enough for a demo import.
 */
function parseCsv(text: string): Array<Record<string, string>> {
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0);
  if (lines.length < 2) return [];
  const splitLine = (line: string): string[] => {
    const out: string[] = [];
    let cur = "";
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (ch === '"') {
        if (inQuotes && line[i + 1] === '"') {
          cur += '"';
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (ch === "," && !inQuotes) {
        out.push(cur);
        cur = "";
      } else {
        cur += ch;
      }
    }
    out.push(cur);
    return out.map((c) => c.trim());
  };
  const headers = splitLine(lines[0]);
  return lines.slice(1).map((line) => {
    const cells = splitLine(line);
    const obj: Record<string, string> = {};
    headers.forEach((h, i) => {
      if (h) obj[h] = cells[i] ?? "";
    });
    return obj;
  });
}

const MATCH_STYLE: Record<string, string> = {
  MATCHED_EXISTING: "bg-teal-50 text-teal-700 border-teal-200",
  NEW: "bg-blue-50 text-blue-700 border-blue-200",
  AMBIGUOUS: "bg-amber-50 text-amber-800 border-amber-200",
  INVALID: "bg-amber-50 text-amber-800 border-amber-200",
  UNMATCHED: "border-border text-muted-foreground",
};
const REVIEW_STYLE: Record<string, string> = {
  ACCEPTED: "bg-teal-50 text-teal-700 border-teal-200",
  REJECTED: "bg-red-50 text-red-700 border-red-200",
  PENDING: "border-border text-muted-foreground",
};
const APPLY_STYLE: Record<string, string> = {
  APPLIED: "bg-teal-50 text-teal-700 border-teal-200",
  SKIPPED: "bg-amber-50 text-amber-800 border-amber-200",
  FAILED: "bg-red-50 text-red-700 border-red-200",
  NONE: "border-border text-muted-foreground",
};

const REGISTRANT_COLUMNS = "registrationNumber, registrationCategory, status, healthId (or providerPublicId)";

export default function BulkImportPage() {
  const params = useParams<{ regulatorId: string }>();
  const regulatorId = params?.regulatorId ?? "";

  const [batches, setBatches] = useState<ImportBatch[]>([]);
  const [loadingBatches, setLoadingBatches] = useState(false);
  const [batchesError, setBatchesError] = useState<string | null>(null);

  const [activeBatch, setActiveBatch] = useState<ImportBatch | null>(null);
  const [rows, setRows] = useState<ImportRow[]>([]);
  const [loadingRows, setLoadingRows] = useState(false);
  const [rowsError, setRowsError] = useState<string | null>(null);

  const loadBatches = useCallback(async () => {
    setLoadingBatches(true);
    setBatchesError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/regulatory/console/imports");
      setBatches(unwrapBatches(res));
    } catch {
      setBatchesError("Could not load prior imports. Please try again.");
    } finally {
      setLoadingBatches(false);
    }
  }, []);

  useEffect(() => {
    void loadBatches();
  }, [loadBatches]);

  const loadRows = useCallback(async (batchId: number) => {
    setLoadingRows(true);
    setRowsError(null);
    try {
      const res = await apiClient.get<unknown>(
        `/internal/v1/regulatory/console/imports/${batchId}/rows`,
      );
      setRows(unwrapRows(res));
    } catch {
      setRowsError("Could not load the staged rows for this batch.");
    } finally {
      setLoadingRows(false);
    }
  }, []);

  const selectBatch = useCallback(
    async (batch: ImportBatch) => {
      setActiveBatch(batch);
      setRows([]);
      await loadRows(batch.id);
    },
    [loadRows],
  );

  // Stage a new batch from parsed rows, then select it and load its matched rows.
  const stageBatch = useCallback(
    async (body: {
      councilId?: number;
      recordType: string;
      sourceLabel?: string;
      filename?: string;
      rows: Array<Record<string, unknown>>;
    }) => {
      const res = await apiClient.post<unknown>("/internal/v1/regulatory/console/imports", body);
      const staged = unwrapBatch(res);
      await loadBatches();
      if (staged) {
        await selectBatch(staged);
      }
    },
    [loadBatches, selectBatch],
  );

  const reviewRow = useCallback(
    async (rowId: number, decision: "ACCEPTED" | "REJECTED") => {
      await apiClient.post<unknown>(
        `/internal/v1/regulatory/console/imports/rows/${rowId}/review`,
        { decision },
      );
      if (activeBatch) await loadRows(activeBatch.id);
    },
    [activeBatch, loadRows],
  );

  const applyBatch = useCallback(async () => {
    if (!activeBatch) return;
    const res = await apiClient.post<unknown>(
      `/internal/v1/regulatory/console/imports/${activeBatch.id}/apply`,
    );
    const updated = unwrapBatch(res);
    if (updated) setActiveBatch(updated);
    await loadBatches();
    await loadRows(activeBatch.id);
  }, [activeBatch, loadBatches, loadRows]);

  return (
    <AppLayout>
      <PageShell
        title="Bulk import"
        subtitle="Upload and reconcile your existing register"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          {regulatorId && (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-muted/40 px-2.5 py-1 text-[11px] font-medium text-muted-foreground">
              <ShieldAlert className="h-3.5 w-3.5 text-teal-600" /> Council {regulatorId}
            </span>
          )}

          <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3.5 text-sm text-amber-900">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" />
            <span>
              Uploading a register grants no login or authority — it records register data only. Rows
              are matched against existing records and reviewed before anything is applied.
            </span>
          </div>

          {/* Prior batches — select or resume one. */}
          <section className="space-y-3">
            <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <Layers className="h-4 w-4 text-teal-600" /> Prior imports
            </h2>
            {loadingBatches ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </div>
            ) : batchesError ? (
              <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {batchesError}
              </div>
            ) : batches.length === 0 ? (
              <div className="rounded-lg border border-border bg-muted/40 p-3 text-sm text-muted-foreground">
                No imports staged yet. Upload a register below to begin.
              </div>
            ) : (
              <ul className="grid gap-2 sm:grid-cols-2">
                {batches.map((b) => {
                  const isActive = activeBatch?.id === b.id;
                  return (
                    <li key={b.id}>
                      <button
                        type="button"
                        onClick={() => void selectBatch(b)}
                        className={`w-full rounded-xl border p-3 text-left transition-colors ${
                          isActive
                            ? "border-teal-300 bg-teal-50"
                            : "border-border bg-card hover:border-teal-200 hover:bg-muted/40"
                        }`}
                      >
                        <div className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
                          <FileSpreadsheet className="h-3.5 w-3.5 text-teal-600" />
                          {b.sourceLabel || b.filename || `Batch #${b.id}`}
                        </div>
                        <div className="mt-0.5 flex flex-wrap items-center gap-1.5 text-[11px] text-muted-foreground">
                          <span className="rounded-full border border-border px-1.5 py-0.5">
                            {(b.status || "—").replace(/_/g, " ").toLowerCase()}
                          </span>
                          {(b.recordType ?? null) && <span>{b.recordType}</span>}
                          <span>· {b.appliedRows ?? 0}/{b.totalRows ?? 0} applied</span>
                        </div>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          {/* Step 1 — Upload / stage */}
          <UploadPanel onStage={stageBatch} />

          {/* Steps 2 & 3 — Review + Apply */}
          {activeBatch && (
            <ReviewApplyPanel
              batch={activeBatch}
              rows={rows}
              loading={loadingRows}
              error={rowsError}
              onReview={reviewRow}
              onApply={applyBatch}
            />
          )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

function UploadPanel({
  onStage,
}: {
  onStage: (body: {
    councilId?: number;
    recordType: string;
    sourceLabel?: string;
    filename?: string;
    rows: Array<Record<string, unknown>>;
  }) => Promise<void>;
}) {
  const [recordType, setRecordType] = useState("REGISTRANT");
  const [sourceLabel, setSourceLabel] = useState("");
  const [councilId, setCouncilId] = useState("");
  const [filename, setFilename] = useState("");
  const [csvText, setCsvText] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const parsed = parseCsv(csvText);

  const onFile = async (file: File | undefined) => {
    if (!file) return;
    setError(null);
    const text = await file.text();
    setCsvText(text);
    setFilename(file.name);
    if (!sourceLabel) setSourceLabel(file.name.replace(/\.[^.]+$/, ""));
  };

  const stage = async () => {
    if (parsed.length === 0) {
      setError("Add CSV with a header row and at least one data row before staging.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const parsedCouncilId = councilId.trim() ? Number(councilId.trim()) : undefined;
      await onStage({
        ...(parsedCouncilId != null && !Number.isNaN(parsedCouncilId)
          ? { councilId: parsedCouncilId }
          : {}),
        recordType: recordType.trim() || "REGISTRANT",
        ...(sourceLabel.trim() ? { sourceLabel: sourceLabel.trim() } : {}),
        ...(filename.trim() ? { filename: filename.trim() } : {}),
        rows: parsed,
      });
      setCsvText("");
      setFilename("");
    } catch {
      setError("Could not stage this upload. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-3 rounded-2xl border border-border bg-card p-4">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <FileUp className="h-4 w-4 text-teal-600" /> Upload your register
      </h2>
      <p className="text-xs text-muted-foreground">
        Provide a CSV. For <span className="font-medium">REGISTRANT</span> records the expected
        columns are: <span className="font-mono">{REGISTRANT_COLUMNS}</span> to resolve the person.
      </p>

      <div className="grid gap-3 sm:grid-cols-3">
        <label className="text-[11px] text-muted-foreground">
          Record type
          <input
            type="text"
            value={recordType}
            onChange={(e) => setRecordType(e.target.value)}
            className="mt-1 w-full rounded-lg border border-border px-2.5 py-1.5 text-sm"
          />
        </label>
        <label className="text-[11px] text-muted-foreground">
          Source label
          <input
            type="text"
            value={sourceLabel}
            onChange={(e) => setSourceLabel(e.target.value)}
            placeholder="NCZ 2024 register"
            className="mt-1 w-full rounded-lg border border-border px-2.5 py-1.5 text-sm"
          />
        </label>
        <label className="text-[11px] text-muted-foreground">
          Council ID (optional)
          <input
            type="number"
            value={councilId}
            onChange={(e) => setCouncilId(e.target.value)}
            placeholder="e.g. 4"
            className="mt-1 w-full rounded-lg border border-border px-2.5 py-1.5 text-sm"
          />
        </label>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-muted/40">
          <Upload className="h-3.5 w-3.5" /> Choose CSV file
          <input
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            onChange={(e) => void onFile(e.target.files?.[0])}
          />
        </label>
        {filename && <span className="text-[11px] text-muted-foreground">{filename}</span>}
      </div>

      <textarea
        value={csvText}
        onChange={(e) => setCsvText(e.target.value)}
        rows={5}
        placeholder={`registrationNumber,registrationCategory,status,healthId\nNCZ-0001,PHARMACIST,ACTIVE,HID-...`}
        className="w-full rounded-lg border border-border px-3 py-2 font-mono text-xs"
      />

      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-[11px] text-muted-foreground">
          {parsed.length > 0
            ? `${parsed.length} row${parsed.length === 1 ? "" : "s"} parsed and ready to stage.`
            : "Paste or upload CSV to preview rows."}
        </span>
        <button
          type="button"
          onClick={stage}
          disabled={saving || parsed.length === 0}
          className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
        >
          {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <FileUp className="h-3.5 w-3.5" />}
          {saving ? "Staging…" : "Stage upload"}
        </button>
      </div>
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-2.5 text-xs text-red-800">
          {error}
        </div>
      )}
    </section>
  );
}

function ReviewApplyPanel({
  batch,
  rows,
  loading,
  error,
  onReview,
  onApply,
}: {
  batch: ImportBatch;
  rows: ImportRow[];
  loading: boolean;
  error: string | null;
  onReview: (rowId: number, decision: "ACCEPTED" | "REJECTED") => Promise<void>;
  onApply: () => Promise<void>;
}) {
  const [applying, setApplying] = useState(false);

  const matched = rows.filter((r) => r.matchStatus?.toUpperCase() === "MATCHED_EXISTING").length;
  const isNew = rows.filter((r) => r.matchStatus?.toUpperCase() === "NEW").length;
  const invalid = rows.filter((r) =>
    ["INVALID", "AMBIGUOUS"].includes(r.matchStatus?.toUpperCase() ?? ""),
  ).length;
  const accepted = rows.filter((r) => r.reviewStatus?.toUpperCase() === "ACCEPTED").length;
  const hasSkipped = rows.some((r) => r.applyStatus?.toUpperCase() === "SKIPPED");

  const apply = async () => {
    setApplying(true);
    try {
      await onApply();
    } finally {
      setApplying(false);
    }
  };

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <FileSpreadsheet className="h-4 w-4 text-teal-600" /> Review — {batch.sourceLabel || batch.filename || `Batch #${batch.id}`}
        </h2>
        <div className="flex flex-wrap items-center gap-1.5 text-[11px]">
          <Count label="matched" value={matched} className="border-teal-200 bg-teal-50 text-teal-700" />
          <Count label="new" value={isNew} className="border-blue-200 bg-blue-50 text-blue-700" />
          <Count label="invalid" value={invalid} className="border-amber-200 bg-amber-50 text-amber-800" />
          <Count label="accepted" value={accepted} className="border-border bg-muted/40 text-muted-foreground" />
        </div>
      </div>

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading rows…
        </div>
      ) : error ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
      ) : rows.length === 0 ? (
        <div className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground">
          This batch has no staged rows.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full min-w-[46rem] text-left text-xs">
            <thead className="bg-muted/40 text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">#</th>
                <th className="px-3 py-2 font-medium">Record</th>
                <th className="px-3 py-2 font-medium">Match</th>
                <th className="px-3 py-2 font-medium">Review</th>
                <th className="px-3 py-2 font-medium">Apply</th>
                <th className="px-3 py-2 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <RowLine key={r.id} row={r} onReview={onReview} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-3">
        <div className="space-y-1 text-xs text-muted-foreground">
          <div className="flex items-center gap-1.5 font-medium text-foreground">
            <PlayCircle className="h-4 w-4 text-teal-600" /> Apply accepted rows
          </div>
          <p>
            Applies accepted, non-invalid rows. This writes register data only — it grants no login
            and no authority.
          </p>
        </div>
        <button
          type="button"
          onClick={apply}
          disabled={applying || accepted === 0}
          className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
        >
          {applying ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <PlayCircle className="h-3.5 w-3.5" />}
          {applying ? "Applying…" : "Apply accepted rows"}
        </button>
      </div>

      {hasSkipped && (
        <p className="rounded-lg border border-amber-200 bg-amber-50 p-2.5 text-[11px] text-amber-900">
          Skipped rows had no matching provider — they are staged for onboarding and grant no
          authority. Nobody gains a login from this import.
        </p>
      )}
    </section>
  );
}

function RowLine({
  row,
  onReview,
}: {
  row: ImportRow;
  onReview: (rowId: number, decision: "ACCEPTED" | "REJECTED") => Promise<void>;
}) {
  const [saving, setSaving] = useState(false);
  const match = row.matchStatus?.toUpperCase() ?? "";
  const review = row.reviewStatus?.toUpperCase() ?? "";
  const apply = row.applyStatus?.toUpperCase() ?? "NONE";
  const isInvalid = match === "INVALID";
  const canReview = !isInvalid && review === "PENDING";

  const decide = async (decision: "ACCEPTED" | "REJECTED") => {
    setSaving(true);
    try {
      await onReview(row.id, decision);
    } finally {
      setSaving(false);
    }
  };

  const raw = row.raw ?? {};
  const primary =
    (typeof raw.registrationNumber === "string" && raw.registrationNumber) ||
    (typeof raw.healthId === "string" && raw.healthId) ||
    (typeof raw.providerPublicId === "string" && raw.providerPublicId) ||
    `Row ${row.rowIndex ?? row.id}`;
  const secondaryParts = [raw.registrationCategory, raw.status]
    .filter((v): v is string => typeof v === "string" && v.length > 0);

  return (
    <tr className="border-t border-border align-top">
      <td className="px-3 py-2 text-muted-foreground">{row.rowIndex ?? "—"}</td>
      <td className="px-3 py-2">
        <div className="font-mono text-foreground">{primary}</div>
        {secondaryParts.length > 0 && (
          <div className="text-[11px] text-muted-foreground">{secondaryParts.join(" · ")}</div>
        )}
        {row.matchedRef && (
          <div className="text-[11px] text-teal-700">→ {row.matchedRef}</div>
        )}
        {row.message && <div className="mt-0.5 text-[11px] text-muted-foreground">{row.message}</div>}
      </td>
      <td className="px-3 py-2">
        <Badge value={match} styleMap={MATCH_STYLE} />
      </td>
      <td className="px-3 py-2">
        <Badge value={review} styleMap={REVIEW_STYLE} />
      </td>
      <td className="px-3 py-2">
        <Badge value={apply} styleMap={APPLY_STYLE} />
      </td>
      <td className="px-3 py-2">
        {canReview ? (
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => void decide("ACCEPTED")}
              disabled={saving}
              className="inline-flex items-center gap-1 rounded-lg border border-teal-200 bg-teal-50 px-2 py-1 text-[11px] font-medium text-teal-700 hover:bg-teal-100 disabled:opacity-50"
            >
              {saving ? <Loader2 className="h-3 w-3 animate-spin" /> : <CheckCircle2 className="h-3 w-3" />} Accept
            </button>
            <button
              type="button"
              onClick={() => void decide("REJECTED")}
              disabled={saving}
              className="inline-flex items-center gap-1 rounded-lg border border-red-200 bg-red-50 px-2 py-1 text-[11px] font-medium text-red-700 hover:bg-red-100 disabled:opacity-50"
            >
              <XCircle className="h-3 w-3" /> Reject
            </button>
          </div>
        ) : (
          <span className="text-[11px] text-muted-foreground">
            {isInvalid ? "not reviewable" : "—"}
          </span>
        )}
      </td>
    </tr>
  );
}

function Badge({ value, styleMap }: { value: string; styleMap: Record<string, string> }) {
  if (!value) return <span className="text-[11px] text-muted-foreground">—</span>;
  return (
    <span
      className={`rounded-full border px-2 py-0.5 text-[10px] font-medium ${
        styleMap[value] ?? "border-border text-muted-foreground"
      }`}
    >
      {value.replace(/_/g, " ").toLowerCase()}
    </span>
  );
}

function Count({ label, value, className }: { label: string; value: number; className: string }) {
  return (
    <span className={`rounded-full border px-2 py-0.5 font-medium ${className}`}>
      {value} {label}
    </span>
  );
}
