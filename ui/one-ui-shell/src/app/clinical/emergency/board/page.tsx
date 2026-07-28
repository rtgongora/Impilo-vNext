"use client";

/**
 * The emergency episode board (W19b) — every open pct.emergency_episode at this facility.
 * Route: /clinical/emergency/board
 *
 * Distinct from the ED pre-arrival board on /clinical/emergency (that reads pct's ed_visit
 * queue) — this is the episode SPINE's own board: every entry route (walk-in, ambulance,
 * in-facility deterioration, MCI, ...), not just ED-visit-originated patients.
 */

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useEmergencyEpisodeBoard, useOpenEmergencyEpisode } from "@/hooks/queries/useEmergencyEpisode";
import { useState } from "react";

const ENTRY_ROUTES = [
  "WALK_IN", "AMBULANCE", "STATUTORY_SERVICE", "INTERFACILITY_REFERRAL", "CHW_REFERRAL",
  "PRIVATE_VEHICLE", "WORKPLACE_OR_SCHOOL", "MASS_CASUALTY", "IN_FACILITY_DETERIORATION",
  "CLINIC_ESCALATION", "TELECONSULT_ESCALATION", "PUBLIC_REQUEST", "DIRECT_SPECIALIST",
  "UNKNOWN_OR_UNRESPONSIVE",
] as const;

const STATE_LABEL: Record<string, string> = {
  PRE_ARRIVAL: "Pre-arrival",
  OPEN_UNTRIAGED: "Untriaged",
  OPEN_IN_CARE: "In care",
  OPEN_AWAITING_ACCEPTANCE: "Awaiting acceptance",
};

export default function EmergencyEpisodeBoardPage() {
  const facility = useFacilityStore((s) => s.facility);
  const board = useEmergencyEpisodeBoard(facility?.id);
  const openEpisode = useOpenEmergencyEpisode();
  const [entryRoute, setEntryRoute] = useState<string>("WALK_IN");

  return (
    <AppLayout>
      <PageShell title="Emergency episode board" subtitle="Every open episode at this facility — the continuum-side spine (pct.emergency_episode)">
        <div className="mb-4 flex items-center justify-between gap-4">
          <Link href="/clinical/emergency" className="text-sm text-primary underline">← ED trackboard</Link>
          <div className="flex items-center gap-2">
            <select
              className="rounded border px-2 py-1 text-sm"
              value={entryRoute}
              onChange={(e) => setEntryRoute(e.target.value)}
              aria-label="Entry route for a new episode"
            >
              {ENTRY_ROUTES.map((r) => (
                <option key={r} value={r}>{r.replaceAll("_", " ")}</option>
              ))}
            </select>
            <button
              type="button"
              className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
              disabled={!facility?.id || openEpisode.isPending}
              onClick={() => {
                if (!facility?.id) return;
                openEpisode.mutate({ facilityId: facility.id, entryRoute });
              }}
            >
              {openEpisode.isPending ? "Opening…" : "Open episode"}
            </button>
          </div>
        </div>

        {board.isLoading && <p className="text-sm text-muted-foreground">Loading board…</p>}
        {board.isError && <p className="text-sm text-destructive">Could not load the emergency board.</p>}
        {!board.isLoading && !board.isError && (board.data?.length ?? 0) === 0 && (
          <p className="text-sm text-muted-foreground">No open episodes at this facility.</p>
        )}

        <div className="grid gap-3">
          {(board.data ?? []).map((episode) => (
            <Link
              key={String(episode.episode_id)}
              href={`/clinical/emergency/spine/${episode.episode_id}`}
              className="rounded border p-3 hover:bg-muted/50"
            >
              <div className="flex items-center justify-between">
                <span className="font-medium">{String(episode.episode_reference ?? episode.episode_id)}</span>
                <span className="rounded bg-muted px-2 py-0.5 text-xs">
                  {STATE_LABEL[String(episode.state)] ?? String(episode.state)}
                </span>
              </div>
              <div className="mt-1 flex gap-4 text-xs text-muted-foreground">
                <span>{String(episode.entry_route).replaceAll("_", " ")}</span>
                <span>{episode.subject_cpid ? "Identified" : "Unidentified"}</span>
                {episode.current_location && <span>{String(episode.current_location)}</span>}
              </div>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
