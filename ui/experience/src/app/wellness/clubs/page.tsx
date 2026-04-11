"use client";
import { Users2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

/** Clubs & Communities — Health OS §6: social participation for healthier living. */
export default function ClubsPage() {
  return (
    <AppLayout>
      <PageShell title="Clubs & Communities" subtitle="Walking, running, and fitness clubs near you" icon={<Users2 className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[
            { title: "Discover Clubs", desc: "Find walking, running, swimming, and fitness groups" },
            { title: "My Clubs", desc: "Clubs and communities you have joined" },
            { title: "Community Feed", desc: "Posts, events, and updates from your communities" },
            { title: "Create a Club", desc: "Start a new health or fitness group in your area" },
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
