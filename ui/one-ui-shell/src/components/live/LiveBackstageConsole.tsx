"use client";

/**
 * LiveBackstageConsole — the producer/host console for a stage-managed
 * live event: speaker queue (approve / deny / demote), announcements
 * composer, poll launcher, embedded moderation panel, backstage warm-up
 * room, and an honest stream-health card.
 *
 * Access is server-truth: the console only unlocks for HOST/PRODUCER tiers
 * resolved by live-service, and every action it triggers is re-authorised
 * server-side (X-Actor-ID crew check).
 */

import { useState } from "react";
import {
  BarChart2,
  Check,
  Hand,
  Megaphone,
  MonitorUp,
  Radio,
  ShieldAlert,
  UserMinus,
  X,
} from "lucide-react";
import { AdaptiveSessionRoom } from "@/components/session/AdaptiveSessionRoom";
import { LiveModerationPanel } from "@/components/live/LiveModerationPanel";
import {
  useLiveActivatePoll,
  useLiveAnnouncements,
  useLiveApproveStage,
  useLiveBackstageJoin,
  useLiveBackstageToken,
  useLiveCreatePoll,
  useLiveDemoteParticipant,
  useLiveDenyStage,
  useLiveEvent,
  useLiveMediaHealth,
  useLiveParticipant,
  useLivePostAnnouncement,
  useLiveStageRequests,
  useLiveStageRole,
} from "@/hooks/queries/useLive";

interface LiveBackstageConsoleProps {
  eventId: string;
}

const CREW_TIERS = new Set(["HOST", "PRODUCER"]);

