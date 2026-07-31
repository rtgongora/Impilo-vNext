"use client";

/**
 * Clinician pregnancy episodes for a patient CPID — uses the existing confidential maternity lane
 * (`usePregnancyEpisodes` / `useCurrentPregnancyEpisode`) with the real CPID, not `"me"`.
 */

import { AlertTriangle, Baby, Loader2 } from "lucide-react";
import {
  useCurrentPregnancyEpisode,
  usePregnancyEpisodes,
  type PregnancyEpisodeView,
} from "@/hooks/queries/useConfidentialMaternity";
import { REPRODUCTIVE_EMPTY_HINT } from "./reproductiveWording";

export interface PregnancyEpisodesPanelProps {
  patientCpid: string;
}

function statusLabel(status: string): string {
  switch (status) {
    case "ONGOING":
      return "Ongoing";
    case "DELIVERED":
      return "Delivered";
    case "LOST":
      return "Ended in loss";
    case "TERMINATED":
      return "Ended";
    default:
      return status.replace(/_/g, " ").toLowerCase();
  }
}

function riskNotice(riskStatus?: string | null): string | null {
  if (!riskStatus || riskStatus === "NOT_ASSESSED") {
    return "Risk not yet assessed — not the same as low risk.";
  }
  return null;
}

function EpisodeCard({ episode }: { episode: PregnancyEpisodeView }) {
  const risk = riskNotice(episode.risk_status);
  return (
    <li
      className="rounded-md border border-border bg-muted/30 px-3 py-2 text-sm"
      data-testid="pregnancy-episode-item"
    >
      <p className="font-medium text-foreground">{statusLabel(episode.status)}</p>
      <p className="text-xs text-muted-foreground">
        EDD:{" "}
        {episode.estimated_delivery_date
          ? new Date(episode.estimated_delivery_date).toLocaleDateString()
          : "Not estimated"}
        {episode.dating_method ? ` · dated by ${episode.dating_method.replace(/_/g, " ").toLowerCase()}` : ""}
      </p>
      {risk ? <p className="mt-1 text-xs text-amber-800">{risk}</p> : null}
    </li>
  );
}

export function PregnancyEpisodesPanel({ patientCpid }: PregnancyEpisodesPanelProps) {
  const current = useCurrentPregnancyEpisode(patientCpid);
  const history = usePregnancyEpisodes(patientCpid);

  if (current.isLoading || history.isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground" data-testid="pregnancy-episodes-loading">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading pregnancy episodes…
      </p>
    );
  }

  if (current.isError || history.isError) {
    return (
      <div
        className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-2 text-sm text-red-900"
        data-testid="pregnancy-episodes-error"
        role="alert"
      >
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
        <p>Could not load pregnancy episodes for this patient. Try again.</p>
      </div>
    );
  }

  const ongoing = current.data;
  const past = (history.data ?? []).filter((e) => e.pregnancy_episode_id !== ongoing?.pregnancy_episode_id);

  return (
    <section
      className="mb-4 rounded-lg border border-pink-200/90 bg-card p-5"
      data-testid="pregnancy-episodes-panel"
    >
      <div className="flex items-center gap-2">
        <Baby className="h-5 w-5 text-pink-600" />
        <h2 className="text-lg font-semibold text-foreground">Pregnancy episodes</h2>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">
        Obstetric history on the confidential lane for this patient.
      </p>

      {!ongoing && past.length === 0 ? (
        <p className="mt-3 text-xs text-muted-foreground">{REPRODUCTIVE_EMPTY_HINT}</p>
      ) : (
        <ul className="mt-3 space-y-2">
          {ongoing ? <EpisodeCard episode={ongoing} /> : null}
          {past.map((e) => (
            <EpisodeCard key={e.pregnancy_episode_id} episode={e} />
          ))}
        </ul>
      )}
    </section>
  );
}
