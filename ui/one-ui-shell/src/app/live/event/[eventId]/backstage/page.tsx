"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Clapperboard } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveBackstageConsole } from "@/components/live/LiveBackstageConsole";
import { useLiveEvent } from "@/hooks/queries/useLive";

export default function LiveEventBackstagePage() {
  const params = useParams();
  const eventId = params.eventId as string;
  const { data: event } = useLiveEvent(eventId);

  return (
    <AppLayout>
      <PageShell
        title={event ? `Backstage · ${event.title}` : "Backstage"}
        subtitle="Producer console: speaker queue, announcements, polls, moderation and warm-up room"
        icon={<Clapperboard className="h-6 w-6" />}
      >
        <div className="mb-3 flex gap-4">
          <Link href={`/live/event/${eventId}`} className="text-sm text-violet-700 hover:underline">
            ← Event details
          </Link>
          <Link href={`/live/event/${eventId}/room`} className="text-sm text-violet-700 hover:underline">
            Main stage room →
          </Link>
        </div>
        <LiveBackstageConsole eventId={eventId} />
      </PageShell>
    </AppLayout>
  );
}
