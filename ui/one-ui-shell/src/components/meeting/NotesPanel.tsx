"use client";

import { useState } from "react";
import { CheckSquare, ClipboardList, ListTodo, Plus, StickyNote } from "lucide-react";
import { useMessages, useSendMessage } from "@/hooks/queries/useComms";
import {
  useActionItemMutations,
  useMeetingActionItems,
  type MeetingActionItem,
} from "@/hooks/queries/useMeeting";

/**
 * Agenda + shared notes + action items for a MEETING conversation. Agenda/notes are ordinary
 * governed Khuluma messages with contentType AGENDA|NOTE (template chat persistence =
 * CONVERSATION — zero parallel storage); action items are khuluma_meeting_action_items rows.
 */
export function NotesPanel({ conversationId }: { conversationId: string }) {
  const messages = useMessages(conversationId);
  const send = useSendMessage(conversationId);
  const actionItems = useMeetingActionItems(conversationId);
  const { create, update } = useActionItemMutations(conversationId);

  const [noteDraft, setNoteDraft] = useState("");
  const [noteType, setNoteType] = useState<"NOTE" | "AGENDA">("NOTE");
  const [actionDraft, setActionDraft] = useState("");

  const noteMessages = (messages.data ?? []).filter(
    (m) => m.contentType === "AGENDA" || m.contentType === "NOTE",
  );

  const addNote = () => {
    const body = noteDraft.trim();
    if (!body) return;
    send.mutate({ body, contentType: noteType, clientMessageId: crypto.randomUUID() });
    setNoteDraft("");
  };

  const addActionItem = () => {
    const description = actionDraft.trim();
    if (!description) return;
    create.mutate({ description });
    setActionDraft("");
  };

  const toggleItem = (item: MeetingActionItem) => {
    update.mutate({ actionItemId: item.actionItemId, status: item.status === "DONE" ? "OPEN" : "DONE" });
  };

  return (
    <div className="flex h-full flex-col gap-3 overflow-y-auto p-3" data-testid="notes-panel">
      <div>
        <p className="mb-2 flex items-center gap-1 text-xs font-semibold text-neutral-300">
          <StickyNote className="h-3 w-3" /> Agenda & notes
        </p>
        <ul className="space-y-1.5">
          {noteMessages.length === 0 && (
            <li className="text-xs text-neutral-500">No agenda items or notes yet.</li>
          )}
          {noteMessages.map((m) => (
            <li key={m.messageId} className="rounded bg-neutral-800 px-2 py-1.5 text-xs" data-testid={`note-${m.messageId}`}>
              <span
                className={`mr-1.5 rounded px-1 py-0.5 text-[10px] ${
                  m.contentType === "AGENDA" ? "bg-sky-900 text-sky-200" : "bg-neutral-700 text-neutral-300"
                }`}
              >
                {m.contentType}
              </span>
              {m.body}
            </li>
          ))}
        </ul>
        <div className="mt-2 flex gap-1">
          <select
            aria-label="Note type"
            className="rounded border border-neutral-700 bg-neutral-900 px-1 py-1 text-[10px]"
            value={noteType}
            onChange={(e) => setNoteType(e.target.value as "NOTE" | "AGENDA")}
          >
            <option value="NOTE">Note</option>
            <option value="AGENDA">Agenda</option>
          </select>
          <input
            aria-label="New note"
            data-testid="note-input"
            className="min-w-0 flex-1 rounded border border-neutral-700 bg-neutral-900 px-2 py-1 text-xs"
            placeholder={noteType === "AGENDA" ? "Add agenda item…" : "Add shared note…"}
            value={noteDraft}
            onChange={(e) => setNoteDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && addNote()}
          />
          <button
            type="button"
            aria-label="Add note"
            data-testid="note-add"
            className="rounded bg-emerald-600 px-2 text-white hover:bg-emerald-500"
            onClick={addNote}
          >
            <Plus className="h-3 w-3" />
          </button>
        </div>
      </div>

      <div>
        <p className="mb-2 flex items-center gap-1 text-xs font-semibold text-neutral-300">
          <ListTodo className="h-3 w-3" /> Action items
        </p>
        <ul className="space-y-1.5">
          {(actionItems.data ?? []).length === 0 && (
            <li className="text-xs text-neutral-500">No action items yet.</li>
          )}
          {(actionItems.data ?? []).map((item) => (
            <li
              key={item.actionItemId}
              className="flex items-start gap-2 rounded bg-neutral-800 px-2 py-1.5 text-xs"
              data-testid={`action-item-${item.actionItemId}`}
            >
              <button
                type="button"
                aria-label={item.status === "DONE" ? "Reopen action item" : "Mark action item done"}
                className={item.status === "DONE" ? "text-emerald-400" : "text-neutral-400 hover:text-white"}
                onClick={() => toggleItem(item)}
              >
                {item.status === "DONE" ? <CheckSquare className="h-3.5 w-3.5" /> : <ClipboardList className="h-3.5 w-3.5" />}
              </button>
              <span className={`min-w-0 flex-1 ${item.status === "DONE" ? "line-through opacity-60" : ""}`}>
                {item.description}
                {item.assigneeDisplayName || item.assigneeId ? (
                  <span className="ml-1 text-[10px] text-neutral-400">→ {item.assigneeDisplayName ?? item.assigneeId}</span>
                ) : null}
                {item.dueDate ? <span className="ml-1 text-[10px] text-amber-300">due {item.dueDate}</span> : null}
              </span>
            </li>
          ))}
        </ul>
        <div className="mt-2 flex gap-1">
          <input
            aria-label="New action item"
            data-testid="action-item-input"
            className="min-w-0 flex-1 rounded border border-neutral-700 bg-neutral-900 px-2 py-1 text-xs"
            placeholder="Add action item…"
            value={actionDraft}
            onChange={(e) => setActionDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && addActionItem()}
          />
          <button
            type="button"
            aria-label="Add action item"
            data-testid="action-item-add"
            className="rounded bg-emerald-600 px-2 text-white hover:bg-emerald-500"
            onClick={addActionItem}
          >
            <Plus className="h-3 w-3" />
          </button>
        </div>
      </div>
    </div>
  );
}
