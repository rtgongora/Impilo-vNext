"use client";

/**
 * Guidance Hub — Proactive Health Guidance (Health OS §2a, §16a)
 *
 * "The experience layer may proactively surface useful, timely, and relevant
 *  information — adherence support, screening reminders, follow-up prompts,
 *  campaign outreach, environmental risk alerts, and healthy living encouragement."
 */

import Link from "next/link";
import { Lightbulb, Bell, BookOpen, Calendar } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/guidance/reminders", label: "Reminders & Prompts", description: "Medication adherence, screening due dates, follow-up prompts", Icon: Bell, color: "bg-amber-50 text-amber-600" },
  { href: "/guidance/education", label: "Health Education", description: "Evidence-based articles, videos, and guides for your conditions", Icon: BookOpen, color: "bg-blue-50 text-blue-600" },
  { href: "/wellness/screenings", label: "Screening Schedule", description: "Due and upcoming preventive screenings", Icon: Calendar, color: "bg-green-50 text-green-600" },
  { href: "/ask", label: "Ask a Question", description: "Chat with the Health OS guidance assistant", Icon: Lightbulb, color: "bg-purple-50 text-purple-600" },
];

export default function GuidancePage() {
  return (
    <AppLayout>
      <PageShell title="Guidance" subtitle="Personalised health guidance, reminders, and education" icon={<Lightbulb className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-2xl mx-auto">
          {SECTIONS.map(({ href, label, description, Icon, color }) => (
            <Link key={href} href={href} className="rounded-xl border border-gray-200 bg-white p-5 hover:border-blue-400 hover:shadow-md transition-all group">
              <div className="flex items-center gap-3 mb-2">
                <div className={`rounded-lg p-2 ${color.split(" ")[0]}`}><Icon className={`h-5 w-5 ${color.split(" ")[1]}`} /></div>
                <h3 className="font-semibold text-gray-900 group-hover:text-blue-600 transition-colors">{label}</h3>
              </div>
              <p className="text-sm text-gray-600">{description}</p>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
