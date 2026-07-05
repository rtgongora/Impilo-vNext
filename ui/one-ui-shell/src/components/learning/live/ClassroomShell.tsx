"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  BarChart2,
  FileText,
  GraduationCap,
  Hand,
  HelpCircle,
  Loader2,
  LogOut,
  MessageSquare,
  QrCode,
  Target,
  Users,
} from "lucide-react";
import { AdaptiveSessionRoom } from "@/components/session/AdaptiveSessionRoom";
import { LowBandwidthToggle } from "@/components/session/LowBandwidthToggle";
import {
  useLiveAttendanceLeave,
  useLiveChat,
  useLiveEvent,
  useLiveJoinRoom,
  useLiveParticipant,
  useLivePolls,
  useLivePostChat,
  useLiveRespondPoll,
  useLiveRoomToken,
  useLiveTrackMinutes,
} from "@/hooks/queries/useLive";
import { useFundoFacilitators, useFundoSession } from "@/hooks/queries/useFundoDelivery";
import { useFundoCourseStructure } from "@/hooks/queries/useFundoCatalog";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import {
  resolveClassroomRole,
  resolveLiveEventId,
  type LearningSessionRecord,
} from "@/lib/learning/live-session";
import { FacilitatorRosterPanel } from "./FacilitatorRosterPanel";
import { ClassQuizLauncherPanel } from "./ClassQuizLauncherPanel";
import { HandQueue } from "./HandQueue";
import { ObjectivesPanel } from "./ObjectivesPanel";
import { ClassResourcesPanel } from "./ClassResourcesPanel";

/**
 * ClassroomShell — the LEARNING_LIVE classroom over the existing live-event
 * media room. The scheduled learning session (learning-service) points at an
 * Impilo Live event; this shell joins THAT event with the same join/token/
 * chat/poll hooks the /live pages use (no duplicated media plumbing) and
 * renders the classroom stage layout with a facilitator or learner rail.
 */

type FacilitatorTab = "roster" | "quiz" | "hands" | "chat";
type LearnerTab = "objectives" | "resources" | "polls" | "chat";

