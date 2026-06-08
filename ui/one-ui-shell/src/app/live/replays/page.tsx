"use client";

import { Clapperboard } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveEventList } from "@/components/live/LiveEventList";
import { useQueries } from "@tanstack/react-query";
import { liveApi } from "@/lib/live";
import { liveQueryKeys } from "@/hooks/queries/useLive";

export default function LiveReplaysPage() {
  const replayQueries = useQueries({
    queries: (["ENDED", "PUBLISHED_REPLAY"] as const).map((status) => ({
      queryKey: liveQueryKeys.events(status),
      queryFn: () => liveApi.listEvents(status),
      staleTime: 15_000,
    })),
  });
  const ended = replayQueries.flatMap((q) => q.data ?? []);
  const isLoading = replayQueries.some((q) => q.isLoading);

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
