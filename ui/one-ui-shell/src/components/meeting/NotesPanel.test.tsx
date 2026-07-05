import { render, screen, fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Message } from "@/hooks/queries/useComms";
import type { MeetingActionItem } from "@/hooks/queries/useMeeting";

/* eslint-disable @typescript-eslint/no-explicit-any */

const sendMutate = vi.fn();
const createMutate = vi.fn();
const updateMutate = vi.fn();

const messagesState: { value: Message[] } = { value: [] };
const itemsState: { value: MeetingActionItem[] } = { value: [] };

vi.mock("@/hooks/queries/useComms", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/hooks/queries/useComms")>();
  return {
    ...original,
    useMessages: () => ({ data: messagesState.value }),
    useSendMessage: () => ({ mutate: sendMutate }),
  };
});

vi.mock("@/hooks/queries/useMeeting", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/hooks/queries/useMeeting")>();
  return {
    ...original,
    useMeetingActionItems: () => ({ data: itemsState.value }),
    useActionItemMutations: () => ({ create: { mutate: createMutate }, update: { mutate: updateMutate } }),
  };
});

import { NotesPanel } from "./NotesPanel";

/* eslint-enable @typescript-eslint/no-explicit-any */

function message(overrides: Partial<Message>): Message {
  return {
    messageId: crypto.randomUUID(),
    conversationId: "conv-1",
    senderId: "provider-a",
    senderType: "PROVIDER",
    senderDisplayName: "Dr A",
    contentType: "TEXT",
    body: "hello",
    status: "SENT",
    clientMessageId: null,
    sentAt: null,
    ...overrides,
  };
}

describe("NotesPanel", () => {
  beforeEach(() => {
    messagesState.value = [];
    itemsState.value = [];
    sendMutate.mockClear();
    createMutate.mockClear();
    updateMutate.mockClear();
  });

  it("renders only AGENDA/NOTE messages (plain chat stays in the chat panel)", () => {
    messagesState.value = [
      message({ contentType: "TEXT", body: "just chatting" }),
      message({ contentType: "AGENDA", body: "1. Review admissions" }),
      message({ contentType: "NOTE", body: "Decision: rollout Monday" }),
    ];
    render(<NotesPanel conversationId="conv-1" />);
    expect(screen.getByText("1. Review admissions")).toBeInTheDocument();
    expect(screen.getByText("Decision: rollout Monday")).toBeInTheDocument();
    expect(screen.queryByText("just chatting")).not.toBeInTheDocument();
  });

  it("adds an agenda item with contentType AGENDA", () => {
    render(<NotesPanel conversationId="conv-1" />);
    fireEvent.change(screen.getByLabelText("Note type"), { target: { value: "AGENDA" } });
    fireEvent.change(screen.getByTestId("note-input"), { target: { value: "Budget review" } });
    fireEvent.click(screen.getByTestId("note-add"));
    expect(sendMutate).toHaveBeenCalledWith(
      expect.objectContaining({ body: "Budget review", contentType: "AGENDA" }),
    );
  });

  it("creates and completes action items", () => {
    itemsState.value = [
      {
        actionItemId: "ai-1", conversationId: "conv-1", description: "Share protocol",
        assigneeId: "provider-b", assigneeDisplayName: "Dr B", dueDate: "2026-07-15",
        status: "OPEN", createdBy: "provider-a", createdAt: null, updatedAt: null,
      },
    ];
    render(<NotesPanel conversationId="conv-1" />);

    fireEvent.change(screen.getByTestId("action-item-input"), { target: { value: "Book theatre" } });
    fireEvent.click(screen.getByTestId("action-item-add"));
    expect(createMutate).toHaveBeenCalledWith({ description: "Book theatre" });

    fireEvent.click(screen.getByLabelText("Mark action item done"));
    expect(updateMutate).toHaveBeenCalledWith({ actionItemId: "ai-1", status: "DONE" });
  });
});