function parsePollOptions(raw: string): string[] {
  try {
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

export function ClassroomShell({ sessionId }: { sessionId: string }) {
  const router = useRouter();
  const { data: session, isLoading: sessionLoading } = useFundoSession(sessionId);
  const { data: facilitatorsData } = useFundoFacilitators();
  const subject = useLearningSubject();
  const { user, participantId, participantType } = useLiveParticipant();

  const sessionRecord = (session ?? {}) as LearningSessionRecord;
  const liveEventId = session ? resolveLiveEventId(sessionRecord) : undefined;
  const courseId = typeof sessionRecord.courseId === "string" ? sessionRecord.courseId : undefined;

  const facilitatorDirectory =
    ((facilitatorsData?.data as { items?: Array<Record<string, unknown>> } | undefined)?.items ?? []);
  const role = useMemo(
    () =>
      session
        ? resolveClassroomRole(
            sessionRecord,
            { subjectId: subject.subjectId, providerId: user?.providerId ?? undefined },
            facilitatorDirectory
          )
        : "learner",
    // eslint-disable-next-line react-hooks/exhaustive-deps -- record identity follows `session`
    [session, subject.subjectId, user?.providerId, facilitatorDirectory]
  );
  const isFacilitator = role === "facilitator";

  // ---- Live event join flow (same hooks as the /live room) ----------------
  const { data: event } = useLiveEvent(liveEventId ?? "");
  const joinRoom = useLiveJoinRoom();
  const leaveAttendance = useLiveAttendanceLeave();
  const trackMinutes = useLiveTrackMinutes();
  const [joined, setJoined] = useState(false);
  const [watchStartedAt] = useState(() => Date.now());

  useEffect(() => {
    if (!liveEventId || !participantId || joined) return;
    let cancelled = false;
    void (async () => {
      await joinRoom.mutateAsync({
        eventId: liveEventId,
        body: {
          participantId,
          participantType,
          role: isFacilitator ? "PRESENTER" : "ATTENDEE",
        },
      });
      if (!cancelled) setJoined(true);
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- join once per classroom mount
  }, [liveEventId, participantId, isFacilitator]);

  const { data: roomToken, isLoading: tokenLoading, error: tokenError } = useLiveRoomToken(
    liveEventId ?? "",
    Boolean(liveEventId) && Boolean(joinRoom.isSuccess || event?.status === "LIVE"),
    // Classroom roles come from the session record (facilitator directory),
    // NOT from providerActivated. The LEARNING_LIVE template grants both
    // FACILITATOR and LEARNER publish rights; the default ATTENDEE→AUDIENCE
    // mapping would hand learners a subscribe-only token.
    isFacilitator ? "FACILITATOR" : "LEARNER"
  );

  const { data: chat = [] } = useLiveChat(liveEventId ?? "");
  const { data: polls = [] } = useLivePolls(liveEventId ?? "");
  const postChat = useLivePostChat();
  const respondPoll = useLiveRespondPoll();
  const { data: structureData } = useFundoCourseStructure(courseId);
  const structure = structureData?.data?.structure;

  const [lowBandwidth, setLowBandwidth] = useState(false);
  const [chatDraft, setChatDraft] = useState("");
  const [facilitatorTab, setFacilitatorTab] = useState<FacilitatorTab>("roster");
  const [learnerTab, setLearnerTab] = useState<LearnerTab>("objectives");

  const activePoll = useMemo(() => polls.find((p) => p.status === "ACTIVE") ?? polls[0], [polls]);
  const pollOptions = activePoll ? parsePollOptions(activePoll.options) : [];

  async function handleLeave() {
    if (liveEventId && participantId) {
      const elapsedMinutes = Math.max(1, Math.round((Date.now() - watchStartedAt) / 60_000));
      await trackMinutes
        .mutateAsync({
          eventId: liveEventId,
          participantId,
          liveMinutes: event?.status === "LIVE" ? elapsedMinutes : 0,
          replayMinutes: 0,
        })
        .catch(() => undefined);
      await leaveAttendance.mutateAsync({ eventId: liveEventId, participantId }).catch(() => undefined);
    }
    router.push(`/learning/sessions/${sessionId}`);
  }

  async function handleSendChat() {
    const message = chatDraft.trim();
    if (!message || !liveEventId) return;
    await postChat.mutateAsync({
      eventId: liveEventId,
      body: { participantId, participantType, message },
    });
    setChatDraft("");
  }

  if (sessionLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" /> Loading classroom…
      </div>
    );
  }

  if (!session || !liveEventId) {
    return (
      <div
        data-testid="classroom-no-live-event"
        className="rounded-lg border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground"
      >
        <p className="font-medium">This session has no linked live classroom.</p>
        <p className="mt-1">
          Only LIVE or HYBRID sessions scheduled with an Impilo Live event can be joined here.
        </p>
        <Link href={`/learning/sessions/${sessionId}`} className="mt-2 inline-block text-teal-700 underline">
          Back to session details
        </Link>
      </div>
    );
  }

  const chatPanel = (
    <div className="space-y-3" data-testid="classroom-chat">
      <ul className="max-h-64 space-y-2 overflow-auto">
        {chat.map((msg) => (
          <li key={msg.id} className="rounded-lg bg-background px-2 py-1.5 text-xs">
            <span className="text-muted-foreground">{msg.participantType}: </span>
            {msg.message}
          </li>
        ))}
        {chat.length === 0 ? <li className="text-xs text-muted-foreground">No messages yet.</li> : null}
      </ul>
      {event?.chatEnabled !== false ? (
        <div className="flex gap-2">
          <input
            value={chatDraft}
            onChange={(e) => setChatDraft(e.target.value)}
            placeholder="Message the class…"
            className="flex-1 rounded-lg border border-border px-2 py-1.5 text-sm"
            onKeyDown={(e) => {
              if (e.key === "Enter") void handleSendChat();
            }}
          />
          <button
            type="button"
            onClick={() => void handleSendChat()}
            disabled={postChat.isPending}
            className="rounded-lg bg-violet-600 px-3 py-1.5 text-sm text-white"
          >
            Send
          </button>
        </div>
      ) : (
        <p className="text-xs text-warning-foreground">Chat is disabled for this class.</p>
      )}
    </div>
  );

  const facilitatorTabs: Array<{ id: FacilitatorTab; label: string; icon: typeof Users }> = [
    { id: "roster", label: "Roster", icon: Users },
    { id: "quiz", label: "Quiz", icon: BarChart2 },
    { id: "hands", label: "Hands", icon: Hand },
    { id: "chat", label: "Chat", icon: MessageSquare },
  ];
  const learnerTabs: Array<{ id: LearnerTab; label: string; icon: typeof Target }> = [
    { id: "objectives", label: "Objectives", icon: Target },
    { id: "resources", label: "Resources", icon: FileText },
    { id: "polls", label: "Polls", icon: HelpCircle },
    { id: "chat", label: "Chat", icon: MessageSquare },
  ];

  return (
    <div className="flex min-h-[70vh] flex-col gap-4 lg:flex-row" data-testid="classroom-shell">
      <div className="flex min-h-[360px] flex-1 flex-col overflow-hidden rounded-2xl border border-border bg-neutral-900">
        <div className="flex items-center justify-between gap-2 px-3 py-2 text-sm text-muted-foreground">
          <div className="min-w-0">
            <p className="flex items-center gap-1.5 truncate font-medium text-white">
              <GraduationCap className="h-4 w-4 flex-shrink-0" />
              {String(sessionRecord.title ?? event?.title ?? "Classroom")}
            </p>
            <p className="text-xs text-muted-foreground">
              <span data-testid="classroom-role" className="uppercase">
                {role}
              </span>
              {" · "}
              {event?.status ?? "…"}
            </p>
          </div>
          <div className="flex flex-shrink-0 items-center gap-2">
            <LowBandwidthToggle audioOnly={lowBandwidth} onAudioOnlyChange={setLowBandwidth} />
            <Link
              href={`/learning/sessions/${sessionId}/checkin`}
              data-testid="classroom-checkin-shortcut"
              className="inline-flex items-center gap-1 rounded-lg border border-gray-600 px-2 py-1 text-xs text-muted-foreground hover:text-white"
            >
              <QrCode className="h-3.5 w-3.5" /> Check-in
            </Link>
            <button
              type="button"
              onClick={() => void handleLeave()}
              className="inline-flex items-center gap-1 rounded-lg bg-red-700 px-2.5 py-1 text-xs font-medium text-white hover:bg-red-800"
            >
              <LogOut className="h-3.5 w-3.5" /> Leave
            </button>
          </div>
        </div>

        <div className="min-h-[280px] flex-1">
          {joinRoom.isPending || tokenLoading ? (
            <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" /> Connecting to the governed classroom…
            </div>
          ) : tokenError || !roomToken ? (
            <div className="flex h-full flex-col items-center justify-center gap-2 px-4 text-center text-sm text-muted-foreground">
              <p>Classroom media is not available yet — the facilitator may not have started the room.</p>
              <Link href={`/learning/sessions/${sessionId}`} className="text-violet-300 underline">
                Back to session details
              </Link>
            </div>
          ) : (
            <AdaptiveSessionRoom
              layout="classroom"
              serverUrl={roomToken.roomUrl}
              token={roomToken.accessToken}
              videoEnabled={!lowBandwidth}
              audioOnly={lowBandwidth}
              onAudioOnlyChange={setLowBandwidth}
              controls={{
                microphone: true,
                camera: true,
                screenShare: isFacilitator,
                leave: true,
              }}
              onError={() => undefined}
            />
          )}
        </div>
      </div>

      <aside className="flex w-full flex-col overflow-hidden rounded-2xl border border-border bg-card lg:w-96">
        <div className="flex border-b border-border">
          {(isFacilitator ? facilitatorTabs : learnerTabs).map(({ id, label, icon: Icon }) => {
            const active = isFacilitator ? facilitatorTab === id : learnerTab === id;
            return (
              <button
                key={id}
                type="button"
                onClick={() =>
                  isFacilitator
                    ? setFacilitatorTab(id as FacilitatorTab)
                    : setLearnerTab(id as LearnerTab)
                }
                className={`inline-flex flex-1 items-center justify-center gap-1 px-2 py-2.5 text-xs font-medium ${
                  active
                    ? "border-b-2 border-violet-600 bg-violet-50 text-violet-700"
                    : "text-muted-foreground hover:bg-background"
                }`}
              >
                <Icon className="h-3.5 w-3.5" />
                {label}
              </button>
            );
          })}
        </div>

        <div className="flex-1 overflow-auto p-3 text-sm" data-testid={isFacilitator ? "facilitator-rail" : "learner-rail"}>
          {isFacilitator ? (
            <>
              {facilitatorTab === "roster" ? <FacilitatorRosterPanel sessionId={sessionId} /> : null}
              {facilitatorTab === "quiz" ? (
                <ClassQuizLauncherPanel eventId={liveEventId} participantId={participantId} />
              ) : null}
              {facilitatorTab === "hands" ? <HandQueue sessionRef={sessionId} /> : null}
              {facilitatorTab === "chat" ? chatPanel : null}
            </>
          ) : (
            <>
              {learnerTab === "objectives" ? (
                <ObjectivesPanel
                  sessionTitle={typeof sessionRecord.title === "string" ? sessionRecord.title : undefined}
                  sessionType={typeof sessionRecord.sessionType === "string" ? sessionRecord.sessionType : undefined}
                  eventDescription={event?.description}
                  courseTitle={structure?.title}
                  courseDescription={structure?.description ?? null}
                />
              ) : null}
              {learnerTab === "resources" ? (
                <ClassResourcesPanel eventId={liveEventId} courseId={courseId} />
              ) : null}
              {learnerTab === "polls" ? (
                <div className="space-y-3" data-testid="learner-polls">
                  {activePoll ? (
                    <>
                      <p className="font-medium text-foreground">{activePoll.question}</p>
                      <div className="space-y-2">
                        {pollOptions.map((option) => (
                          <button
                            key={option}
                            type="button"
                            onClick={() =>
                              respondPoll.mutate({
                                eventId: liveEventId,
                                pollId: activePoll.id,
                                body: { participantId, selectedOption: option },
                              })
                            }
                            disabled={respondPoll.isPending}
                            className="w-full rounded-lg border border-border px-3 py-2 text-left text-sm hover:border-violet-300 hover:bg-violet-50"
                          >
                            {option}
                          </button>
                        ))}
                      </div>
                    </>
                  ) : (
                    <p className="text-xs text-muted-foreground">No active quiz or poll.</p>
                  )}
                </div>
              ) : null}
              {learnerTab === "chat" ? chatPanel : null}
              <div className="mt-4 border-t border-border pt-3">
                {/* HONEST SEAM: the Khuluma realtime transport is subscribe-only in the
                    web shell — there is no client publish path for raise-hand yet, so
                    the button stays disabled instead of faking an emit. */}
                <button
                  type="button"
                  disabled
                  data-testid="raise-hand-button"
                  title="Raise hand is awaiting the realtime publish channel."
                  className="inline-flex w-full cursor-not-allowed items-center justify-center gap-1.5 rounded-lg border border-border px-3 py-2 text-xs text-muted-foreground opacity-60"
                >
                  <Hand className="h-3.5 w-3.5" /> Raise hand — awaiting realtime channel
                </button>
              </div>
            </>
          )}
        </div>
      </aside>
    </div>
  );
}
