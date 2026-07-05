"use client";

import { Clock, DoorClosed, ShieldAlert } from "lucide-react";
import type { MeetingJoinResponse } from "@/hooks/queries/useMeeting";

/**
 * KNOCK-lobby waiting surface for a MEETING participant who is not yet admitted.
 * The join query keeps re-knocking every 4s; this component only renders the state.
 */
export function MeetingLobby({
  join,
  title,
}: {
  join: MeetingJoinResponse | undefined;
  title?: string | null;
}) {
  const status = join?.status ?? "WAITING";

  if (status === "DENIED") {
    return (
      <div
        className="flex h-full min-h-[320px] w-full flex-col items-center justify-center gap-3 rounded-md bg-neutral-900 px-6 text-center text-white"
        data-testid="meeting-lobby-denied"
      >
        <DoorClosed className="h-10 w-10 text-red-400" />
        <p className="text-sm font-medium">You were not admitted to this meeting.</p>
        <p className="text-xs text-neutral-400">
          The host declined your request to join{title ? ` "${title}"` : ""}.
        </p>
      </div>
    );
  }

  if (status === "UNAVAILABLE") {
    return (
      <div
        className="flex h-full min-h-[320px] w-full flex-col items-center justify-center gap-3 rounded-md bg-neutral-900 px-6 text-center text-white"
        data-testid="meeting-lobby-unavailable"
      >
        <ShieldAlert className="h-10 w-10 text-amber-400" />
        <p className="text-sm font-medium">Meeting media is unavailable right now.</p>
        <p className="text-xs text-neutral-400">{join?.error ?? "The governed media service could not mint a room token. Retrying…"}</p>
      </div>
    );
  }

  return (
    <div
      className="flex h-full min-h-[320px] w-full flex-col items-center justify-center gap-3 rounded-md bg-neutral-900 px-6 text-center text-white"
      data-testid="meeting-lobby-waiting"
    >
      <Clock className="h-10 w-10 animate-pulse text-emerald-300" />
      <p className="text-sm font-medium">Waiting for a host to let you in…</p>
      <p className="text-xs text-neutral-400">
        {title ? `"${title}" — ` : ""}you have knocked; the meeting host has been notified.
      </p>
    </div>
  );
}
