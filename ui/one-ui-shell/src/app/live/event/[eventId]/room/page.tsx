"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Radio } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveModerationPanel } from "@/components/live/LiveModerationPanel";
import { LiveRoom } from "@/components/live/LiveRoom";
import { useLiveEvent, useLiveParticipant } from "@/hooks/queries/useLive";

export default function LiveEventRoomPage() {
  const params = useParams();
  const eventId = params.eventId as string;
  const { data: event } = useLiveEvent(eventId);
  const { user } = useLiveParticipant();
  const canModerate = user?.providerActivated ?? false;

  return (
    <AppLayout>
      <PageShell
        title={event?.title ?? "Live room"}
        subtitle="Governed media room with chat, Q&A, polls and resources"
        icon={<Radio className="h-6 w-6" />}
      >
        <div className="mb-3">
          <Link href={`/live/event/${eventId}`} className="text-sm text-violet-700 hover:underline">
            ← Event details
          </Link>
        </div>
        <LiveRoom eventId={eventId} />
        {canModerate ? (
          <div className="mt-6">
            <LiveModerationPanel eventId={eventId} />
          </div>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
