"use client";

import { Bookmark } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveEventList } from "@/components/live/LiveEventList";
import {
  useLiveMyRegistrations,
  useLiveParticipant,
  useLiveSaveBookmark,
} from "@/hooks/queries/useLive";
import { useQueries } from "@tanstack/react-query";
import { liveApi } from "@/lib/live";

export default function LiveSavedPage() {
  const { data: registrations = [], isLoading } = useLiveMyRegistrations();
  const { participantId } = useLiveParticipant();
  const saveBookmark = useLiveSaveBookmark();
  const saved = registrations.filter((r) => r.saved);

  const eventQueries = useQueries({
    queries: saved.map((reg) => ({
      queryKey: ["live", "event", reg.eventId],
      queryFn: () => liveApi.getEvent(reg.eventId),
      enabled: Boolean(reg.eventId),
    })),
  });

  const events = eventQueries
    .map((q) => q.data)
    .filter((event): event is NonNullable<typeof event> => Boolean(event));
  const eventsLoading = isLoading || eventQueries.some((q) => q.isLoading);

  return (
    <AppLayout>
      <PageShell
        title="Saved Live Events"
        subtitle="Bookmarked sessions you plan to attend or replay"
        icon={<Bookmark className="h-6 w-6" />}
      >
        <LiveEventList
          events={events}
          registrations={saved}
          loading={eventsLoading}
          onSave={(eventId, savedFlag) =>
            saveBookmark.mutate({ eventId, participantId, saved: savedFlag })
          }
          emptyMessage="No saved events yet. Browse discovery to bookmark talks."
        />
      </PageShell>
    </AppLayout>
  );
}
