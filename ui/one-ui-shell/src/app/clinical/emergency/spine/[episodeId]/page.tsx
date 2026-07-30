"use client";

/**
 * The emergency episode spine detail (W19b) — one pct.emergency_episode: its FSM state, location,
 * and THE ACCEPTANCE HANDSHAKE. Route: /clinical/emergency/spine/{episodeId}
 *
 * The handshake invariant, made visible: requesting a handover moves the episode to
 * OPEN_AWAITING_ACCEPTANCE and nothing more. It closes ONLY when the accepting party writes back
 * with their own record id — never on the request, never on a timeout. This page never lets a
 * "request" button read as "done".
 */

import Link from "next/link";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useEmergencyEpisode,
  useEmergencyEpisodeActions,
  useEmergencyHandoverActions,
  useEmergencyHandoverHistory,
} from "@/hooks/queries/useEmergencyEpisode";

const HANDOVER_TARGET_TYPES = ["ADMISSION", "THEATRE", "MENTAL_HEALTH", "INTERFACILITY_TRANSFER", "OTHER_SERVICE"] as const;

const STATE_LABEL: Record<string, string> = {
  PRE_ARRIVAL: "Pre-arrival",
  OPEN_UNTRIAGED: "Untriaged",
  OPEN_IN_CARE: "In care",
  OPEN_AWAITING_ACCEPTANCE: "Awaiting acceptance",
  CLOSED_HANDED_OVER: "Closed — handed over",
  CLOSED_DISCHARGED: "Closed — discharged",
  CLOSED_DIED: "Closed — died",
};

