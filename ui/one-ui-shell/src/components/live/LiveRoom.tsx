"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  FileText,
  Headphones,
  Loader2,
  LogOut,
  MessageSquare,
  HelpCircle,
  BarChart2,
} from "lucide-react";
import { AdaptiveSessionRoom } from "@/components/session/AdaptiveSessionRoom";
import {
  useLiveChat,
  useLiveEvent,
  useLiveJoinRoom,
  useLiveParticipant,
  useLivePolls,
  useLivePostChat,
  useLiveQuestions,
  useLiveResources,
  useLiveRespondPoll,
  useLiveRoomToken,
  useLiveSubmitQuestion,
  useLiveAttendanceLeave,
  useLiveTrackMinutes,
} from "@/hooks/queries/useLive";

interface LiveRoomProps {
  eventId: string;
}

type SideTab = "chat" | "qna" | "polls" | "resources";

function parsePollOptions(raw: string): string[] {
  try {
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

export function LiveRoom({ eventId }: LiveRoomProps) {
  const router = useRouter();
  const { participantId, participantType, role } = useLiveParticipant();
  const { data: event, isLoading: eventLoading } = useLiveEvent(eventId);
  const isBroadcast =
    event?.mode === "PUBLIC_BROADCAST" || event?.mode === "EMERGENCY_BRIEFING";
  // Presenters (activated providers) publish; everyone else in a broadcast is a viewer.
  const userCanPublish = role === "PRESENTER";
  const audience = isBroadcast && !userCanPublish;
  const joinRoom = useLiveJoinRoom();
  const leaveAttendance = useLiveAttendanceLeave();
  const trackMinutes = useLiveTrackMinutes();
  const { data: roomToken, isLoading: tokenLoading, error: tokenError } = useLiveRoomToken(
    eventId,
    Boolean(joinRoom.isSuccess || event?.status === "LIVE"),
  );

  const { data: chat = [] } = useLiveChat(eventId);
  const { data: questions = [] } = useLiveQuestions(eventId);
  const { data: polls = [] } = useLivePolls(eventId);
  const { data: resources = [] } = useLiveResources(eventId);

  const postChat = useLivePostChat();
  const submitQuestion = useLiveSubmitQuestion();
  const respondPoll = useLiveRespondPoll();

  const [tab, setTab] = useState<SideTab>("chat");
  const [lowBandwidth, setLowBandwidth] = useState(false);
  const [chatDraft, setChatDraft] = useState("");
  const [questionDraft, setQuestionDraft] = useState("");
  const [watchStartedAt] = useState(() => Date.now());
  const [joined, setJoined] = useState(false);

  useEffect(() => {
    if (!eventId || !participantId || joined) return;
    let cancelled = false;
    void liveApiJoin();
    async function liveApiJoin() {
      await joinRoom.mutateAsync({
        eventId,
        body: { participantId, participantType, role },
      });
      if (!cancelled) setJoined(true);
    }
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- join once per room mount
  }, [eventId, participantId]);

  const activePoll = useMemo(
    () => polls.find((p) => p.status === "ACTIVE") ?? polls[0],
    [polls],
  );
  const pollOptions = activePoll ? parsePollOptions(activePoll.options) : [];

  async function handleLeave() {
    const elapsedMinutes = Math.max(1, Math.round((Date.now() - watchStartedAt) / 60_000));
    await trackMinutes.mutateAsync({
      eventId,
      participantId,
      liveMinutes: event?.status === "LIVE" ? elapsedMinutes : 0,
      replayMinutes: event?.status === "ENDED" ? elapsedMinutes : 0,
    });
    await leaveAttendance.mutateAsync({ eventId, participantId });
    router.push(`/live/event/${eventId}`);
  }

  async function handleSendChat() {
    const message = chatDraft.trim();
    if (!message) return;
    await postChat.mutateAsync({
      eventId,
      body: { participantId, participantType, message },
    });
    setChatDraft("");
  }

  async function handleSubmitQuestion() {
    const questionText = questionDraft.trim();
    if (!questionText) return;
    await submitQuestion.mutateAsync({
      eventId,
      body: { participantId, participantType, questionText, anonymousAllowed: false },
    });
    setQuestionDraft("");
  }

  const tabs: Array<{ id: SideTab; label: string; icon: typeof MessageSquare }> = [
    { id: "chat", label: "Chat", icon: MessageSquare },
    { id: "qna", label: "Q&A", icon: HelpCircle },
    { id: "polls", label: "Polls", icon: BarChart2 },
    { id: "resources", label: "Resources", icon: FileText },
  ];

  return (
    <div className="flex flex-col lg:flex-row gap-4 min-h-[70vh]">
      <div className="flex-1 flex flex-col rounded-2xl border border-border bg-neutral-900 overflow-hidden min-h-[360px]">
        <div className="flex items-center justify-between gap-2 px-3 py-2 bg-neutral-900 text-muted-foreground text-sm">
          <div>
            <p className="font-medium text-white">{event?.title ?? "Live session"}</p>
            {isBroadcast ? (
              <p className="text-xs text-amber-300 mt-0.5">Public broadcast · moderated viewer mode</p>
            ) : null}
            <p className="text-xs text-muted-foreground">{event?.status ?? "…"}</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setLowBandwidth((v) => !v)}
              className={`inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs border ${
                lowBandwidth
                  ? "border-amber-400 text-warning-foreground bg-amber-950/40"
                  : "border-gray-600 text-muted-foreground"
              }`}
            >
              <Headphones className="h-3.5 w-3.5" />
              {lowBandwidth ? "Audio only" : "Video"}
            </button>
            <button
              type="button"
              onClick={handleLeave}
              className="inline-flex items-center gap-1 rounded-lg bg-red-700 px-2.5 py-1 text-xs font-medium text-white hover:bg-red-800"
            >
              <LogOut className="h-3.5 w-3.5" />
              Leave
            </button>
          </div>
        </div>

        <div className="flex-1 min-h-[280px]">
          {eventLoading || joinRoom.isPending || tokenLoading ? (
            <div className="h-full flex items-center justify-center gap-2 text-muted-foreground text-sm">
              <Loader2 className="h-5 w-5 animate-spin" />
              Connecting to governed live room…
            </div>
          ) : tokenError || !roomToken ? (
            <div className="h-full flex flex-col items-center justify-center gap-2 px-4 text-center text-muted-foreground text-sm">
              <p>Room token not available yet.</p>
              <Link href={`/live/event/${eventId}`} className="text-violet-300 underline">
                Return to event details
              </Link>
            </div>
          ) : (
            <AdaptiveSessionRoom
              layout={isBroadcast ? "stage" : "speaker"}
              audience={audience}
              serverUrl={roomToken.roomUrl}
              token={roomToken.accessToken}
              videoEnabled={!lowBandwidth}
              audioOnly={lowBandwidth}
              onAudioOnlyChange={setLowBandwidth}
              controls={{ microphone: !audience, camera: !audience, screenShare: false, leave: true }}
              onError={() => undefined}
            />
          )}
        </div>
      </div>

      <aside className="w-full lg:w-96 flex flex-col rounded-2xl border border-border bg-card overflow-hidden">
        <div className="flex border-b border-border">
          {tabs.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              className={`flex-1 inline-flex items-center justify-center gap-1 px-2 py-2.5 text-xs font-medium ${
                tab === id
                  ? "border-b-2 border-violet-600 text-violet-700 bg-violet-50"
                  : "text-muted-foreground hover:bg-background"
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {label}
            </button>
          ))}
        </div>

        <div className="flex-1 overflow-auto p-3 text-sm">
          {tab === "chat" ? (
            <div className="space-y-3">
              <ul className="space-y-2 max-h-64 overflow-auto">
                {chat.map((msg) => (
                  <li key={msg.id} className="rounded-lg bg-background px-2 py-1.5 text-xs">
                    <span className="text-muted-foreground">{msg.participantType}: </span>
                    {msg.message}
                  </li>
                ))}
                {chat.length === 0 ? (
                  <li className="text-xs text-muted-foreground">No messages yet.</li>
                ) : null}
              </ul>
              {event?.chatEnabled !== false ? (
                <div className="flex gap-2">
                  <input
                    value={chatDraft}
                    onChange={(e) => setChatDraft(e.target.value)}
                    placeholder="Type a message…"
                    className="flex-1 rounded-lg border border-border px-2 py-1.5 text-sm"
                    onKeyDown={(e) => {
                      if (e.key === "Enter") void handleSendChat();
                    }}
                  />
                  <button
                    type="button"
                    onClick={handleSendChat}
                    disabled={postChat.isPending}
                    className="rounded-lg bg-violet-600 px-3 py-1.5 text-sm text-white"
                  >
                    Send
                  </button>
                </div>
              ) : (
                <p className="text-xs text-warning-foreground">Chat is disabled for this event.</p>
              )}
            </div>
          ) : null}

          {tab === "qna" ? (
            <div className="space-y-3">
              <ul className="space-y-2 max-h-64 overflow-auto">
                {questions.map((q) => (
                  <li key={q.id} className="rounded-lg bg-background px-2 py-1.5 text-xs">
                    <p>{q.questionText}</p>
                    <p className="text-muted-foreground mt-1">{q.upvotes} upvotes · {q.status}</p>
                  </li>
                ))}
                {questions.length === 0 ? (
                  <li className="text-xs text-muted-foreground">No questions yet.</li>
                ) : null}
              </ul>
              {event?.qnaEnabled !== false ? (
                <div className="flex gap-2">
                  <input
                    value={questionDraft}
                    onChange={(e) => setQuestionDraft(e.target.value)}
                    placeholder="Ask a question…"
                    className="flex-1 rounded-lg border border-border px-2 py-1.5 text-sm"
                  />
                  <button
                    type="button"
                    onClick={handleSubmitQuestion}
                    disabled={submitQuestion.isPending}
                    className="rounded-lg bg-violet-600 px-3 py-1.5 text-sm text-white"
                  >
                    Ask
                  </button>
                </div>
              ) : (
                <p className="text-xs text-warning-foreground">Q&amp;A is disabled for this event.</p>
              )}
            </div>
          ) : null}

          {tab === "polls" ? (
            <div className="space-y-3">
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
                            eventId,
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
                <p className="text-xs text-muted-foreground">No active polls.</p>
              )}
            </div>
          ) : null}

          {tab === "resources" ? (
            <ul className="space-y-2">
              {resources.map((res) => (
                <li key={res.id} className="rounded-lg border border-border p-2 text-xs">
                  <p className="font-medium text-foreground">{res.title}</p>
                  <p className="text-muted-foreground">{res.resourceType}</p>
                  {res.fileId ? (
                    <p className="text-violet-700 mt-1">File: {res.fileId}</p>
                  ) : null}
                </li>
              ))}
              {resources.length === 0 ? (
                <li className="text-xs text-muted-foreground">No shared resources yet.</li>
              ) : null}
            </ul>
          ) : null}
        </div>
      </aside>
    </div>
  );
}
