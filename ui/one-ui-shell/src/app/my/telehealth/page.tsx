"use client";

/**
 * My telehealth — a citizen's video visits, in plain language.
 *
 * Listing uses the same BFF sessions listing the provider hub uses, scoped
 * with the patientId query param (the person anchor).
 *
 * SEAM: the sessions listing is patient-scoped by CPID, while the signed-in
 * citizen carries a Health ID. Until the BFF resolves Health ID → CPID for
 * citizen callers (or offers a first-class /my/teleconsults endpoint), visits
 * created against a walk-in CPID may not appear here even though the person
 * can still open a visit directly from its link/notification.
 */

import Link from "next/link";
import { CalendarClock, ChevronRight, Video } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useTelemedicineSessions, type TelemedicineSession } from "@/hooks/queries/useTelemedicine";

const STATUS_COPY: Record<string, { label: string; className: string }> = {
  SCHEDULED: { label: "Upcoming", className: "bg-primary-soft text-primary" },
  SUBMITTED: { label: "Being arranged", className: "bg-primary-soft text-primary" },
  ACCEPTED: { label: "Ready soon", className: "bg-primary-soft text-primary" },
  ACTIVE: { label: "Happening now", className: "bg-green-100 text-green-700" },
  IN_PROGRESS: { label: "Happening now", className: "bg-green-100 text-green-700" },
  RESPONDED: { label: "Finished", className: "bg-neutral-100 text-muted-foreground" },
  COMPLETED: { label: "Finished", className: "bg-neutral-100 text-muted-foreground" },
  CLOSED: { label: "Finished", className: "bg-neutral-100 text-muted-foreground" },
  CANCELLED: { label: "Cancelled", className: "bg-red-100 text-red-600" },
};

function statusCopy(status: string) {
  return STATUS_COPY[status.toUpperCase()] ?? { label: "Visit", className: "bg-neutral-100 text-muted-foreground" };
}

function formatWhen(value: string | null) {
  if (!value) return null;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return null;
  return parsed.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
}

export default function MyTelehealthPage() {
  const user = useAuthStore((s) => s.user);
  const personAnchor = user?.healthId || user?.id || undefined;

  const { data, isLoading } = useTelemedicineSessions({ patientId: personAnchor });
  const sessions: TelemedicineSession[] = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="My video visits"
        subtitle="See your telehealth appointments and join when the care team is ready"
        icon={<Video className="h-5 w-5" />}
      >
        {isLoading ? (
          <p className="text-sm text-muted-foreground py-8 text-center">Looking for your visits...</p>
        ) : sessions.length === 0 ? (
          <div className="max-w-lg mx-auto text-center py-12 space-y-2" data-testid="my-telehealth-empty">
            <Video className="w-10 h-10 mx-auto text-muted-foreground" />
            <h2 className="text-base font-semibold text-foreground">No video visits yet</h2>
            <p className="text-sm text-muted-foreground">
              When a clinic books a video visit for you, it will show up here. If you were
              sent a visit link directly, that link will still work.
            </p>
          </div>
        ) : (
          <ul className="space-y-2 max-w-2xl" data-testid="my-telehealth-list">
            {sessions.map((session) => {
              const attrs = session.attributes;
              const copy = statusCopy(attrs.status ?? "");
              const when = formatWhen(attrs.scheduled_at) ?? formatWhen(attrs.created_at);
              return (
                <li key={session.id}>
                  <Link
                    href={`/my/telehealth/${session.id}`}
                    className="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 hover:border-primary/40 transition-colors"
                    data-testid="my-telehealth-item"
                  >
                    <div className="w-9 h-9 rounded-full bg-primary-soft flex items-center justify-center shrink-0">
                      <Video className="w-4 h-4 text-primary" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-foreground">Video visit</p>
                      {when && (
                        <p className="text-xs text-muted-foreground flex items-center gap-1">
                          <CalendarClock className="w-3 h-3" /> {when}
                        </p>
                      )}
                    </div>
                    <span className={`px-2 py-0.5 text-[10px] font-medium rounded-full ${copy.className}`}>
                      {copy.label}
                    </span>
                    <ChevronRight className="w-4 h-4 text-muted-foreground shrink-0" />
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
