"use client";

/**
 * Waiting-room admit control — provider-side view of patients/caregivers who
 * requested to join the teleconsult. Polls GET .../waiting-room every 5s while
 * the session page is open and renders Admit / Deny actions per participant.
 */

import { useState } from "react";
import { Check, Loader2, UserX, Users } from "lucide-react";
import {
  useAdmitTelemedicineParticipant,
  useDenyTelemedicineParticipant,
  useTelemedicineWaitingRoom,
  type TelemedicineWaitingRoomEntry,
} from "@/hooks/queries/useTelemedicine";

function formatRequestedAt(requestedAt: string) {
  const parsed = new Date(requestedAt);
  if (Number.isNaN(parsed.getTime())) return "";
  return parsed.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export function WaitingRoomAdmitControl({ sessionId }: { sessionId: string }) {
  const waitingRoom = useTelemedicineWaitingRoom(sessionId);
  const admit = useAdmitTelemedicineParticipant(sessionId);
  const deny = useDenyTelemedicineParticipant(sessionId);
  const [pendingIdentity, setPendingIdentity] = useState<string | null>(null);

  const waiting: TelemedicineWaitingRoomEntry[] = waitingRoom.data?.data?.waiting ?? [];

  function handleAdmit(entry: TelemedicineWaitingRoomEntry) {
    setPendingIdentity(entry.identity);
    admit.mutate(
      { identity: entry.identity },
      { onSettled: () => setPendingIdentity(null) },
    );
  }

  function handleDeny(entry: TelemedicineWaitingRoomEntry) {
    setPendingIdentity(entry.identity);
    deny.mutate(
      { identity: entry.identity, reason: "Denied by consulting provider" },
      { onSettled: () => setPendingIdentity(null) },
    );
  }

  return (
    <div className="p-3 border-b" data-testid="waiting-room-admit-control">
      <div className="flex items-center justify-between mb-1.5">
        <h4 className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
          <Users className="w-3.5 h-3.5" /> Waiting room
        </h4>
        <span
          data-testid="waiting-room-count"
          className={`px-1.5 py-0.5 text-[10px] font-semibold rounded-full ${
            waiting.length > 0 ? "bg-amber-100 text-warning-foreground" : "bg-neutral-100 text-muted-foreground"
          }`}
        >
          {waiting.length}
        </span>
      </div>
      {waiting.length === 0 ? (
        <p className="text-[11px] text-muted-foreground">No one is waiting to join.</p>
      ) : (
        <ul className="space-y-1.5">
          {waiting.map((entry) => {
            const busy = pendingIdentity === entry.identity && (admit.isPending || deny.isPending);
            return (
              <li
                key={entry.identity}
                data-testid="waiting-room-entry"
                className="flex items-center gap-2 rounded-lg border border-amber-200 bg-warning-soft px-2 py-1.5"
              >
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-foreground truncate">{entry.displayName || entry.identity}</p>
                  <p className="text-[10px] text-muted-foreground">
                    {entry.role}
                    {entry.requestedAt ? ` · waiting since ${formatRequestedAt(entry.requestedAt)}` : ""}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => handleAdmit(entry)}
                  disabled={busy}
                  className="inline-flex items-center gap-1 px-2 py-1 text-[11px] font-medium rounded-md bg-green-100 text-green-700 hover:bg-green-200 disabled:opacity-40"
                >
                  {busy && admit.isPending ? <Loader2 className="w-3 h-3 animate-spin" /> : <Check className="w-3 h-3" />}
                  Admit
                </button>
                <button
                  type="button"
                  onClick={() => handleDeny(entry)}
                  disabled={busy}
                  className="inline-flex items-center gap-1 px-2 py-1 text-[11px] font-medium rounded-md bg-red-100 text-red-600 hover:bg-red-200 disabled:opacity-40"
                >
                  {busy && deny.isPending ? <Loader2 className="w-3 h-3 animate-spin" /> : <UserX className="w-3 h-3" />}
                  Deny
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
