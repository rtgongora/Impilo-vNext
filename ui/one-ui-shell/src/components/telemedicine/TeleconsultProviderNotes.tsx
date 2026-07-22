"use client";

/**
 * TM-B5 (B5-3): labelled provider-only side-channel.
 *
 * A provider-to-provider discussion thread within a teleconsult that the patient NEVER sees. Backed
 * by a structurally-separate pct column (provider_notes) — the boundary is enforced server-side, this
 * panel just surfaces it, clearly labelled so a provider is never confused about who can read it.
 */

import { useCallback, useEffect, useState } from "react";
import { Lock } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface ProviderNote {
  author?: string;
  authorName?: string;
  note?: string;
  timestamp?: string;
}

export function TeleconsultProviderNotes({ sessionId }: { sessionId: string }) {
  const [notes, setNotes] = useState<ProviderNote[]>([]);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await apiClient.get<{ data: { providerNotes?: ProviderNote[] } }>(
        `/internal/v1/teleconsult/sessions/${sessionId}/provider-notes`,
      );
      setNotes(res.data?.providerNotes ?? []);
    } catch {
      /* provider notes unavailable — leave empty */
    }
  }, [sessionId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function send() {
    const text = draft.trim();
    if (!text || busy) return;
    setBusy(true);
    try {
      await apiClient.post(`/internal/v1/teleconsult/sessions/${sessionId}/provider-note`, { note: text });
      setDraft("");
      await load();
    } catch {
      /* keep draft so the provider can retry */
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className="rounded-lg border border-violet-300 bg-violet-50/60 p-3 dark:border-violet-900 dark:bg-violet-950/30"
      data-testid="teleconsult-provider-notes"
    >
      <div className="flex items-center gap-1.5">
        <Lock className="h-3.5 w-3.5 text-violet-700 dark:text-violet-300" aria-hidden />
        <span className="text-[10px] font-semibold uppercase tracking-[0.16em] text-violet-800 dark:text-violet-300">
          Provider-only notes
        </span>
      </div>
      <p className="mt-0.5 text-[11px] text-violet-700/80 dark:text-violet-300/70">
        Not visible to the patient. Clinician-to-clinician only.
      </p>

      <ul className="mt-2 space-y-1.5" aria-label="provider notes">
        {notes.length === 0 && (
          <li className="text-[11px] italic text-muted-foreground">No provider notes yet.</li>
        )}
        {notes.map((n, i) => (
          <li key={i} className="rounded-md bg-card/70 px-2 py-1.5 text-xs">
            <span className="text-foreground">{n.note}</span>
            <span className="ml-1 text-[10px] text-muted-foreground">
              — {n.authorName || n.author || "provider"}
            </span>
          </li>
        ))}
      </ul>

      <div className="mt-2 flex gap-1.5">
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && send()}
          placeholder="Add a provider-only note…"
          className="min-w-0 flex-1 rounded-md border border-border bg-background px-2 py-1 text-xs"
        />
        <button
          type="button"
          disabled={busy || !draft.trim()}
          onClick={send}
          className="rounded-md border border-violet-400 bg-violet-100 px-2.5 py-1 text-xs font-medium text-violet-900 hover:bg-violet-200 disabled:opacity-50 dark:bg-violet-900 dark:text-violet-100"
        >
          Add
        </button>
      </div>
    </div>
  );
}
