"use client";

import { Loader2 } from "lucide-react";
import type { LiveEvent, LiveRegistration } from "@/lib/live";
import { LiveEventCard } from "./LiveEventCard";

interface LiveEventListProps {
  events: LiveEvent[];
  registrations?: LiveRegistration[];
  loading?: boolean;
  emptyMessage?: string;
  onRegister?: (eventId: string) => void;
  onSave?: (eventId: string, saved: boolean) => void;
  registeringEventId?: string | null;
}

export function LiveEventList({
  events,
  registrations = [],
  loading,
  emptyMessage = "No live events found.",
  onRegister,
  onSave,
  registeringEventId,
}: LiveEventListProps) {
  if (loading) {
    return (
      <div className="flex items-center justify-center gap-2 py-12 text-sm text-gray-500">
        <Loader2 className="h-4 w-4 animate-spin" />
        Loading events…
      </div>
    );
  }

  if (!events.length) {
    return <p className="py-8 text-center text-sm text-gray-500">{emptyMessage}</p>;
  }

  const registrationByEvent = new Map(
    registrations.map((r) => [r.eventId, r]),
  );

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {events.map((event) => {
        const reg = registrationByEvent.get(event.id);
        return (
          <LiveEventCard
            key={event.id}
            event={event}
            registrationStatus={reg?.registrationStatus}
            saved={reg?.saved}
            onRegister={onRegister ? () => onRegister(event.id) : undefined}
            onSave={onSave ? () => onSave(event.id, !reg?.saved) : undefined}
            registering={registeringEventId === event.id}
          />
        );
      })}
    </div>
  );
}
