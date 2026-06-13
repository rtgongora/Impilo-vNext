"use client";

import { useState } from "react";
import { Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveEventList } from "@/components/live/LiveEventList";
import {
  useLiveDiscover,
  useLiveMyRegistrations,
  useLiveParticipant,
  useLiveRegister,
  useLiveSaveBookmark,
} from "@/hooks/queries/useLive";

export default function LiveDiscoverPage() {
  const [contextType, setContextType] = useState<"CITIZEN" | "PUBLIC" | "PROFESSIONAL">("CITIZEN");
  const { data: events = [], isLoading } = useLiveDiscover(contextType);
  const { data: registrations = [] } = useLiveMyRegistrations();
  const { participantId, participantType } = useLiveParticipant();
  const register = useLiveRegister();
  const saveBookmark = useLiveSaveBookmark();
  const [registeringEventId, setRegisteringEventId] = useState<string | null>(null);

  async function handleRegister(eventId: string) {
    setRegisteringEventId(eventId);
    try {
      await register.mutateAsync({
        eventId,
        body: { participantId, participantType, inviteSource: "DISCOVER" },
      });
    } finally {
      setRegisteringEventId(null);
    }
  }

  function handleSave(eventId: string, saved: boolean) {
    saveBookmark.mutate({ eventId, participantId, saved });
  }

  return (
    <AppLayout>
      <PageShell
        title="Live Health Talks"
        subtitle="Discover citizen health talks, community broadcasts and public sessions"
        icon={<Search className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap gap-2">
          {(["CITIZEN", "PUBLIC", "PROFESSIONAL"] as const).map((ctx) => (
            <button
              key={ctx}
              type="button"
              onClick={() => setContextType(ctx)}
              className={`rounded-lg px-3 py-1.5 text-sm ${
                contextType === ctx
                  ? "bg-violet-600 text-white"
                  : "border border-border text-foreground"
              }`}
            >
              {ctx}
            </button>
          ))}
        </div>
        <LiveEventList
          events={events}
          registrations={registrations}
          loading={isLoading}
          onRegister={handleRegister}
          onSave={handleSave}
          registeringEventId={registeringEventId}
          emptyMessage="No events match this discovery context."
        />
      </PageShell>
    </AppLayout>
  );
}
