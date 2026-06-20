"use client";

import { useState } from "react";
import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useWorkforceImportBridge, isVashandiDegraded } from "@/hooks/useVashandi";
import type { ImportBridgeRequest, ImportWorkforceRow } from "@/lib/vashandi/api/profiles";

function parseImportPayload(text: string): ImportBridgeRequest | null {
  const trimmed = text.trim();
  if (!trimmed) return null;
  try {
    const parsed = JSON.parse(trimmed) as ImportBridgeRequest;
    if (typeof parsed.source === "string" && Array.isArray(parsed.rows)) {
      return parsed;
    }
  } catch {
    // fall through to CSV
  }
  const lines = trimmed.split(/\r?\n/).filter((line) => line.trim().length > 0);
  if (lines.length < 2) return null;
  const headers = lines[0].split(",").map((h) => h.trim());
  const rows: ImportWorkforceRow[] = lines.slice(1).map((line) => {
    const values = line.split(",").map((v) => v.trim());
    const row: ImportWorkforceRow = {};
    headers.forEach((header, index) => {
      const value = values[index];
      if (!value) return;
      (row as Record<string, string>)[header] = value;
    });
    return row;
  });
  return { source: "csv-upload", rows };
}

export default function Page() {
  const [source, setSource] = useState("registry-sync");
  const [payload, setPayload] = useState("");
  const importMutation = useWorkforceImportBridge();
  const response = importMutation.data;

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const body = parseImportPayload(payload);
    if (!body) {
      return;
    }
    if (source.trim()) {
      body.source = source.trim();
    }
    await importMutation.mutateAsync(body);
  }

  return (
    <VashandiShell title="Workforce Imports" subtitle="Bulk workforce profile and assignment import bridge">
      <form onSubmit={handleSubmit} className="space-y-4 rounded-2xl border border-border bg-card p-6">
        <p className="text-sm text-muted-foreground">
          Submit workforce rows through the BFF import bridge at{" "}
          <code className="text-xs">/internal/v1/vashandi/workforce-profiles/import-bridge</code>. Paste JSON{" "}
          <code className="text-xs">{`{"source":"…","rows":[…]}`}</code> or CSV with a header row.
        </p>
        <label className="block space-y-1 text-sm">
          <span className="font-medium text-foreground">Source label</span>
          <input
            className="w-full rounded-lg border border-border px-3 py-2"
            value={source}
            onChange={(event) => setSource(event.target.value)}
            placeholder="registry-sync"
          />
        </label>
        <label className="block space-y-1 text-sm">
          <span className="font-medium text-foreground">Import payload</span>
          <textarea
            className="min-h-[200px] w-full rounded-lg border border-border px-3 py-2 font-mono text-xs"
            value={payload}
            onChange={(event) => setPayload(event.target.value)}
            placeholder='{"source":"hr-export","rows":[{"healthId":"…","providerWorkerId":"…"}]}'
          />
        </label>
        <button
          type="submit"
          disabled={importMutation.isPending || !payload.trim()}
          className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {importMutation.isPending ? "Importing…" : "Run import bridge"}
        </button>
      </form>

      {response && isVashandiDegraded(response) ? (
        <VashandiFriendlyBlockedState
          title={response.friendlyTitle ?? "Import blocked"}
          description={response.friendlyMessage ?? response.integrationMessage ?? "Vashandi upstream is unavailable."}
          backHref="/work/vashandi"
        />
      ) : null}

      {response?.success ? (
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
          Import completed — profiles:{" "}
          {String((response.data as Record<string, unknown> | undefined)?.profilesImported ?? "—")}, assignments:{" "}
          {String((response.data as Record<string, unknown> | undefined)?.assignmentsImported ?? "—")}.
        </div>
      ) : null}

      {importMutation.isError ? (
        <div className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
          Import failed. Check payload shape and policy scope, then retry.
        </div>
      ) : null}
    </VashandiShell>
  );
}
