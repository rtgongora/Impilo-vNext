/**
 * Lightweight in-process shell event bus for analytics, future tray, and extensions.
 * Not a replacement for server-side audit — session-local observability only.
 */

export type ShellEventName =
  | "app_launched"
  | "task_opened"
  | "task_activated"
  | "task_closed"
  | "task_cleared_minimized"
  | "app_pinned"
  | "app_unpinned"
  | "search_executed"
  | "fusion_hints_loaded"
  | "live_notification";

export type ShellEventPayload = Record<string, unknown>;

type Handler = (name: ShellEventName, payload: ShellEventPayload) => void;

const handlers = new Set<Handler>();

export function subscribeShellEvents(fn: Handler): () => void {
  handlers.add(fn);
  return () => handlers.delete(fn);
}

export function emitShellEvent(name: ShellEventName, payload: ShellEventPayload = {}): void {
  for (const fn of handlers) {
    try {
      fn(name, payload);
    } catch {
      /* never break shell for listener errors */
    }
  }
}
