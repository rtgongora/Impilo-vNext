"use client";

/**
 * Wallet · Care journey timeline. Aggregated, read-only view of encounters / journeys from PCT
 * (via the wallet overview). The wallet links and orders events but does not own the timeline.
 */

import { useWalletOverview } from "@/hooks/queries/useWallet";

type AnyRecord = Record<string, unknown>;

function asList(v: unknown): AnyRecord[] {
  if (Array.isArray(v)) return v.filter((x): x is AnyRecord => typeof x === "object" && x !== null);
  if (v && typeof v === "object") {
    const r = v as AnyRecord;
    for (const k of ["items", "content", "data", "events"]) {
      if (Array.isArray(r[k])) return r[k] as AnyRecord[];
    }
  }
  return [];
}

export default function WalletTimelinePage() {
  const { data, isLoading, isError } = useWalletOverview();
  const card = (data?.data?.careTimeline ?? {}) as AnyRecord;
  const events = asList(card.timeline);
  const unavailable = card.unavailable === true;

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-2">
      <div>
        <h1 className="text-xl font-semibold text-foreground">Care timeline</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Your visits, encounters and care events over time.
        </p>
        <p className="mt-1 text-[10px] uppercase tracking-wide text-muted-foreground/70">
          Source: {String(card._source ?? "PCT")}
        </p>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading your timeline…</p>
      ) : isError || unavailable ? (
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          Your care timeline is temporarily unavailable.
        </div>
      ) : events.length === 0 ? (
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          No care events yet. Your visits and encounters will appear here.
        </div>
      ) : (
        <ol className="space-y-3 border-l border-border pl-4">
          {events.map((e, idx) => (
            <li key={idx} className="relative">
              <span className="absolute -left-[21px] top-1 h-2 w-2 rounded-full bg-primary" />
              <div className="rounded-lg border border-border bg-card p-3">
                <p className="text-sm font-medium text-foreground">
                  {String(e.type ?? e.encounterType ?? e.title ?? "Care event")}
                </p>
                <p className="text-xs text-muted-foreground">
                  {String(e.facilityName ?? e.facility ?? "")}{" "}
                  {String(e.date ?? e.startedAt ?? e.timestamp ?? "")}
                </p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
