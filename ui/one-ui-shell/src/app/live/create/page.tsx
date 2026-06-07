"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { CalendarPlus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveNompiloAssistPanel } from "@/components/live/LiveNompiloAssistPanel";
import { useCreateLiveEvent, useLiveParticipant } from "@/hooks/queries/useLive";
import { useFacilityStore } from "@/hooks/useFacilityStore";

export default function LiveCreatePage() {
  const router = useRouter();
  const create = useCreateLiveEvent();
  const { participantId } = useLiveParticipant();
  const facilityId = useFacilityStore((s) => s.facility?.id);

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [eventType, setEventType] = useState("WEBINAR");
  const [contextType, setContextType] = useState<"PROFESSIONAL" | "CITIZEN" | "PUBLIC">("PROFESSIONAL");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [cpdEnabled, setCpdEnabled] = useState(false);
  const [cpdPoints, setCpdPoints] = useState("1");
  const [agenda, setAgenda] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const event = await create.mutateAsync({
      title,
      description,
      eventType,
      contextType,
      organiserType: "PROVIDER",
      organiserId: participantId,
      facilityId: facilityId ?? undefined,
      startTime: startTime ? new Date(startTime).toISOString() : undefined,
      endTime: endTime ? new Date(endTime).toISOString() : undefined,
      registrationRequired: true,
      recordingAllowed: true,
      replayAllowed: true,
      cpdEnabled,
      cpdPoints: cpdEnabled ? Number(cpdPoints) : undefined,
      agenda,
      attendanceThresholdMinutes: 30,
    });
    router.push(`/live/event/${event.id}`);
  }

  return (
    <AppLayout>
      <PageShell
        title="Create Live Event"
        subtitle="Author a governed live session with optional CPD and replay"
        icon={<CalendarPlus className="h-6 w-6" />}
      >
        <div className="grid lg:grid-cols-3 gap-6">
          <form onSubmit={handleSubmit} className="lg:col-span-2 space-y-4 rounded-2xl border border-gray-200 bg-white p-5">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Title</label>
              <input
                required
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm min-h-[80px]"
              />
            </div>
            <div className="grid sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Event type</label>
                <select
                  value={eventType}
                  onChange={(e) => setEventType(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                >
                  {["WEBINAR", "BROADCAST", "TRAINING", "TOWN_HALL", "COMMUNITY_TALK"].map((t) => (
                    <option key={t} value={t}>
                      {t.replace(/_/g, " ")}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Audience context</label>
                <select
                  value={contextType}
                  onChange={(e) => setContextType(e.target.value as typeof contextType)}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                >
                  <option value="PROFESSIONAL">Professional</option>
                  <option value="CITIZEN">Citizen</option>
                  <option value="PUBLIC">Public</option>
                </select>
              </div>
            </div>
            <div className="grid sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Start</label>
                <input
                  type="datetime-local"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">End</label>
                <input
                  type="datetime-local"
                  value={endTime}
                  onChange={(e) => setEndTime(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Agenda</label>
              <textarea
                value={agenda}
                onChange={(e) => setAgenda(e.target.value)}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm min-h-[72px]"
              />
            </div>
            <label className="inline-flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={cpdEnabled}
                onChange={(e) => setCpdEnabled(e.target.checked)}
              />
              CPD eligible
            </label>
            {cpdEnabled ? (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">CPD points</label>
                <input
                  type="number"
                  min="0"
                  step="0.5"
                  value={cpdPoints}
                  onChange={(e) => setCpdPoints(e.target.value)}
                  className="w-32 rounded-lg border border-gray-300 px-3 py-2 text-sm"
                />
              </div>
            ) : null}
            <button
              type="submit"
              disabled={create.isPending}
              className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-60"
            >
              {create.isPending ? "Creating…" : "Create draft event"}
            </button>
          </form>

          <LiveNompiloAssistPanel eventTitle={title} eventType={eventType} />
        </div>
      </PageShell>
    </AppLayout>
  );
}
