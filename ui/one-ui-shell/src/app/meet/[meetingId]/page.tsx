"use client";

import { useParams } from "next/navigation";
import { Video } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { MeetingRoomShell } from "@/components/meeting/MeetingRoomShell";

/**
 * /meet/[meetingId] — the professional MEETING room (Khuluma MEETING conversation over an
 * Impilo Live event; media = rtc-gateway MEETING template with KNOCK lobby, grid layout).
 */
export default function MeetingPage() {
  const params = useParams<{ meetingId: string }>();
  const meetingId = params?.meetingId;

  return (
    <AppLayout>
      <PageShell
        title="Meeting"
        subtitle="Governed professional meeting — Khuluma over Impilo Live"
        icon={<Video className="h-5 w-5" />}
      >
        {meetingId ? <MeetingRoomShell conversationId={meetingId} /> : null}
      </PageShell>
    </AppLayout>
  );
}
