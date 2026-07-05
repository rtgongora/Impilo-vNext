"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CalendarClock, ClipboardList, Users, Video } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMeetingActionItems, useMeetingDetail } from "@/hooks/queries/useMeeting";
import { NotesPanel } from "@/components/meeting/NotesPanel";

function durationLabel(joinedAt: string | null, leftAt: string | null): string {
  if (!joinedAt) return "did not connect";
  if (!leftAt) return "in meeting";
  const seconds = Math.max(0, (new Date(leftAt).getTime() - new Date(joinedAt).getTime()) / 1000);
  const minutes = Math.round(seconds / 60);
  return minutes < 1 ? "<1 min" : `${minutes} min`;
}

/**
 * /meet/[meetingId]/summary — post-session SUMMARY + FOLLOW_UP surface: schedule, attendance
 * (derived from the rtc participant media-truth stamps), lobby outcomes, and the meeting's
 * notes + action items (which stay editable after the room ends).
 */
export default function MeetingSummaryPage() {
  const params = useParams<{ meetingId: string }>();
  const meetingId = params?.meetingId ?? null;
  const detail = useMeetingDetail(meetingId);
  const actionItems = useMeetingActionItems(meetingId);
  const openItems = (actionItems.data ?? []).filter((i) => i.status !== "DONE" && i.status !== "CANCELLED");

  return (
    <AppLayout>
      <PageShell
        title={detail.data?.title ? `Summary — ${detail.data.title}` : "Meeting summary"}
        subtitle="Attendance, notes and follow-ups for this meeting"
        icon={<Video className="h-5 w-5" />}
      >
        <div className="mb-3 flex items-center justify-between">
          <Link
            href={meetingId ? `/meet/${meetingId}` : "/work/comms"}
            className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Back to meeting room
          </Link>
          {detail.data?.scheduledAt && (
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <CalendarClock className="h-3.5 w-3.5" />
              Scheduled {new Date(detail.data.scheduledAt).toLocaleString()}
            </span>
          )}
        </div>

        <div className="grid gap-3 md:grid-cols-2">
          <section className="rounded-md border border-border bg-card p-3" data-testid="attendance-summary">
            <h2 className="mb-2 flex items-center gap-1 text-sm font-semibold">
              <Users className="h-4 w-4" /> Attendance
            </h2>
            {(detail.data?.attendance ?? []).length === 0 ? (
              <p className="text-xs text-muted-foreground">No attendance recorded yet.</p>
            ) : (
              <table className="w-full text-xs">
                <thead>
                  <tr className="text-left text-muted-foreground">
                    <th className="py-1 pr-2">Participant</th>
                    <th className="py-1 pr-2">Role</th>
                    <th className="py-1 pr-2">Lobby</th>
                    <th className="py-1">Presence</th>
                  </tr>
                </thead>
                <tbody>
                  {(detail.data?.attendance ?? []).map((a) => (
                    <tr key={a.actorId} className="border-t border-border" data-testid={`attendance-${a.actorId}`}>
                      <td className="py-1 pr-2">{a.displayName ?? a.actorId}</td>
                      <td className="py-1 pr-2">{a.role ?? "—"}</td>
                      <td className="py-1 pr-2">{a.status}</td>
                      <td className="py-1">{durationLabel(a.joinedAt, a.leftAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <p className="mt-2 text-[10px] text-muted-foreground">
              Presence is derived from the governed media room&apos;s participant events (rtc-gateway truth).
            </p>
          </section>

          <section className="rounded-md border border-border bg-card p-3" data-testid="followups-summary">
            <h2 className="mb-2 flex items-center gap-1 text-sm font-semibold">
              <ClipboardList className="h-4 w-4" /> Notes & follow-ups
              {openItems.length > 0 && (
                <span className="ml-1 rounded-full bg-amber-500/20 px-2 py-0.5 text-[10px] text-amber-600">
                  {openItems.length} open
                </span>
              )}
            </h2>
            <div className="h-[420px] overflow-hidden rounded bg-neutral-900 text-white">
              {meetingId && <NotesPanel conversationId={meetingId} />}
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
