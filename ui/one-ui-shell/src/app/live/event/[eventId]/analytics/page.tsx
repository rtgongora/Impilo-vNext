"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { BarChart3 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveAnalyticsPanel } from "@/components/live/LiveAnalyticsPanel";
import { useLiveAttendance, useLiveEvent } from "@/hooks/queries/useLive";

export default function LiveEventAnalyticsPage() {
  const params = useParams();
  const eventId = params.eventId as string;
  const { data: event } = useLiveEvent(eventId);
  const { data: attendance = [] } = useLiveAttendance(eventId);

  return (
    <AppLayout>
      <PageShell
        title={event?.title ? `Analytics — ${event.title}` : "Live analytics"}
        subtitle="Real-time engagement metrics and attendance roll-up"
        icon={<BarChart3 className="h-6 w-6" />}
      >
        <div className="mb-4 flex gap-2">
          <Link href={`/live/event/${eventId}`} className="text-sm text-violet-700 hover:underline">
            ← Event details
          </Link>
          <Link href="/live/manage" className="text-sm text-gray-600 hover:underline">
            Management console
          </Link>
        </div>

        <LiveAnalyticsPanel eventId={eventId} />

        <section className="mt-6 rounded-2xl border border-gray-200 bg-white p-5">
          <h3 className="font-semibold text-gray-900 mb-3">Attendance roll-up</h3>
          <ul className="space-y-2 text-sm">
            {attendance.map((row) => (
              <li key={row.id} className="flex flex-wrap justify-between gap-2 rounded-lg bg-gray-50 px-3 py-2">
                <span className="text-gray-700">{row.participantId}</span>
                <span className="text-gray-500">
                  {row.totalWatchMinutes} min · {row.completionStatus}
                  {row.eligibleForCpd ? " · CPD eligible" : ""}
                </span>
              </li>
            ))}
            {attendance.length === 0 ? (
              <li className="text-gray-500">No attendance records yet.</li>
            ) : null}
          </ul>
        </section>
      </PageShell>
    </AppLayout>
  );
}
