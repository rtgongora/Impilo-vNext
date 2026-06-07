"use client";

import { Clapperboard } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveEventList } from "@/components/live/LiveEventList";
import { useLiveEvents } from "@/hooks/queries/useLive";

export default function LiveReplaysPage() {
  const { data: ended = [], isLoading } = useLiveEvents("ENDED");

  return (
    <AppLayout>
      <PageShell
        title="Live Event Replays"
        subtitle="Catch up on ended sessions with governed replay attendance tracking"
        icon={<Clapperboard className="h-6 w-6" />}
      >
        <LiveEventList
          events={ended}
          loading={isLoading}
          emptyMessage="No replays available yet."
        />
      </PageShell>
    </AppLayout>
  );
}
