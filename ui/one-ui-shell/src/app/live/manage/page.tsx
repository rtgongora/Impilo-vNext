"use client";

import Link from "next/link";
import { Settings2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveEventList } from "@/components/live/LiveEventList";
import { LiveModerationPanel } from "@/components/live/LiveModerationPanel";
import {
  useCancelLiveEvent,
  useLiveEvents,
  useScheduleLiveEvent,
} from "@/hooks/queries/useLive";
import { useState } from "react";

export default function LiveManagePage() {
  const { data: events = [], isLoading } = useLiveEvents();
  const schedule = useScheduleLiveEvent();
  const cancel = useCancelLiveEvent();
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null);

  const draftEvents = events.filter((e) => e.status === "DRAFT");
  const activeEvents = events.filter((e) => e.status !== "DRAFT");

  return (
    <AppLayout>
      <PageShell
        title="Live Event Management"
        subtitle="Schedule drafts, monitor live sessions, and open moderation tools"
        icon={<Settings2 className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap gap-2">
          <Link
            href="/live/create"
            className="rounded-lg bg-violet-600 px-3 py-2 text-sm font-medium text-white hover:bg-violet-700"
          >
            Create new event
          </Link>
          <Link href="/live" className="rounded-lg border border-border px-3 py-2 text-sm text-foreground">
            Back to hub
          </Link>
        </div>

        {draftEvents.length > 0 ? (
          <section className="mb-8">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground mb-3">Drafts</h2>
            <div className="space-y-2">
              {draftEvents.map((event) => (
                <div
                  key={event.id}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border bg-card p-3"
                >
                  <div>
                    <p className="font-medium text-foreground">{event.title}</p>
                    <p className="text-xs text-muted-foreground">{event.eventType}</p>
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => schedule.mutate(event.id)}
                      disabled={schedule.isPending}
                      className="rounded-lg bg-sky-600 px-3 py-1.5 text-sm text-white"
                    >
                      Schedule
                    </button>
                    <Link
                      href={`/live/event/${event.id}`}
                      className="rounded-lg border border-border px-3 py-1.5 text-sm text-foreground"
                    >
                      Edit
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          </section>
        ) : null}

        <section className="mb-8">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground mb-3">
            Scheduled &amp; live
          </h2>
          <LiveEventList events={activeEvents} loading={isLoading} />
        </section>

        <section>
          <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground mb-3">
            Moderation console
          </h2>
          <div className="mb-3 flex flex-wrap gap-2">
            {activeEvents.slice(0, 6).map((event) => (
              <button
                key={event.id}
                type="button"
                onClick={() => setSelectedEventId(event.id)}
                className={`rounded-lg border px-3 py-1.5 text-sm ${
                  selectedEventId === event.id
                    ? "border-violet-400 bg-violet-50 text-violet-800"
                    : "border-border text-foreground"
                }`}
              >
                {event.title}
              </button>
            ))}
          </div>
          {selectedEventId ? (
            <>
              <LiveModerationPanel eventId={selectedEventId} />
              <div className="mt-3 flex gap-2">
                <Link
                  href={`/live/event/${selectedEventId}/analytics`}
                  className="rounded-lg border border-border px-3 py-1.5 text-sm text-foreground"
                >
                  Open analytics
                </Link>
                <button
                  type="button"
                  onClick={() => cancel.mutate(selectedEventId)}
                  disabled={cancel.isPending}
                  className="rounded-lg border border-amber-300 px-3 py-1.5 text-sm text-warning-foreground"
                >
                  Cancel event
                </button>
              </div>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">Select an event to open moderation controls.</p>
          )}
        </section>
      </PageShell>
    </AppLayout>
  );
}
