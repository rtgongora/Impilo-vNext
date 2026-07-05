"use client";

import { useCallback, useState } from "react";
import { Hand } from "lucide-react";
import { useSessionRealtime, type SessionRealtimeEvent } from "@/hooks/useSessionRealtime";

/**
 * Facilitator hand queue — consumes `session.raise_hand` /
 * `session.hand_lowered` frames from the shared Khuluma realtime channel
 * (scoped to this learning session) and keeps a local ordered queue.
 *
 * HONEST SEAM: the web shell has no realtime publish path (the Khuluma
 * transport is subscribe-only), so "Lower" only clears the entry from this
 * facilitator's local queue — it does not push a `session.hand_lowered`
 * frame back to other participants.
 */

export interface HandQueueEntry {
  actorId: string;
  raisedAt: number;
}

export function handQueueReducer(
  queue: HandQueueEntry[],
  event: Pick<SessionRealtimeEvent, "type" | "actorId">,
  now: number = Date.now()
): HandQueueEntry[] {
  const actorId = event.actorId?.trim();
  if (!actorId) return queue;
  if (event.type === "session.raise_hand") {
    if (queue.some((entry) => entry.actorId === actorId)) return queue;
    return [...queue, { actorId, raisedAt: now }];
  }
  if (event.type === "session.hand_lowered") {
    return queue.filter((entry) => entry.actorId !== actorId);
  }
  return queue;
}

export function HandQueue({ sessionRef }: { sessionRef: string }) {
  const [queue, setQueue] = useState<HandQueueEntry[]>([]);

  const onEvent = useCallback((event: SessionRealtimeEvent) => {
    setQueue((current) => handQueueReducer(current, event));
  }, []);

  useSessionRealtime({ sessionRef, onEvent });

  return (
    <div data-testid="hand-queue" className="space-y-2">
      <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        <Hand className="h-3.5 w-3.5" /> Raised hands ({queue.length})
      </p>
      {queue.length === 0 ? (
        <p className="text-xs text-muted-foreground" data-testid="hand-queue-empty">
          No hands raised. Raise-hand frames arrive over the session realtime channel.
        </p>
      ) : (
        <ol className="space-y-1.5">
          {queue.map((entry, index) => (
            <li
              key={entry.actorId}
              data-testid="hand-queue-entry"
              className="flex items-center justify-between gap-2 rounded border border-border bg-background px-2 py-1.5 text-xs"
            >
              <span className="min-w-0 truncate">
                <span className="mr-1.5 font-mono text-muted-foreground">{index + 1}.</span>
                {entry.actorId}
              </span>
              <button
                type="button"
                onClick={() =>
                  setQueue((current) =>
                    handQueueReducer(current, { type: "session.hand_lowered", actorId: entry.actorId })
                  )
                }
                title="Clears this entry locally only — no realtime publish path exists yet."
                className="rounded border border-border px-2 py-0.5 text-[11px] text-foreground hover:bg-neutral-100"
              >
                Lower (local)
              </button>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
