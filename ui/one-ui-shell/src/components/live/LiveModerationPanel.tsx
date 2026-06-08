"use client";

import { Shield, ToggleLeft, ToggleRight } from "lucide-react";
import {
  useLiveChat,
  useLiveEvent,
  useLiveModerateChat,
  useLiveModerateQuestion,
  useLiveParticipant,
  useLiveQuestions,
  useLiveSetChatEnabled,
  useLiveSetQnaEnabled,
} from "@/hooks/queries/useLive";

interface LiveModerationPanelProps {
  eventId: string;
}

export function LiveModerationPanel({ eventId }: LiveModerationPanelProps) {
  const { participantId } = useLiveParticipant();
  const { data: event } = useLiveEvent(eventId);
  const { data: chat = [] } = useLiveChat(eventId);
  const { data: questions = [] } = useLiveQuestions(eventId);
  const moderateChat = useLiveModerateChat();
  const moderateQuestion = useLiveModerateQuestion();
  const setChatEnabled = useLiveSetChatEnabled();
  const setQnaEnabled = useLiveSetQnaEnabled();

  const chatEnabled = event?.chatEnabled ?? true;
  const qnaEnabled = event?.qnaEnabled ?? true;

  return (
    <section className="rounded-2xl border border-amber-200 bg-amber-50/40 p-4 space-y-4">
      <div className="flex items-center gap-2">
        <Shield className="h-5 w-5 text-amber-700" />
        <h3 className="font-semibold text-amber-900">Moderation</h3>
      </div>

      <div className="flex flex-wrap gap-3">
        <button
          type="button"
          onClick={() => setChatEnabled.mutate({ eventId, enabled: !chatEnabled })}
          disabled={setChatEnabled.isPending}
          className="inline-flex items-center gap-2 rounded-lg border border-amber-300 bg-white px-3 py-2 text-sm text-amber-900 hover:bg-amber-100"
        >
          {chatEnabled ? <ToggleRight className="h-4 w-4" /> : <ToggleLeft className="h-4 w-4" />}
          Chat {chatEnabled ? "enabled" : "disabled"}
        </button>
        <button
          type="button"
          onClick={() => setQnaEnabled.mutate({ eventId, enabled: !qnaEnabled })}
          disabled={setQnaEnabled.isPending}
          className="inline-flex items-center gap-2 rounded-lg border border-amber-300 bg-white px-3 py-2 text-sm text-amber-900 hover:bg-amber-100"
        >
          {qnaEnabled ? <ToggleRight className="h-4 w-4" /> : <ToggleLeft className="h-4 w-4" />}
          Q&amp;A {qnaEnabled ? "enabled" : "disabled"}
        </button>
      </div>

      <div className="grid md:grid-cols-2 gap-4">
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-amber-800 mb-2">
            Recent chat
          </h4>
          <ul className="space-y-2 max-h-48 overflow-auto">
            {chat.slice(-8).map((msg) => (
              <li key={msg.id} className="rounded-lg border border-amber-100 bg-white p-2 text-xs">
                <p className="text-gray-800">{msg.message}</p>
                <p className="text-gray-500 mt-1">{msg.status}</p>
                {msg.status === "VISIBLE" ? (
                  <button
                    type="button"
                    onClick={() =>
                      moderateChat.mutate({
                        eventId,
                        messageId: msg.id,
                        body: { action: "HIDE", moderatedBy: participantId },
                      })
                    }
                    className="mt-1 text-amber-800 underline"
                  >
                    Hide message
                  </button>
                ) : null}
              </li>
            ))}
            {chat.length === 0 ? (
              <li className="text-xs text-gray-500">No chat messages yet.</li>
            ) : null}
          </ul>
        </div>

        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-amber-800 mb-2">
            Q&amp;A queue
          </h4>
          <ul className="space-y-2 max-h-48 overflow-auto">
            {questions.slice(0, 8).map((q) => (
              <li key={q.id} className="rounded-lg border border-amber-100 bg-white p-2 text-xs">
                <p className="text-gray-800">{q.questionText}</p>
                <p className="text-gray-500 mt-1">
                  {q.upvotes} upvotes · {q.status}
                </p>
                {q.status === "PENDING" ? (
                  <button
                    type="button"
                    onClick={() =>
                      moderateQuestion.mutate({
                        eventId,
                        questionId: q.id,
                        body: { action: "APPROVE", moderatedBy: participantId },
                      })
                    }
                    className="mt-1 text-amber-800 underline"
                  >
                    Approve for display
                  </button>
                ) : null}
              </li>
            ))}
            {questions.length === 0 ? (
              <li className="text-xs text-gray-500">No questions submitted yet.</li>
            ) : null}
          </ul>
        </div>
      </div>
    </section>
  );
}
