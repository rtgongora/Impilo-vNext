"use client";
import { Trophy } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

/** Challenges — Health OS §6: wellness challenges, achievements, and progress. */
export default function ChallengesPage() {
  return (
    <AppLayout>
      <PageShell title="Challenges" subtitle="Wellness challenges, achievements, and progress" icon={<Trophy className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[
            { title: "Active Challenges", desc: "Challenges you are currently participating in" },
            { title: "Discover Challenges", desc: "Browse available wellness challenges" },
            { title: "Achievements", desc: "Badges, streaks, and milestones you have earned" },
            { title: "Leaderboards", desc: "See how you compare within your clubs and communities" },
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
