"use client";

/**
 * Unknown-patient temp-identity mint + in-episode VITO reconcile (WU5, trauma-scoped).
 *
 * Mint issues a PROVISIONAL CPID for an unidentified trauma patient (stamp across the
 * episode). Reconcile merges that provisional into a confirmed survivor via VITO
 * MergeService (POST /internal/v1/trauma/identity/merge) — the merge emits
 * vito.merge.executed, which fans out the repoint to every trauma anchor. The survivor
 * is picked by name from the client registry (no raw UUID entry). No mocks.
 */

import { useState } from "react";
import { CheckCircle2, Loader2, Search, UserPlus } from "lucide-react";
import {
  useMintProvisionalIdentity,
  useReconcileTraumaIdentity,
} from "@/hooks/queries/useDaidzai";
import { useClientRegistryClients } from "@/hooks/queries/useClientRegistry";

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

const REASONS = ["IDENTIFIED", "DUPLICATE", "CORRECTION"] as const;

export function TraumaIdentityReconcile({ subjectIdentityMode }: { subjectIdentityMode?: string }) {
  const mint = useMintProvisionalIdentity();
  const reconcile = useReconcileTraumaIdentity();

  const [sex, setSex] = useState("UNKNOWN");
  const [descriptor, setDescriptor] = useState("");
  const [provisionalCrid, setProvisionalCrid] = useState("");
  const [reason, setReason] = useState<string>("IDENTIFIED");

  const [query, setQuery] = useState("");
  const [survivor, setSurvivor] = useState<{ crid: string; label: string } | null>(null);
  const search = useClientRegistryClients({ query: query.length >= 2 ? query : undefined, page: 0, size: 8 });
  const results = search.data?.data.items ?? [];

  const minted = mint.data ?? null;
  const mergeResult = reconcile.data ?? null;

  return (
    <section className="rounded-xl border border-border bg-card p-4 shadow-sm" data-testid="trauma-identity-reconcile">
      <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <UserPlus className="h-4 w-4 text-primary" /> Unknown patient identity
      </h3>
      <p className="mt-1 text-xs text-muted-foreground">
        {subjectIdentityMode ? `Episode subject mode: ${subjectIdentityMode}. ` : ""}
        Mint a provisional identity for an unidentified patient, then reconcile it to the confirmed person once known.
      </p>

      {/* Mint */}
      <div className="mt-3 rounded-lg border border-border bg-background p-3">
        <div className="text-xs font-semibold text-foreground">1 · Mint provisional</div>
        <div className="mt-2 flex flex-wrap items-end gap-2">
          <label className="text-xs font-medium text-muted-foreground">
            Estimated sex
            <select value={sex} onChange={(e) => setSex(e.target.value)} className="ml-1 rounded border px-2 py-1 text-sm">
              {["UNKNOWN", "M", "F"].map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          <input value={descriptor} onChange={(e) => setDescriptor(e.target.value)} placeholder="Scene descriptor (e.g. adult male RTC)" className="min-w-[200px] flex-1 rounded border px-2 py-1 text-sm" />
          <button
            type="button"
            disabled={mint.isPending}
            onClick={() => mint.mutate({ estimated_sex: sex, descriptor: descriptor.trim() || undefined }, { onSuccess: (d) => setProvisionalCrid(str(d.crid)) })}
            className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
          >
            {mint.isPending ? "Minting…" : "Mint"}
          </button>
        </div>
        {minted && (
          <div className="mt-2 rounded bg-success-soft px-2 py-1.5 text-xs text-primary-hover">
            Provisional minted — Health ID <code className="font-mono">{str(minted.health_id)}</code> · CRID <code className="font-mono">{str(minted.crid)}</code>. Stamp this across the episode.
          </div>
        )}
      </div>

      {/* Reconcile */}
      <div className="mt-3 rounded-lg border border-border bg-background p-3">
        <div className="text-xs font-semibold text-foreground">2 · Reconcile to confirmed person</div>
        <div className="mt-2 space-y-2">
          <input
            value={provisionalCrid}
            onChange={(e) => setProvisionalCrid(e.target.value)}
            placeholder="Provisional CRID (from mint)"
            className="w-full rounded border px-2 py-1 font-mono text-sm"
          />
          <div className="relative">
            <div className="flex items-center gap-1 rounded border px-2 py-1">
              <Search className="h-3.5 w-3.5 text-muted-foreground" />
              <input value={query} onChange={(e) => { setQuery(e.target.value); setSurvivor(null); }} placeholder="Search confirmed patient by name / Impilo ID" className="w-full text-sm outline-none" />
            </div>
            {query.length >= 2 && !survivor && (
              <ul className="mt-1 max-h-48 overflow-auto rounded-lg border border-border bg-card text-sm shadow">
                {search.isLoading ? (
                  <li className="px-3 py-2 text-muted-foreground"><Loader2 className="mr-1 inline h-3 w-3 animate-spin" /> Searching…</li>
                ) : results.length === 0 ? (
                  <li className="px-3 py-2 text-muted-foreground">No matches.</li>
                ) : (
                  results.map((c) => (
                    <li key={str(c.crid)}>
                      <button
                        type="button"
                        onClick={() => setSurvivor({ crid: str(c.crid), label: str(c.displayName) })}
                        className="flex w-full items-center justify-between px-3 py-2 text-left hover:bg-background"
                      >
                        <span>{str(c.displayName) || "Unnamed"}</span>
                        <span className="font-mono text-[11px] text-muted-foreground">{str(c.crid).slice(0, 10)}</span>
                      </button>
                    </li>
                  ))
                )}
              </ul>
            )}
          </div>
          {survivor && (
            <div className="flex items-center justify-between rounded bg-primary/5 px-2 py-1.5 text-xs">
              <span>Survivor: <strong>{survivor.label || survivor.crid}</strong></span>
              <button type="button" onClick={() => setSurvivor(null)} className="text-primary underline">change</button>
            </div>
          )}
          <div className="flex flex-wrap items-center gap-2">
            <label className="text-xs font-medium text-muted-foreground">
              Reason
              <select value={reason} onChange={(e) => setReason(e.target.value)} className="ml-1 rounded border px-2 py-1 text-sm">
                {REASONS.map((r) => <option key={r} value={r}>{r}</option>)}
              </select>
            </label>
            <button
              type="button"
              disabled={reconcile.isPending || !provisionalCrid.trim() || !survivor}
              onClick={() => survivor && reconcile.mutate({ survivor_crid: survivor.crid, merged_crids: [provisionalCrid.trim()], reason })}
              className="rounded-lg bg-neutral-900 px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-50"
            >
              {reconcile.isPending ? "Reconciling…" : "Reconcile identity"}
            </button>
          </div>
          {mergeResult && (
            <div className="flex items-center gap-1.5 rounded bg-success-soft px-2 py-1.5 text-xs text-primary-hover">
              <CheckCircle2 className="h-3.5 w-3.5" /> Reconciled — every trauma link repoints to the survivor via the merge event.
            </div>
          )}
          {reconcile.isError && <p className="text-xs text-danger">Reconcile failed — check the CRIDs and retry.</p>}
        </div>
      </div>
    </section>
  );
}
