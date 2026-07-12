"use client";

import { create } from "zustand";

/**
 * Nompilo assistant UI state — kept in a store (not component state) so
 * minimising the assistant or navigating between routes NEVER loses the
 * conversation or notification context (adaptive-workspace hard gate).
 * In-memory only: conversations are not persisted to storage.
 */

export interface AssistantChatMessage {
  role: "user" | "assistant";
  text: string;
}

export interface AssistantUiStore {
  /** Notification/chat panel expanded. */
  panelOpen: boolean;
  /** Chat mode active inside the panel. */
  chatOpen: boolean;
  chatMessages: AssistantChatMessage[];
  /** Last user keystroke in an editable control (ms epoch); 0 = not typing. */
  lastTypingAt: number;

  setPanelOpen: (open: boolean) => void;
  togglePanel: () => void;
  setChatOpen: (open: boolean) => void;
  appendChatMessage: (message: AssistantChatMessage) => void;
  markTyping: (at: number) => void;
}

export const useAssistantUiStore = create<AssistantUiStore>()((set) => ({
  panelOpen: false,
  chatOpen: false,
  chatMessages: [],
  lastTypingAt: 0,

  setPanelOpen: (open) => set({ panelOpen: open }),
  togglePanel: () => set((s) => ({ panelOpen: !s.panelOpen })),
  setChatOpen: (open) => set({ chatOpen: open }),
  appendChatMessage: (message) => set((s) => ({ chatMessages: [...s.chatMessages, message] })),
  markTyping: (at) => set({ lastTypingAt: at }),
}));

/** Active-typing window: field entry within this window suppresses non-urgent Nompilo prompts. */
export const TYPING_SUPPRESSION_MS = 4000;

/**
 * Interruption rule (Nompilo doctrine): only safety-critical content may
 * interrupt active field entry; everything else waits for a safe idle state.
 */
export function shouldSuppressNonUrgent(lastTypingAt: number, now: number): boolean {
  return lastTypingAt > 0 && now - lastTypingAt < TYPING_SUPPRESSION_MS;
}
