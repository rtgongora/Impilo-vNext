"use client";
import { HeartHandshake } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

/** Coaching & Habits — Health OS §6: coaching, motivation, and self-improvement. */
export default function CoachingPage() {
  return (
    <AppLayout>
      <PageShell title="Coaching & Habits" subtitle="Coaching, motivation, and habit support" icon={<HeartHandshake className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[
            { title: "My Habits", desc: "Track daily habits and streaks" },
            { title: "Coaching Plans", desc: "Guided wellness and behaviour change programs" },
            { title: "Motivational Insights", desc: "Personalised tips and encouragement" },
            { title: "Progress Journal", desc: "Reflect on your wellness journey" },
          ].map((s) => (
            <div key={s.title} className="rounded-lg border border-gray-200 bg-white p-5">
              <h3 className="font-semibold text-gray-900 mb-1">{s.title}</h3>
              <p className="text-sm text-gray-600">{s.desc}</p>
            </div>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
