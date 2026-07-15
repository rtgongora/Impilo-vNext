"use client";

/**
 * Consolidated trauma-episode timeline (WU5) — the whole journey for one canonical
 * trauma_episode_id: INCIDENT → DISPATCH → PREHOSPITAL → ED → RESUS → DIAGNOSTICS →
 * BLOOD → DISPOSITION, reconstructed from the daidzai read-model
 * (GET /internal/v1/daidzai/trauma-episodes/{id}). Each entry names its owning service
 * and status. No mocks — this is the resolvable spine every phase owner stamps.
 */

import { Loader2, Waypoints } from "lucide-react";
import { useTraumaEpisode, type TraumaEpisode } from "@/hooks/queries/useDaidzai";

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

const PHASE_COLOR: Record<string, string> = {
  INCIDENT: "bg-red-500",
  DISPATCH: "bg-teal-500",
  PREHOSPITAL: "bg-teal-600",
  ED: "bg-orange-500",
  RESUS: "bg-red-600",
  DIAGNOSTICS: "bg-purple-500",
  BLOOD: "bg-rose-600",
  DISPOSITION: "bg-green-600",
};

function fmt(iso: unknown): string {
  const s = str(iso);
  if (!s) return "";
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? s : d.toLocaleString();
}

export function TraumaEpisodeTimeline({
  episodeId,
  episode: episodeProp,
}: {
  episodeId?: string;
  episode?: TraumaEpisode;
}) {
  const query = useTraumaEpisode(episodeProp ? undefined : episodeId);
  const episode = episodeProp ?? query.data;

  if (!episodeProp && query.isLoading) {
    return <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading episode…</div>;
  }
  if (!episode) {
    return (
      <div className="rounded-xl border border-border bg-card p-6 text-center text-sm text-muted-foreground">
        No trauma episode found for this reference.
      </div>
    );
  }

  const timeline = episode.timeline ?? [];
  const status = str(episode.status);

  return (
    <section className="rounded-xl border border-border bg-card p-4 shadow-sm" data-testid="trauma-episode-timeline">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <Waypoints className="h-4 w-4 text-primary" /> {str(episode.episodeReference) || "Trauma episode"}
        </h2>
        <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${status === "CLOSED" ? "bg-neutral-200 text-neutral-800" : "bg-green-100 text-green-800"}`}>
          {status || "OPEN"}
        </span>
        {episode.currentPhase ? <span className="text-xs text-muted-foreground">Current: {str(episode.currentPhase)}</span> : null}
        <span className="text-xs text-muted-foreground">
          Subject: {str(episode.subjectIdentityMode) || "—"}{episode.subjectHealthId ? ` · ${str(episode.subjectHealthId)}` : ""}
        </span>
      </div>

      {timeline.length === 0 ? (
        <p className="mt-3 rounded-lg border border-dashed border-border bg-background px-3 py-4 text-center text-xs text-muted-foreground">
          No phases recorded yet on this episode.
        </p>
      ) : (
        <ol className="mt-4 space-y-3 border-l border-border pl-4">
          {timeline.map((t, i) => {
            const phase = str(t.phase);
            return (
              <li key={`${phase}-${str(t.ownerRef)}-${i}`} className="relative">
                <span className={`absolute -left-[21px] top-1 h-3 w-3 rounded-full ${PHASE_COLOR[phase] ?? "bg-neutral-400"}`} aria-hidden />
                <div className="flex flex-wrap items-baseline gap-x-2">
                  <span className="text-sm font-semibold text-foreground">{phase}</span>
                  <span className="text-xs font-medium text-muted-foreground">{str(t.status)}</span>
                  <span className="font-mono text-[11px] text-muted-foreground">{str(t.ownerService)}</span>
                  <span className="ml-auto font-mono text-[11px] text-muted-foreground">{fmt(t.occurredAt)}</span>
                </div>
                {t.eventType ? <div className="text-xs text-muted-foreground">{str(t.eventType)}</div> : null}
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
