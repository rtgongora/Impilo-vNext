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
      return "bg-red-100 text-red-800 border-red-200";
    case "SCHEDULED":
      return "bg-sky-100 text-sky-800 border-sky-200";
    case "ENDED":
      return "bg-gray-100 text-gray-700 border-gray-200";
    case "CANCELLED":
      return "bg-amber-100 text-amber-900 border-amber-200";
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
    <article className="rounded-2xl border border-gray-200 bg-white p-5 hover:border-violet-300 hover:shadow-sm transition-all">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${statusTone(event.status)}`}>
              {isLive ? <Radio className="h-3 w-3 mr-1 animate-pulse" /> : null}
              {event.status}
            </span>
            <span className="text-xs text-gray-500">{event.eventType.replace(/_/g, " ")}</span>
            {event.cpdEnabled ? (
              <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700">
                CPD{event.cpdPoints ? ` · ${event.cpdPoints} pts` : ""}
              </span>
            ) : null}
            {saved ? (
              <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800">
                Saved
              </span>
            ) : null}
          </div>
          <h3 className="font-semibold text-gray-900 truncate">{event.title}</h3>
          {event.description ? (
            <p className="mt-1 text-sm text-gray-600 line-clamp-2">{event.description}</p>
          ) : null}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-3 text-xs text-gray-500">
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
            className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Watch replay
          </Link>
        ) : null}

        <Link
          href={`/live/event/${event.id}`}
          className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          Details
        </Link>

        {onSave ? (
          <button
            type="button"
            onClick={onSave}
            className="rounded-lg border border-amber-300 px-3 py-1.5 text-sm font-medium text-amber-900 hover:bg-amber-50"
          >
            {saved ? "Unsave" : "Save"}
          </button>
        ) : null}
      </div>
    </article>
  );
}
