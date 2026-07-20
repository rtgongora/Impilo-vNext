"use client";

import { useState } from "react";
import { Siren, ShieldAlert, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { CardPresentationField } from "@/components/card/CardPresentationField";
import { apiClient } from "@/lib/api-client";
import type { CardPresentation } from "@/hooks/queries/useCardResolve";

interface BreakGlassPhrResult {
  subjectCpid: string;
  breakGlassRequestId?: string;
  summary?: unknown;
  notice?: string;
}

/**
 * Break-glass card PHR read — for an emergency responder holding an unconscious /
 * unidentified patient's SMART card. Presents the card + a mandatory reason; the
 * server opens a governed break-glass request (mandatory post-hoc review), reads
 * the patient's emergency summary, and logs the access. This is deliberately
 * high-friction and fully audited: every read is reviewed.
 */
export default function BreakGlassCardPhrWorkspace() {
  const [presentation, setPresentation] = useState<CardPresentation | null>(null);
  const [reason, setReason] = useState("");
  const [result, setResult] = useState<BreakGlassPhrResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!presentation || !reason.trim()) return;
    setError(null);
    setResult(null);
    setBusy(true);
    try {
      const res = await apiClient.post<{ data: BreakGlassPhrResult }>(
        "/internal/v1/emergency/card-phr",
        { ...presentation, reason: reason.trim() },
      );
      setResult(res?.data ?? null);
    } catch {
      setError("Break-glass read was refused — the card could not be resolved, or emergency access could not be authorized.");
    } finally {
      setBusy(false);
    }
  }

  const summaryText = result?.summary ? JSON.stringify(result.summary, null, 2) : null;

  return (
    <AppLayout>
      <PageShell
        title="Emergency break-glass — card PHR"
        subtitle="Read an unconscious/unidentified patient's emergency summary from their SMART card. Fully audited and reviewed."
        icon={<Siren className="h-5 w-5" />}
      >
        <div className="max-w-2xl space-y-4">
          <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800 flex gap-2">
            <ShieldAlert className="h-5 w-5 shrink-0" />
            <span>
              Break-glass overrides consent for emergency care. Your access is logged against your identity
              and is subject to mandatory review. Use only for a genuine emergency.
            </span>
          </div>

          <CardPresentationField onPresented={setPresentation} />

          <label className="block">
            <span className="block text-sm font-medium mb-1">Reason for emergency access (required)</span>
            <textarea
              data-testid="break-glass-reason"
              className="w-full rounded-md border px-3 py-2 bg-background text-sm"
              rows={2}
              placeholder="e.g. Unconscious patient at scene, no next of kin, need allergies/medications"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>

          <button
            type="button"
            data-testid="break-glass-submit"
            className="rounded-md bg-red-600 text-white px-4 py-2 text-sm font-medium disabled:opacity-50"
            onClick={submit}
            disabled={!presentation || !reason.trim() || busy}
          >
            {busy ? <Loader2 className="h-4 w-4 animate-spin inline" /> : "Break glass & read summary"}
          </button>

          {error && <p className="text-sm text-red-600" data-testid="break-glass-error">{error}</p>}

          {result && (
            <div className="rounded-lg border p-4 space-y-2" data-testid="break-glass-result">
              <p className="text-sm">
                <span className="text-muted-foreground">Patient (CPID):</span>{" "}
                <span className="font-mono">{result.subjectCpid}</span>
              </p>
              <p className="text-xs text-muted-foreground">
                Break-glass request {result.breakGlassRequestId ?? "recorded"} · {result.notice}
              </p>
              {summaryText ? (
                <pre className="text-xs bg-muted rounded-md p-3 overflow-x-auto max-h-96">{summaryText}</pre>
              ) : (
                <p className="text-sm text-muted-foreground">No emergency summary is on record for this patient.</p>
              )}
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
