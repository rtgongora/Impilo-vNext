"use client";

/**
 * LiveAudienceEngagementRail — the side rail of a live room: chat, Q&A,
 * polls, resources, pinned announcements, and (for the audience of a
 * stage-managed broadcast) the request-stage flow.
 *
 * Extracted from LiveRoom so the audience and stage variants compose the
 * same rail. Role truth is server-resolved (useLiveStageRole) — the rail
 * only *renders* what the backend granted.
 */

import { useMemo, useState } from "react";
import {
  BarChart2,
  FileText,
  Hand,
  HelpCircle,
  Megaphone,
  MessageSquare,
} from "lucide-react";
import type { LiveEvent, LiveStageRole } from "@/lib/live";
import {
  useLiveAnnouncements,
  useLiveChat,
  useLiveParticipant,
  useLivePolls,
  useLivePostChat,
  useLiveQuestions,
  useLiveRequestStage,
  useLiveResources,
  useLiveRespondPoll,
  useLiveSubmitQuestion,
} from "@/hooks/queries/useLive";

type SideTab = "chat" | "qna" | "polls" | "resources";

function parsePollOptions(raw: string): string[] {
  try {
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

export interface LiveAudienceEngagementRailProps {
  eventId: string;
  event?: LiveEvent;
  /** Server-resolved stage role; enables the request-stage flow for the audience. */
  stageRole?: LiveStageRole;
  /** Hide the request-stage affordance (e.g. stage variant / non-broadcast modes). */
  showStageRequest?: boolean;
}

export function LiveAudienceEngagementRail({
  eventId,
  event,
  stageRole,
  showStageRequest = true,
}: LiveAudienceEngagementRailProps) {
  const { participantId, participantType } = useLiveParticipant();
  const { data: chat = [] } = useLiveChat(eventId);
  const { data: questions = [] } = useLiveQuestions(eventId);
  const { data: polls = [] } = useLivePolls(eventId);
  const { data: resources = [] } = useLiveResources(eventId);
  const { data: announcements = [] } = useLiveAnnouncements(eventId);

  const postChat = useLivePostChat();
  const submitQuestion = useLiveSubmitQuestion();
  const respondPoll = useLiveRespondPoll();
  const requestStage = useLiveRequestStage();

  const [tab, setTab] = useState<SideTab>("chat");
  const [chatDraft, setChatDraft] = useState("");
  const [questionDraft, setQuestionDraft] = useState("");

  const activePoll = useMemo(
    () => polls.find((p) => p.status === "ACTIVE") ?? polls[0],
    [polls],
  );
  const pollOptions = activePoll ? parsePollOptions(activePoll.options) : [];

  const stageStatus = stageRole?.stageRequest?.status;
  const canRequestStage =
    showStageRequest &&
    stageRole?.stageManaged === true &&
    stageRole?.tier === "AUDIENCE" &&
    stageStatus !== "REQUESTED";

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
    <aside className="w-full lg:w-96 flex flex-col rounded-2xl border border-border bg-card overflow-hidden">
      {announcements.length > 0 ? (
        <div className="border-b border-amber-200 bg-amber-50 px-3 py-2" data-testid="live-announcements">
          <p className="inline-flex items-center gap-1 text-xs font-semibold text-amber-900">
            <Megaphone className="h-3.5 w-3.5" />
            Announcement
          </p>
          <p className="mt-0.5 text-xs text-amber-900">{announcements[0]?.message}</p>
        </div>
      ) : null}

      {showStageRequest && stageRole?.stageManaged && stageRole.tier === "AUDIENCE" ? (
        <div className="border-b border-border px-3 py-2 flex items-center justify-between gap-2">
          {stageStatus === "REQUESTED" ? (
            <p className="text-xs text-muted-foreground" data-testid="stage-request-pending">
              Stage request sent — waiting for the producer…
            </p>
          ) : stageStatus === "DENIED" ? (
            <p className="text-xs text-muted-foreground">Stage request declined.</p>
          ) : (
            <p className="text-xs text-muted-foreground">Want to speak?</p>
          )}
          {canRequestStage ? (
            <button
              type="button"
              onClick={() => requestStage.mutate(eventId)}
              disabled={requestStage.isPending}
              className="inline-flex items-center gap-1 rounded-lg bg-violet-600 px-2.5 py-1 text-xs font-medium text-white disabled:opacity-60"
              data-testid="request-stage-button"
            >
              <Hand className="h-3.5 w-3.5" />
              Request stage
            </button>
          ) : null}
        </div>
      ) : null}

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
                <li
                  key={msg.id}
                  className={`rounded-lg px-2 py-1.5 text-xs ${
                    msg.kind === "ANNOUNCEMENT"
                      ? "bg-amber-50 border border-amber-200"
                      : "bg-background"
                  }`}
                >
                  <span className="text-muted-foreground">
                    {msg.kind === "ANNOUNCEMENT" ? "Announcement" : msg.participantType}:{" "}
                  </span>
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
  );
}
