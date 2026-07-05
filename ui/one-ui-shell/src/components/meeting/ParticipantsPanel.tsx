"use client";

import { Check, Hand, Star, StarOff, UserCheck, UserX, Users } from "lucide-react";
import type { ConversationParticipant } from "@/hooks/queries/useComms";
import {
  useLobbyDecision,
  useMeetingLobby,
  type MeetingAdmission,
} from "@/hooks/queries/useMeeting";
import { useCohostActions } from "@/hooks/queries/useMeeting";

/**
 * Meeting roster + (for hosts/cohosts) the KNOCK admit queue and cohost management.
 * The admit queue is the khuluma mirror of rtc-gateway's waiting-room; admitting drives
 * the gateway first, then the mirror.
 */
export function ParticipantsPanel({
  conversationId,
  participants,
  hostId,
  cohostIds,
  currentActorId,
  isModerator,
  raisedHands,
}: {
  conversationId: string;
  participants: ConversationParticipant[];
  hostId: string;
  cohostIds: string[];
  currentActorId: string;
  isModerator: boolean;
  raisedHands: Set<string>;
}) {
  const lobby = useMeetingLobby(conversationId, isModerator);
  const decision = useLobbyDecision(conversationId);
  const cohosts = useCohostActions(conversationId);

  const waiting = (lobby.data ?? []).filter((a) => a.status === "WAITING");
  const isHost = currentActorId === hostId;

  const roleOf = (actorId: string) =>
    actorId === hostId ? "Host" : cohostIds.includes(actorId) ? "Cohost" : "Participant";

  return (
    <div className="flex h-full flex-col gap-3 overflow-y-auto p-3" data-testid="participants-panel">
      {isModerator && waiting.length > 0 && (
        <div className="rounded-md border border-amber-500/40 bg-amber-500/10 p-2" data-testid="admit-queue">
          <p className="mb-2 flex items-center gap-1 text-xs font-semibold text-amber-200">
            <Hand className="h-3 w-3" /> Waiting to join ({waiting.length})
          </p>
          <ul className="space-y-2">
            {waiting.map((a: MeetingAdmission) => (
              <li key={a.actorId} className="flex items-center justify-between gap-2 text-xs">
                <span className="truncate">{a.displayName ?? a.actorId}</span>
                <span className="flex shrink-0 gap-1">
                  <button
                    type="button"
                    aria-label={`Admit ${a.displayName ?? a.actorId}`}
                    data-testid={`admit-${a.actorId}`}
                    className="rounded bg-emerald-600 px-2 py-1 text-white hover:bg-emerald-500"
                    onClick={() => decision.mutate({ actorId: a.actorId, admit: true })}
                    disabled={decision.isPending}
                  >
                    <UserCheck className="h-3 w-3" />
                  </button>
                  <button
                    type="button"
                    aria-label={`Deny ${a.displayName ?? a.actorId}`}
                    data-testid={`deny-${a.actorId}`}
                    className="rounded bg-red-600 px-2 py-1 text-white hover:bg-red-500"
                    onClick={() => decision.mutate({ actorId: a.actorId, admit: false, reason: "Denied by host" })}
                    disabled={decision.isPending}
                  >
                    <UserX className="h-3 w-3" />
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div>
        <p className="mb-2 flex items-center gap-1 text-xs font-semibold text-neutral-300">
          <Users className="h-3 w-3" /> Participants ({participants.filter((p) => p.active).length})
        </p>
        <ul className="space-y-1.5">
          {participants
            .filter((p) => p.active)
            .map((p) => (
              <li key={p.actorId} className="flex items-center justify-between gap-2 text-xs" data-testid={`participant-${p.actorId}`}>
                <span className="flex min-w-0 items-center gap-1.5">
                  {raisedHands.has(p.actorId) && <Hand className="h-3 w-3 shrink-0 text-amber-300" data-testid={`hand-${p.actorId}`} />}
                  <span className="truncate">{p.displayName ?? p.actorId}</span>
                  <span className="shrink-0 rounded bg-neutral-700 px-1.5 py-0.5 text-[10px] text-neutral-300">
                    {roleOf(p.actorId)}
                  </span>
                </span>
                {isHost && p.actorId !== hostId && (
                  cohostIds.includes(p.actorId) ? (
                    <button
                      type="button"
                      aria-label={`Revoke cohost ${p.actorId}`}
                      title="Revoke cohost"
                      className="text-neutral-400 hover:text-white"
                      onClick={() => void cohosts.revoke(p.actorId)}
                    >
                      <StarOff className="h-3 w-3" />
                    </button>
                  ) : (
                    <button
                      type="button"
                      aria-label={`Make ${p.actorId} cohost`}
                      title="Make cohost"
                      className="text-neutral-400 hover:text-amber-300"
                      onClick={() => void cohosts.assign(p.actorId)}
                    >
                      <Star className="h-3 w-3" />
                    </button>
                  )
                )}
                {p.actorId === currentActorId && <Check className="h-3 w-3 text-emerald-400" />}
              </li>
            ))}
        </ul>
      </div>
    </div>
  );
}
