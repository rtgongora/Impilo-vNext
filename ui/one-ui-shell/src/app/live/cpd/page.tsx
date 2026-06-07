"use client";

import Link from "next/link";
import { GraduationCap } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useLiveEvents,
  useLiveMyAttendance,
  useLiveMyRegistrations,
  useLiveParticipant,
} from "@/hooks/queries/useLive";

function CpdAttendanceRow({ eventId, title }: { eventId: string; title: string }) {
  const { data: attendance, isLoading } = useLiveMyAttendance(eventId);
  if (isLoading) return null;
  if (!attendance?.eligibleForCpd) return null;
  return (
    <li className="rounded-lg border border-gray-200 bg-white p-3 text-sm">
      <p className="font-medium text-gray-900">{title}</p>
      <p>Live minutes: {attendance.liveWatchMinutes}</p>
      <p>Total watch: {attendance.totalWatchMinutes}</p>
      <p>Status: {attendance.completionStatus}</p>
      <Link href={`/live/event/${eventId}`} className="text-violet-700 text-xs underline">
        View event
      </Link>
    </li>
  );
}

export default function LiveCpdPage() {
  const { data: cpdEvents = [] } = useLiveEvents("ENDED");
  const { data: registrations = [] } = useLiveMyRegistrations();
  const { user } = useLiveParticipant();
  const providerId = user?.providerId ?? "";

  const eligibleEvents = cpdEvents.filter((e) => e.cpdEnabled);

  return (
    <AppLayout>
      <PageShell
        title="Live CPD"
        subtitle="CPD-eligible attendance from Impilo Live — council acceptance remains in Varapi"
        icon={<GraduationCap className="h-6 w-6" />}
      >
        <div className="mb-4 rounded-lg border border-indigo-200 bg-indigo-50 p-3 text-sm text-indigo-900">
          <p className="font-semibold">Council handoff</p>
          <p className="mt-1">
            Live CPD evidence complements Impilo Fundo. Submit packages via Provider Council Self-Service.
          </p>
          <Link
            href={
              providerId
                ? `/registry/provider-council/self-service?providerId=${encodeURIComponent(providerId)}`
                : "/registry/provider-council/self-service"
            }
            className="mt-2 inline-block rounded border border-indigo-300 bg-white px-3 py-1.5 text-xs font-medium text-indigo-900"
          >
            Open council self-service
          </Link>
        </div>

        <p className="text-sm text-gray-600 mb-3">
          {registrations.length} registrations · {eligibleEvents.length} CPD-eligible ended events
        </p>

        <ul className="space-y-2">
          {eligibleEvents.map((event) => (
            <CpdAttendanceRow key={event.id} eventId={event.id} title={event.title} />
          ))}
          {eligibleEvents.length === 0 ? (
            <li className="text-sm text-gray-500">No CPD-eligible live events yet.</li>
          ) : null}
        </ul>
      </PageShell>
    </AppLayout>
  );
}
