"use client";

import Link from "next/link";
import { Calendar, Radio, User } from "lucide-react";
import type { LiveEvent } from "@/lib/live";

interface LiveEventCardProps {
  event: LiveEvent;
  registrationStatus?: string;
  saved?: boolean;
  onRegister?: () => void;
  onSave?: () => void;
  registering?: boolean;
}

function formatEventDate(value?: string | null): string {
  if (!value) return "Date TBC";
  try {
    return new Date(value).toLocaleString(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    });
  } catch {
    return value;
  }
}

function statusTone(status: string): string {
  switch (status) {
    case "LIVE":
      return "bg-red-100 text-red-800 border-danger/28";
    case "SCHEDULED":
      return "bg-sky-100 text-sky-800 border-sky-200";
    case "ENDED":
      return "bg-neutral-100 text-foreground border-border";
    case "CANCELLED":
      return "bg-amber-100 text-warning-foreground border-warning/35";
    default:
      return "bg-violet-100 text-violet-800 border-violet-200";
  }
}

export function LiveEventCard({
  event,
  registrationStatus,
  saved,
  onRegister,
  onSave,
  registering,
}: LiveEventCardProps) {
  const isLive = event.status === "LIVE";
  const isEnded = event.status === "ENDED";
  const isRegistered = registrationStatus === "REGISTERED" || registrationStatus === "CONFIRMED";
  const hostLabel = event.facilityId ? `Facility ${event.facilityId}` : "Impilo Live";

  return (
    <article className="rounded-2xl border border-border bg-card p-5 hover:border-violet-300 hover:shadow-sm transition-all">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${statusTone(event.status)}`}>
              {isLive ? <Radio className="h-3 w-3 mr-1 animate-pulse" /> : null}
              {event.status}
            </span>
            <span className="text-xs text-muted-foreground">{event.eventType.replace(/_/g, " ")}</span>
            {event.cpdEnabled ? (
              <span className="rounded-full bg-info-soft px-2 py-0.5 text-xs font-medium text-primary-hover">
                CPD{event.cpdPoints ? ` · ${event.cpdPoints} pts` : ""}
              </span>
            ) : null}
            {saved ? (
              <span className="rounded-full bg-warning-soft px-2 py-0.5 text-xs font-medium text-warning-foreground">
                Saved
              </span>
            ) : null}
          </div>
          <h3 className="font-semibold text-foreground truncate">{event.title}</h3>
          {event.description ? (
            <p className="mt-1 text-sm text-muted-foreground line-clamp-2">{event.description}</p>
          ) : null}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-3 text-xs text-muted-foreground">
        <span className="inline-flex items-center gap-1">
          <Calendar className="h-3.5 w-3.5" />
          {formatEventDate(event.startTime)}
        </span>
        <span className="inline-flex items-center gap-1">
          <User className="h-3.5 w-3.5" />
          {hostLabel}
        </span>
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        {isLive || isRegistered ? (
          <Link
            href={`/live/event/${event.id}/room`}
            className="rounded-lg bg-violet-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-violet-700"
          >
            {isLive ? "Join live" : "Enter room"}
          </Link>
        ) : !isEnded && onRegister ? (
          <button
            type="button"
            onClick={onRegister}
            disabled={registering}
            className="rounded-lg bg-violet-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-60"
          >
            {registering ? "Registering…" : "Register"}
          </button>
        ) : null}

        {isEnded ? (
          <Link
            href={`/live/event/${event.id}/replay`}
            className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-background"
          >
            Watch replay
          </Link>
        ) : null}

        <Link
          href={`/live/event/${event.id}`}
          className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-background"
        >
          Details
        </Link>

        {onSave ? (
          <button
            type="button"
            onClick={onSave}
            className="rounded-lg border border-amber-300 px-3 py-1.5 text-sm font-medium text-warning-foreground hover:bg-warning-soft"
          >
            {saved ? "Unsave" : "Save"}
          </button>
        ) : null}
      </div>
    </article>
  );
}