export default function EmergencyEpisodeSpinePage({ params }: { params: { episodeId: string } }) {
  const { user } = useAuthStore();
  const episode = useEmergencyEpisode(params.episodeId);
  const handovers = useEmergencyHandoverHistory(params.episodeId);
  const actions = useEmergencyEpisodeActions(params.episodeId);

  const [targetType, setTargetType] = useState<string>("ADMISSION");
  const pendingHandover = handovers.data?.find((h) => h.status === "PENDING");

  return (
    <AppLayout>
      <PageShell
        title={episode.data ? String(episode.data.episode_reference ?? params.episodeId) : "Emergency episode"}
        subtitle="pct.emergency_episode — the continuum-side spine"
      >
        <div className="mb-4 flex flex-wrap justify-end gap-x-4 gap-y-1 text-sm">
          <Link href={`/clinical/emergency/spine/${params.episodeId}/observation`} className="text-primary underline">
            Observation stay
          </Link>
          <Link href={`/clinical/emergency/spine/${params.episodeId}/disposition`} className="text-primary underline">
            Disposition
          </Link>
          <Link href="/clinical/emergency/board" className="text-primary underline">← Emergency board</Link>
        </div>

        {episode.isLoading && <p className="text-sm text-muted-foreground">Loading episode…</p>}
        {episode.isError && <p className="text-sm text-destructive">Could not load this episode.</p>}

        {episode.data && (
          <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
            <div className="space-y-4">
              <div className="rounded border p-4">
                <div className="flex items-center justify-between">
                  <span className="rounded bg-muted px-2 py-1 text-sm font-medium">
                    {STATE_LABEL[String(episode.data.state)] ?? String(episode.data.state)}
                  </span>
                  {episode.data.sensitive && (
                    <span className="rounded bg-destructive/10 px-2 py-1 text-xs text-destructive">Sensitive</span>
                  )}
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
                  <dt className="text-muted-foreground">Entry route</dt>
                  <dd>{String(episode.data.entry_route ?? "—").replaceAll("_", " ")}</dd>
                  <dt className="text-muted-foreground">Identity</dt>
                  <dd>{episode.data.subject_cpid ? "Identified" : String(episode.data.identity_mode ?? "Unknown")}</dd>
                  <dt className="text-muted-foreground">Location</dt>
                  <dd>{String(episode.data.current_location ?? "—")}</dd>
                  <dt className="text-muted-foreground">Journey</dt>
                  <dd>{episode.data.journey_id ? String(episode.data.journey_id) : "Not yet anchored"}</dd>
                </dl>
              </div>

              {/* THE ACCEPTANCE HANDSHAKE */}
              <div className="rounded border p-4">
                <h3 className="mb-2 text-sm font-semibold">Acceptance handshake</h3>
                {pendingHandover ? (
                  <div className="space-y-2">
                    <p className="text-sm">
                      Handover requested to <strong>{String(pendingHandover.target_type)}</strong> — awaiting
                      acceptance. Requesting does not close this episode; only the accepting party&apos;s own write does.
                    </p>
                    <AcceptDeclineForm handoverId={String(pendingHandover.handover_id)} episodeId={params.episodeId} />
                  </div>
                ) : (
                  <div className="flex items-center gap-2">
                    <select
                      className="rounded border px-2 py-1 text-sm"
                      value={targetType}
                      onChange={(e) => setTargetType(e.target.value)}
                      aria-label="Handover target type"
                    >
                      {HANDOVER_TARGET_TYPES.map((t) => (
                        <option key={t} value={t}>{t.replaceAll("_", " ")}</option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
                      disabled={episode.data.state !== "OPEN_IN_CARE" || actions.requestHandover.isPending}
                      onClick={() =>
                        actions.requestHandover.mutate({
                          targetType,
                          requestedBy: user?.displayName ?? "unknown",
                        })
                      }
                    >
                      {actions.requestHandover.isPending ? "Requesting…" : "Request handover"}
                    </button>
                  </div>
                )}
              </div>

              <div className="rounded border p-4">
                <h3 className="mb-2 text-sm font-semibold">Handover history</h3>
                {(handovers.data ?? []).length === 0 && (
                  <p className="text-sm text-muted-foreground">No handovers requested yet.</p>
                )}
                <ul className="space-y-1 text-sm">
                  {(handovers.data ?? []).map((h) => (
                    <li key={String(h.handover_id)} className="flex justify-between">
                      <span>{String(h.target_type)}</span>
                      <span className="text-muted-foreground">{String(h.status)}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>

            <div className="space-y-4">
              <div className="rounded border p-4 text-sm text-muted-foreground">
                Confirming location resets the LOCATION_UNKNOWN alert clock — do this whenever the
                patient physically moves (e.g. to imaging).
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

/** The one write that closes the episode — carries the accepting party's OWN record id. */
function AcceptDeclineForm({ handoverId, episodeId }: { handoverId: string; episodeId: string }) {
  const { user } = useAuthStore();
  const handoverActions = useEmergencyHandoverActions(handoverId, episodeId);
  const [acceptingRef, setAcceptingRef] = useState("");
  const [declineReason, setDeclineReason] = useState("");

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input
          className="flex-1 rounded border px-2 py-1 text-sm"
          placeholder="Your own record id (e.g. admission id)"
          value={acceptingRef}
          onChange={(e) => setAcceptingRef(e.target.value)}
        />
        <button
          type="button"
          className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
          disabled={!acceptingRef.trim() || handoverActions.accept.isPending}
          onClick={() =>
            handoverActions.accept.mutate({ acceptedBy: user?.displayName ?? "unknown", acceptingRef })
          }
        >
          {handoverActions.accept.isPending ? "Accepting…" : "Accept"}
        </button>
      </div>
      <div className="flex gap-2">
        <input
          className="flex-1 rounded border px-2 py-1 text-sm"
          placeholder="Decline reason"
          value={declineReason}
          onChange={(e) => setDeclineReason(e.target.value)}
        />
        <button
          type="button"
          className="rounded border px-3 py-1 text-sm disabled:opacity-50"
          disabled={!declineReason.trim() || handoverActions.decline.isPending}
          onClick={() =>
            handoverActions.decline.mutate({ declinedBy: user?.displayName ?? "unknown", reason: declineReason })
          }
        >
          {handoverActions.decline.isPending ? "Declining…" : "Decline"}
        </button>
      </div>
    </div>
  );
}
