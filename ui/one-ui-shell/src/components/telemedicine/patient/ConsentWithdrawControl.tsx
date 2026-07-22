"use client";

/**
 * TM-B5 (B5-4): mid-session consent withdrawal.
 *
 * A patient can withdraw consent at any point in a live consult. Confirming calls the governed
 * withdrawal endpoint (records WITHDRAWN + forces the media rung to ASYNC + best-effort server room
 * teardown) and the page drops local media immediately — the fastest possible publish-stop. The
 * visit is not abandoned: it continues on the secure message thread.
 */

import { useState } from "react";
import { ShieldOff } from "lucide-react";
import { apiClient } from "@/lib/api-client";

export function ConsentWithdrawControl({
  sessionId,
  onWithdrawn,
}: {
  sessionId: string;
  /** Called after the withdrawal is recorded — the parent must drop media immediately. */
  onWithdrawn: () => void;
}) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function withdraw() {
    setBusy(true);
    setError(null);
    try {
      await apiClient.post(`/internal/v1/teleconsult/sessions/${sessionId}/consent-withdraw`, {
        reason: "Patient withdrew consent during the session",
      });
      onWithdrawn();
    } catch {
      setError("Could not record the withdrawal — you can still leave the call at any time.");
      setBusy(false);
    }
  }

  if (!confirming) {
    return (
      <button
        type="button"
        onClick={() => setConfirming(true)}
        className="inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1 text-xs font-medium text-muted-foreground hover:border-destructive/50 hover:text-destructive"
        data-testid="consent-withdraw-open"
      >
        <ShieldOff className="h-3.5 w-3.5" aria-hidden />
        Withdraw consent
      </button>
    );
  }

  return (
    <div
      className="flex flex-wrap items-center gap-2 rounded-md border border-destructive/40 bg-destructive/5 px-2.5 py-1.5"
      data-testid="consent-withdraw-confirm"
    >
      <span className="text-xs text-foreground">
        End video/audio now and continue by secure message?
      </span>
      <button
        type="button"
        disabled={busy}
        onClick={withdraw}
        className="rounded-md bg-destructive px-2.5 py-1 text-xs font-semibold text-destructive-foreground disabled:opacity-50"
      >
        {busy ? "Ending…" : "Yes, withdraw"}
      </button>
      <button
        type="button"
        disabled={busy}
        onClick={() => setConfirming(false)}
        className="rounded-md border border-border px-2.5 py-1 text-xs"
      >
        Keep going
      </button>
      {error && <span className="w-full text-[11px] text-destructive">{error}</span>}
    </div>
  );
}
