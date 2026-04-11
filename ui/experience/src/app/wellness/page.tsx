"use client";

/**
 * Wellness Hub — Health OS §2
 * Prevention, self-care, fitness, health goals, screening schedules.
 * Route: /wellness | Zone: wellness | Guard: auth
 */

import Link from "next/link";
import { Sparkles, Target, BookOpen, CalendarCheck, Activity } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/wellness/goals", label: "Health Goals", description: "Set and track personal health targets", Icon: Target },
  { href: "/wellness/programs", label: "Prevention Programs", description: "Enroll in wellness and prevention programmes", Icon: BookOpen },
  { href: "/wellness/screenings", label: "Screening Schedule", description: "View due and upcoming health screenings", Icon: CalendarCheck },
  { href: "/wellness/activity", label: "Activity & Fitness", description: "Track physical activity and exercise", Icon: Activity },
];

export default function WellnessPage() {
  return (
    <AppLayout>
      <PageShell title="Wellness Hub" subtitle="Health OS §2 — Prevention, self-care, fitness, and health goals" icon={<Sparkles className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {SECTIONS.map(({ href, label, description, Icon }) => (
            <Link key={href} href={href} className="rounded-lg border border-gray-200 bg-white p-5 hover:border-blue-400 hover:shadow-sm transition-all">
              <div className="flex items-center gap-3 mb-2">
                <div className="rounded-lg bg-green-50 p-2"><Icon className="h-5 w-5 text-green-600" /></div>
                <h3 className="font-semibold text-gray-900">{label}</h3>
              </div>
              <p className="text-sm text-gray-600">{description}</p>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
