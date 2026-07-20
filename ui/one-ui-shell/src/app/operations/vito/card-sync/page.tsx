"use client";

import { useState } from "react";
import { RefreshCw, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface DrainResult {
  processed: number;
  synced: number;
  failed: number;
  retried: number;
}

/**
 * Card-sync ops (B2 physical-write pipeline). Drains the pending card-update queue
 * to the (simulated) NFC writer on demand and shows per-pass counts. A scheduled
 * tick also drains when enabled; this is the operator's manual trigger.
 */
export default function CardSyncOpsWorkspace() {
  const [result, setResult] = useState<DrainResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function run() {
    setError(null);
    setBusy(true);
    try {
      const res = await apiClient.post<{ data: DrainResult }>(
        "/internal/v1/admin/card-sync/drain",
        {},
      );
      setResult(res?.data ?? null);
    } catch {
      setError("The card-sync service is temporarily unavailable.");
    } finally {
      setBusy(false);
    }
  }

  const tiles: [string, keyof DrainResult, string][] = [
    ["Processed", "processed", "text-foreground"],
    ["Synced", "synced", "text-green-600"],
    ["Retried", "retried", "text-amber-600"],
    ["Failed", "failed", "text-red-600"],
  ];

  return (
    <AppLayout>
      <PageShell
        title="Card-sync pipeline"
        subtitle="Drain queued health-summary updates onto physical cards (APDU write hand-off)."
        icon={<RefreshCw className="h-5 w-5" />}
      >
        <div className="max-w-xl space-y-4">
          <button
            type="button"
            data-testid="drain-btn"
            className="rounded-md bg-primary text-primary-foreground px-4 py-2 text-sm font-medium disabled:opacity-50"
            onClick={run}
            disabled={busy}
          >
            {busy ? <Loader2 className="h-4 w-4 animate-spin inline" /> : "Drain pending updates now"}
          </button>

          {result && (
            <div className="grid grid-cols-4 gap-3" data-testid="drain-result">
              {tiles.map(([label, key, cls]) => (
                <div key={key} className="rounded-lg border p-3 text-center">
                  <p className={`text-2xl font-semibold ${cls}`}>{result[key]}</p>
                  <p className="text-xs text-muted-foreground">{label}</p>
                </div>
              ))}
            </div>
          )}
          {error && <p className="text-sm text-red-600" data-testid="drain-error">{error}</p>}

          <p className="text-xs text-muted-foreground">
            Each pending entry is written to the card as ISO 7816-4 APDU frames; success marks it synced,
            repeated failures are flagged for attention. Real reader transmit is a hardware follow-on.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
