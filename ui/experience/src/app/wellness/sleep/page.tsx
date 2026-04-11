"use client";
import { Moon } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

/** Sleep & Recovery — Health OS §6: how people sleep, rest, and recover. */
export default function SleepPage() {
  return (
    <AppLayout>
      <PageShell title="Sleep & Recovery" subtitle="Sleep patterns, recovery support, and rest tracking" icon={<Moon className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[
            { title: "Sleep Journal", desc: "Log sleep times, quality, and interruptions" },
            { title: "Sleep Trends", desc: "Visualize your sleep patterns over time" },
            { title: "Recovery Score", desc: "Track rest and recovery readiness" },
            { title: "Sleep Guidance", desc: "Evidence-based tips for better sleep" },
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