export function LiveBackstageConsole({ eventId }: LiveBackstageConsoleProps) {
  const { participantId } = useLiveParticipant();
  const { data: event } = useLiveEvent(eventId);
  const { data: stageRole, isLoading: roleLoading } = useLiveStageRole(eventId);
  const isCrew = CREW_TIERS.has(stageRole?.tier ?? "");

  const { data: requests = [] } = useLiveStageRequests(eventId, undefined, isCrew);
  const { data: announcements = [] } = useLiveAnnouncements(eventId, isCrew);
  const { data: mediaHealth } = useLiveMediaHealth(eventId);

  const approve = useLiveApproveStage();
  const deny = useLiveDenyStage();
  const demote = useLiveDemoteParticipant();
  const postAnnouncement = useLivePostAnnouncement();
  const createPoll = useLiveCreatePoll();
  const activatePoll = useLiveActivatePoll();
  const backstageJoin = useLiveBackstageJoin();
  const backstageToken = useLiveBackstageToken(eventId, backstageJoin.isSuccess);

  const [announcementDraft, setAnnouncementDraft] = useState("");
  const [pollQuestion, setPollQuestion] = useState("");
  const [pollOptionsDraft, setPollOptionsDraft] = useState("");

  const pending = requests.filter((r) => r.status === "REQUESTED");
  const onStage = requests.filter((r) => r.status === "APPROVED");

  if (roleLoading) {
    return <p className="text-sm text-muted-foreground">Resolving your role…</p>;
  }

  if (!isCrew) {
    return (
      <div className="rounded-2xl border border-border bg-card p-5" data-testid="backstage-denied">
        <p className="inline-flex items-center gap-2 text-sm font-medium text-foreground">
          <ShieldAlert className="h-4 w-4 text-amber-600" />
          Producer access required
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          The backstage console is available to the event host and producers only. Your
          server-resolved role for this event is {stageRole?.tier ?? "AUDIENCE"}.
        </p>
      </div>
    );
  }

  async function handlePostAnnouncement() {
    const message = announcementDraft.trim();
    if (!message) return;
    await postAnnouncement.mutateAsync({ eventId, message });
    setAnnouncementDraft("");
  }

  async function handleLaunchPoll() {
    const question = pollQuestion.trim();
    const options = pollOptionsDraft
      .split("\n")
      .map((o) => o.trim())
      .filter(Boolean);
    if (!question || options.length < 2) return;
    const poll = await createPoll.mutateAsync({
      eventId,
      body: { question, options, createdBy: participantId },
    });
    await activatePoll.mutateAsync({ eventId, pollId: poll.id });
    setPollQuestion("");
    setPollOptionsDraft("");
  }

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      {/* Speaker queue */}
      <section className="rounded-2xl border border-border bg-card p-4" data-testid="speaker-queue">
        <h2 className="inline-flex items-center gap-2 text-sm font-semibold text-foreground">
          <Hand className="h-4 w-4 text-violet-600" />
          Speaker queue
        </h2>
        <ul className="mt-3 space-y-2">
          {pending.map((req) => (
            <li
              key={req.id}
              className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm"
            >
              <span className="truncate">
                {req.participantId}
                <span className="ml-2 text-xs text-muted-foreground">{req.participantType}</span>
              </span>
              <span className="flex gap-1">
                <button
                  type="button"
                  onClick={() => approve.mutate({ eventId, requestId: req.id })}
                  disabled={approve.isPending}
                  className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-2 py-1 text-xs font-medium text-white"
                  data-testid={`approve-${req.participantId}`}
                >
                  <Check className="h-3.5 w-3.5" /> Approve
                </button>
                <button
                  type="button"
                  onClick={() => deny.mutate({ eventId, requestId: req.id })}
                  disabled={deny.isPending}
                  className="inline-flex items-center gap-1 rounded-lg border border-border px-2 py-1 text-xs"
                >
                  <X className="h-3.5 w-3.5" /> Deny
                </button>
              </span>
            </li>
          ))}
          {pending.length === 0 ? (
            <li className="text-xs text-muted-foreground">No pending stage requests.</li>
          ) : null}
        </ul>

        <h3 className="mt-4 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          On stage
        </h3>
        <ul className="mt-2 space-y-2">
          {onStage.map((req) => (
            <li
              key={req.id}
              className="flex items-center justify-between gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm"
            >
              <span className="truncate">{req.participantId}</span>
              <button
                type="button"
                onClick={() => demote.mutate({ eventId, participantId: req.participantId })}
                disabled={demote.isPending}
                className="inline-flex items-center gap-1 rounded-lg border border-border bg-card px-2 py-1 text-xs"
                data-testid={`demote-${req.participantId}`}
              >
                <UserMinus className="h-3.5 w-3.5" /> Return to audience
              </button>
            </li>
          ))}
          {onStage.length === 0 ? (
            <li className="text-xs text-muted-foreground">No promoted speakers.</li>
          ) : null}
        </ul>
      </section>

      {/* Announcements */}
      <section className="rounded-2xl border border-border bg-card p-4" data-testid="announcements-composer">
        <h2 className="inline-flex items-center gap-2 text-sm font-semibold text-foreground">
          <Megaphone className="h-4 w-4 text-amber-600" />
          Announcements
        </h2>
        <textarea
          value={announcementDraft}
          onChange={(e) => setAnnouncementDraft(e.target.value)}
          placeholder="Broadcast to everyone in the room and registrants…"
          rows={3}
          className="mt-3 w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
        <button
          type="button"
          onClick={handlePostAnnouncement}
          disabled={postAnnouncement.isPending || !announcementDraft.trim()}
          className="mt-2 rounded-lg bg-violet-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60"
        >
          Publish announcement
        </button>
        <ul className="mt-3 space-y-2 max-h-40 overflow-auto">
          {announcements.map((a) => (
            <li key={a.id} className="rounded-lg bg-amber-50 border border-amber-200 px-3 py-1.5 text-xs">
              {a.message}
            </li>
          ))}
        </ul>
      </section>

      {/* Poll launcher */}
      <section className="rounded-2xl border border-border bg-card p-4" data-testid="poll-launcher">
        <h2 className="inline-flex items-center gap-2 text-sm font-semibold text-foreground">
          <BarChart2 className="h-4 w-4 text-sky-600" />
          Launch a poll
        </h2>
        <input
          value={pollQuestion}
          onChange={(e) => setPollQuestion(e.target.value)}
          placeholder="Poll question"
          className="mt-3 w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
        <textarea
          value={pollOptionsDraft}
          onChange={(e) => setPollOptionsDraft(e.target.value)}
          placeholder={"One option per line (min 2)"}
          rows={3}
          className="mt-2 w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
        <button
          type="button"
          onClick={handleLaunchPoll}
          disabled={createPoll.isPending || activatePoll.isPending}
          className="mt-2 rounded-lg bg-sky-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60"
        >
          Create &amp; activate
        </button>
      </section>

      {/* Stream health — honest placeholder */}
      <section className="rounded-2xl border border-border bg-card p-4" data-testid="stream-health">
        <h2 className="inline-flex items-center gap-2 text-sm font-semibold text-foreground">
          <MonitorUp className="h-4 w-4 text-emerald-600" />
          Stream health
        </h2>
        <dl className="mt-3 grid grid-cols-2 gap-2 text-xs">
          <dt className="text-muted-foreground">Media provider</dt>
          <dd>{mediaHealth?.providerType ?? "…"}</dd>
          <dt className="text-muted-foreground">Room health</dt>
          <dd>{mediaHealth?.mediaHealth ?? mediaHealth?.healthStatus ?? "…"}</dd>
          <dt className="text-muted-foreground">Event status</dt>
          <dd>{mediaHealth?.eventStatus ?? event?.status ?? "…"}</dd>
          <dt className="text-muted-foreground">RTMP stream-out</dt>
          <dd className="text-muted-foreground">
            Not configured in this estate (gateway flag off) — external restream targets are a
            platform seam, not yet provisioned.
          </dd>
        </dl>
      </section>

      {/* Backstage warm-up room */}
      <section className="rounded-2xl border border-border bg-card p-4 lg:col-span-2" data-testid="backstage-room">
        <div className="flex items-center justify-between gap-2">
          <h2 className="inline-flex items-center gap-2 text-sm font-semibold text-foreground">
            <Radio className="h-4 w-4 text-violet-600" />
            Backstage room
          </h2>
          {!backstageJoin.isSuccess ? (
            <button
              type="button"
              onClick={() => backstageJoin.mutate(eventId)}
              disabled={backstageJoin.isPending}
              className="rounded-lg bg-violet-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60"
              data-testid="join-backstage"
            >
              {backstageJoin.isPending ? "Provisioning…" : "Join backstage"}
            </button>
          ) : null}
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          A private linked room for the crew and approved speakers to warm up before going on the
          main stage. Promoting a speaker mints their main-room token — they keep their backstage
          seat until they switch.
        </p>
        {backstageJoin.isSuccess && backstageToken.data ? (
          <div className="mt-3 min-h-[280px]">
            <AdaptiveSessionRoom
              layout="grid"
              serverUrl={backstageToken.data.roomUrl}
              token={backstageToken.data.accessToken}
              videoEnabled
              controls={{ microphone: true, camera: true, screenShare: false, leave: true }}
              onError={() => undefined}
            />
          </div>
        ) : null}
      </section>

      {/* Moderation */}
      <div className="lg:col-span-2">
        <LiveModerationPanel eventId={eventId} />
      </div>
    </div>
  );
}
