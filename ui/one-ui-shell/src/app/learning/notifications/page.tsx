"use client";

import { useState } from "react";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoNotifications, useScheduleLearningNotification } from "@/hooks/queries/useFundoStudio";

export default function LearningNotificationsPage() {
  const subject = useLearningSubject();
  const { data, refetch } = useFundoNotifications(subject, 100);
  const schedule = useScheduleLearningNotification();
  const [title, setTitle] = useState("Course due soon");
  const [channelPreference, setChannelPreference] = useState("IN_APP");

  async function onSchedule() {
    if (!subject) return;
    await schedule.mutateAsync({
      subjectType: subject.subjectType,
      subjectId: subject.subjectId,
      eventCode: "course.due.soon",
      title,
      message: "Your assigned learning is due soon.",
      channelPreference,
      scheduledAt: new Date().toISOString(),
      status: "PENDING",
    });
    void refetch();
  }

  const items = ((data?.data as { items?: Array<{ id: string; title: string; eventCode?: string; status?: string }> } | undefined)?.items ?? []);

  return (
      <PageShell title="Learning Notifications" subtitle="Comms Hub-aligned notification scheduling for learning reminders and outcomes.">
        <div className="rounded border border-border bg-card p-4">
          <div className="grid gap-2 md:grid-cols-3">
            <input value={title} onChange={(e) => setTitle(e.target.value)} className="w-full rounded border border-border px-3 py-2 text-sm" />
            <select value={channelPreference} onChange={(e) => setChannelPreference(e.target.value)} className="rounded border border-border px-3 py-2 text-sm">
              {["IN_APP", "EMAIL", "SMS", "WHATSAPP", "PUSH"].map((channel) => <option key={channel} value={channel}>{channel}</option>)}
            </select>
            <button onClick={onSchedule} className="rounded bg-teal-600 px-3 py-2 text-sm text-white">Schedule</button>
          </div>
        </div>
        <ul className="mt-4 space-y-2">
          {items.map((n) => (
            <li key={n.id} className="rounded border border-border bg-card px-3 py-2 text-sm">
              {n.title} · {n.eventCode ?? "learning.event"} · {n.status ?? "PENDING"}
            </li>
          ))}
        </ul>
      </PageShell>
  );
}
