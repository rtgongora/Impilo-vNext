"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { Disc, ListTodo, MessageSquare, PhoneOff, Users } from "lucide-react";
import { AdaptiveSessionRoom } from "@/components/session/AdaptiveSessionRoom";
import { useSessionRoomState } from "@/components/session/useSessionRoomState";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConversation } from "@/hooks/queries/useComms";
import {
  meetingKeys,
  useMeetingDetail,
  useMeetingJoin,
} from "@/hooks/queries/useMeeting";
import { subscribeKhulumaRealtime, type RealtimeFrame } from "@/lib/realtime/khuluma-socket";
import { liveApi } from "@/lib/live";
import { MeetingLobby } from "./MeetingLobby";
import { ParticipantsPanel } from "./ParticipantsPanel";
import { NotesPanel } from "./NotesPanel";
import { MeetingChatPanel } from "./MeetingChatPanel";
import { ReactionsBar } from "./ReactionsBar";
import { InviteLinkButton } from "./InviteLinkButton";
import { RecordingConsentToast } from "./RecordingConsentToast";

/** MEETING template: who may screen-share (contracts/schemas/session-templates/meeting.json). */
const SCREEN_SHARE_ROLES = new Set(["HOST", "COHOST", "PARTICIPANT"]);

type PanelTab = "participants" | "chat" | "notes";

interface FloatingReaction {
  id: string;
  emoji: string;
  actorId: string;
}

/**
 * MeetingRoomShell — the /meet room: KNOCK lobby (un-admitted members wait while the join
 * query re-knocks), then the ONE governed media surface (AdaptiveSessionRoom, grid layout)
 * with participants/admit-queue, conversation chat, agenda/notes/action items, raise-hand +
 * reactions, invite links and the recording notice. Realtime meeting.* events ride the shared
 * khuluma socket; everything stays correct via polling without it.
 */
