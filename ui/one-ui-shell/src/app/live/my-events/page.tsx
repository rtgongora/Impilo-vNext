"use client";

import { LayoutDashboard } from "lucide-react";
import { useQueries } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveEventList } from "@/components/live/LiveEventList";
import { useLiveMyRegistrations } from "@/hooks/queries/useLive";
import { liveApi } from "@/lib/live";

export default function LiveMyEventsPage() {
  const { data: registrations = [], isLoading } = useLiveMyRegistrations();
  const active = registrations.filter((r) => r.registrationStatus !== "CANCELLED");

  const eventQueries = useQueries({
    queries: active.map((reg) => ({
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
        title="My Live Events"
        subtitle="Sessions you registered for across citizen and professional contexts"
        icon={<LayoutDashboard className="h-6 w-6" />}
      >
        <LiveEventList
          events={events}
          registrations={active}
          loading={eventsLoading}
          emptyMessage="You have not registered for any live events yet."
        />
      </PageShell>
    </AppLayout>
  );
}