export function MeetingRoomShell({ conversationId }: { conversationId: string }) {
  const router = useRouter();
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const currentActorId = user?.healthId ?? user?.id ?? "";

  const conversation = useConversation(conversationId);
  const detail = useMeetingDetail(conversationId, { refetchInterval: 15_000 });
  const join = useMeetingJoin(conversationId);

  const [tab, setTab] = useState<PanelTab>("participants");
  const [panelOpen, setPanelOpen] = useState(true);
  const [handRaised, setHandRaised] = useState(false);
  const [raisedHands, setRaisedHands] = useState<Set<string>>(new Set());
  const [reactions, setReactions] = useState<FloatingReaction[]>([]);

  const isModerator =
    !!detail.data &&
    (detail.data.hostId === currentActorId || detail.data.cohostIds.includes(currentActorId));

  // Realtime meeting.* events on the shared khuluma socket (enhancement — polling still works).
  useEffect(() => {
    const unsubscribe = subscribeKhulumaRealtime((frame: RealtimeFrame) => {
      if (String(frame.conversation_id ?? "") !== conversationId) return;
      const eventType = String(frame.event_type ?? "");
      if (eventType === "meeting.hand.raised" || eventType === "meeting.hand.lowered") {
        const actor = String(frame.actor_id ?? "");
        setRaisedHands((prev) => {
          const next = new Set(prev);
          if (eventType === "meeting.hand.raised") next.add(actor);
          else next.delete(actor);
          return next;
        });
      } else if (eventType === "meeting.reaction") {
        const reaction: FloatingReaction = {
          id: crypto.randomUUID(),
          emoji: String(frame.reaction ?? "👏"),
          actorId: String(frame.actor_id ?? ""),
        };
        setReactions((prev) => [...prev.slice(-11), reaction]);
        setTimeout(() => setReactions((prev) => prev.filter((r) => r.id !== reaction.id)), 4000);
      } else if (eventType.startsWith("meeting.admission.")) {
        void qc.invalidateQueries({ queryKey: meetingKeys().lobby(conversationId) });
        void qc.invalidateQueries({ queryKey: meetingKeys().join(conversationId) });
        void qc.invalidateQueries({ queryKey: meetingKeys().detail(conversationId) });
      } else if (eventType.startsWith("meeting.action-item.")) {
        void qc.invalidateQueries({ queryKey: meetingKeys().actionItems(conversationId) });
      } else if (eventType === "meeting.ended") {
        router.push(`/meet/${conversationId}/summary`);
      }
    });
    return unsubscribe;
  }, [conversationId, qc, router]);

  const leave = useCallback(() => {
    router.push(`/meet/${conversationId}/summary`);
  }, [router, conversationId]);

  const startRecording = useCallback(() => {
    const eventId = detail.data?.eventId;
    if (!eventId) return;
    void liveApi.startRecording(eventId).catch(() => undefined);
  }, [detail.data?.eventId]);

  const role = join.data?.role ?? "PARTICIPANT";
  const canScreenShare = SCREEN_SHARE_ROLES.has(role);
  const controls = useMemo(
    () => ({ microphone: true, camera: true, screenShare: canScreenShare, leave: false }),
    [canScreenShare],
  );

  const title = detail.data?.title ?? conversation.data?.title ?? "Meeting";

  if (!join.data || join.data.status !== "READY" || !join.data.accessToken || !join.data.roomUrl) {
    return (
      <div className="h-[70vh]">
        <MeetingLobby join={join.data} title={title} />
      </div>
    );
  }

  return (
    <div className="flex h-[80vh] gap-2" data-testid="meeting-room-shell" data-role={role}>
      <div className="min-w-0 flex-1">
        <AdaptiveSessionRoom
          serverUrl={join.data.roomUrl}
          token={join.data.accessToken}
          videoEnabled
          layout="grid"
          controls={controls}
          audience={role === "OBSERVER"}
          onDisconnected={leave}
          headerSlot={
            <div className="flex items-center justify-between gap-2 border-b border-white/10 bg-black/60 px-3 py-1.5">
              <span className="truncate text-xs font-medium" data-testid="meeting-title">
                {title}
                {detail.data?.scheduledAt ? (
                  <span className="ml-2 text-[10px] text-neutral-400">
                    scheduled {new Date(detail.data.scheduledAt).toLocaleString()}
                  </span>
                ) : null}
              </span>
              <span className="flex shrink-0 items-center gap-1">
                {isModerator && <InviteLinkButton conversationId={conversationId} />}
                {isModerator && (
                  <button
                    type="button"
                    aria-label="Start recording"
                    data-testid="start-recording"
                    className="flex items-center gap-1 rounded-md bg-neutral-800 px-2 py-1.5 text-xs text-white hover:bg-red-900"
                    onClick={startRecording}
                  >
                    <Disc className="h-3.5 w-3.5" /> Record
                  </button>
                )}
                <button
                  type="button"
                  aria-label="Leave meeting"
                  data-testid="leave-meeting"
                  className="flex items-center gap-1 rounded-md bg-red-700 px-2 py-1.5 text-xs text-white hover:bg-red-600"
                  onClick={leave}
                >
                  <PhoneOff className="h-3.5 w-3.5" /> Leave
                </button>
              </span>
            </div>
          }
          footerSlot={
            <div className="flex items-center justify-between gap-2 border-t border-white/10 bg-black/60 px-3 py-1.5">
              <ReactionsBar
                conversationId={conversationId}
                handRaised={handRaised}
                onHandToggled={(raised) => {
                  setHandRaised(raised);
                  setRaisedHands((prev) => {
                    const next = new Set(prev);
                    if (raised) next.add(currentActorId);
                    else next.delete(currentActorId);
                    return next;
                  });
                }}
              />
              <div className="flex items-center gap-1">
                <PanelTabButton icon={<Users className="h-3.5 w-3.5" />} label="People" active={panelOpen && tab === "participants"} onClick={() => togglePanel("participants")} testId="tab-participants" />
                <PanelTabButton icon={<MessageSquare className="h-3.5 w-3.5" />} label="Chat" active={panelOpen && tab === "chat"} onClick={() => togglePanel("chat")} testId="tab-chat" />
                <PanelTabButton icon={<ListTodo className="h-3.5 w-3.5" />} label="Notes" active={panelOpen && tab === "notes"} onClick={() => togglePanel("notes")} testId="tab-notes" />
              </div>
            </div>
          }
          overlaySlot={
            <>
              <RecordingToastBridge />
              <div className="pointer-events-none absolute bottom-16 left-4 flex flex-col-reverse gap-1" data-testid="floating-reactions">
                {reactions.map((r) => (
                  <span key={r.id} className="animate-bounce text-2xl drop-shadow">
                    {r.emoji}
                  </span>
                ))}
              </div>
            </>
          }
        />
      </div>

      {panelOpen && (
        <aside className="flex w-72 shrink-0 flex-col overflow-hidden rounded-md border border-neutral-800 bg-neutral-900 text-white" data-testid="meeting-side-panel">
          {tab === "participants" && detail.data && conversation.data && (
            <ParticipantsPanel
              conversationId={conversationId}
              participants={conversation.data.participants}
              hostId={detail.data.hostId}
              cohostIds={detail.data.cohostIds}
              currentActorId={currentActorId}
              isModerator={isModerator}
              raisedHands={raisedHands}
            />
          )}
          {tab === "chat" && <MeetingChatPanel conversationId={conversationId} currentActorId={currentActorId} />}
          {tab === "notes" && <NotesPanel conversationId={conversationId} />}
        </aside>
      )}
    </div>
  );

  function togglePanel(next: PanelTab) {
    if (panelOpen && tab === next) {
      setPanelOpen(false);
    } else {
      setTab(next);
      setPanelOpen(true);
    }
  }
}

/** Bridges LiveKit's isRecording (inside the room context) into the consent toast. */
function RecordingToastBridge() {
  const { isRecording } = useSessionRoomState();
  return <RecordingConsentToast recording={isRecording} />;
}

function PanelTabButton({
  icon,
  label,
  active,
  onClick,
  testId,
}: {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  onClick: () => void;
  testId: string;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={active}
      data-testid={testId}
      className={`flex items-center gap-1 rounded-md px-2 py-1.5 text-xs ${
        active ? "bg-emerald-700 text-white" : "bg-neutral-800 text-neutral-200 hover:bg-neutral-700"
      }`}
      onClick={onClick}
    >
      {icon}
      {label}
    </button>
  );
}
