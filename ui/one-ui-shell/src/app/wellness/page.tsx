"use client";

/**
 * Wellness Hub — Health OS §5 (Broad Participation) & §6 (Wellness Doctrine)
 *
 * "Wellness, lifestyle, fitness, diet, and sleep are first-class domains within
 *  the Impilo experience layer and must not be treated as peripheral extensions."
 *
 * Product doctrine: highly usable, attractive, motivating, consumer-grade.
 * Route: /wellness | Zone: wellness | Guard: auth
 */

import Link from "next/link";
import {
  Sparkles, Target, BookOpen, CalendarCheck, Activity,
  UtensilsCrossed, Moon, Users2, Trophy, MapPin, HeartHandshake, LayoutDashboard, Package,
  Route, Stethoscope,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useWellnessActivities } from "@/hooks/queries/useCitizenWellness";

const SECTIONS = [
  { href: "/wellness/goals", label: "Health Goals", description: "Set and track personal health targets", Icon: Target, color: "bg-green-50 text-green-600" },
  { href: "/wellness/activity", label: "Activity & Fitness", description: "Track physical activity, steps, and exercise", Icon: Activity, color: "bg-info-soft text-primary" },
  { href: "/wellness/diet", label: "Diet & Nutrition", description: "Dietary support, healthy eating guidance, and food tracking", Icon: UtensilsCrossed, color: "bg-orange-50 text-orange-600" },
  { href: "/wellness/sleep", label: "Sleep & Recovery", description: "Sleep patterns, recovery support, and rest tracking", Icon: Moon, color: "bg-info-soft text-indigo-600" },
  { href: "/wellness/clubs", label: "Clubs & Communities", description: "Walking, running, and fitness clubs near you", Icon: Users2, color: "bg-warning-soft text-purple-600" },
  { href: "/wellness/challenges", label: "Challenges", description: "Wellness challenges, achievements, and progress", Icon: Trophy, color: "bg-warning-soft text-amber-600" },
  { href: "/wellness/routes", label: "Routes & Places", description: "Find safe places, routes, and parks for exercise", Icon: MapPin, color: "bg-teal-50 text-teal-600" },
  { href: "/wellness/coaching", label: "Coaching & Habits", description: "Coaching, motivation, and habit support", Icon: HeartHandshake, color: "bg-pink-50 text-pink-600" },
  { href: "/wellness/plans", label: "Plans & Journeys", description: "Structured prevention journeys — enrol and track your progress", Icon: Route, color: "bg-emerald-50 text-emerald-600" },
  { href: "/wellness/care", label: "Connect to Care", description: "Route a wellness concern safely to the right care service", Icon: Stethoscope, color: "bg-rose-50 text-rose-600" },
  { href: "/wellness/programs", label: "Prevention Programs", description: "Enroll in wellness and prevention programmes", Icon: BookOpen, color: "bg-success-soft text-primary" },
  { href: "/wellness/commodities", label: "Programme commodities (Dura)", description: "Stock for wellness programmes is owned by Dura, not Simba", Icon: Package, color: "bg-sky-50 text-sky-600" },
  { href: "/wellness/screenings", label: "Screening Schedule", description: "View due and upcoming health screenings", Icon: CalendarCheck, color: "bg-cyan-50 text-cyan-600" },
];

function TodaySnapshot() {
  const patientId = useAuthStore((s) => s.user?.id);
  const today = new Date().toISOString().slice(0, 10);
  const { data: activities = [], isLoading } = useWellnessActivities(patientId, 7);
  const todayRow = activities.find((a) => (a.activityDate || "").slice(0, 10) === today);

  if (!patientId) return null;
  if (isLoading) {
    return (
      <div className="mb-6 rounded-xl border border-border bg-card px-4 py-3 text-sm text-muted-foreground">Loading today&apos;s snapshot…</div>
    );
  }
  if (!todayRow && activities.length === 0) return null;

  return (
    <div className="mb-6 rounded-xl border border-blue-100 bg-gradient-to-r from-blue-50 to-indigo-50 px-4 py-4 flex flex-wrap items-center gap-6 text-sm">
      <span className="font-semibold text-foreground">Today</span>
      <span className="text-foreground">
        <strong className="text-primary-hover">{todayRow?.steps ?? 0}</strong> steps
      </span>
      <span className="text-foreground">
        <strong className="text-cyan-700">{todayRow?.waterMl ?? 0}</strong> ml water
      </span>
      <span className="text-foreground">
        <strong className="text-violet-700">{todayRow?.activeMinutes ?? 0}</strong> active min
      </span>
      {todayRow?.sleepHours != null && (
        <span className="text-foreground">
          <strong className="text-primary-hover">{todayRow.sleepHours}</strong>h sleep
        </span>
      )}
      <div className="ml-auto flex flex-wrap gap-4 text-sm">
        <Link href="/wellness/activity" className="text-primary font-medium hover:underline">
          Log activity
        </Link>
        <Link href="/wellness/connect" className="text-indigo-600 font-medium hover:underline">
          Health Connect ingest
        </Link>
      </div>
    </div>
  );
}

export default function WellnessPage() {
  return (
    <AppLayout>
      <PageShell title="Wellness Hub" subtitle="Move. Eat. Sleep. Rest. Recover. Live." serviceSlug="simba">
        <Link
          href="/wellness/dashboard"
          className="mb-6 block rounded-xl border-2 border-teal-200 bg-gradient-to-r from-teal-50 to-cyan-50 p-5 shadow-sm hover:border-teal-400 hover:shadow-md transition-all group"
        >
          <div className="flex flex-wrap items-center gap-4">
            <div className="rounded-lg bg-teal-600 p-3 text-white shadow">
              <LayoutDashboard className="h-7 w-7" />
            </div>
            <div className="min-w-0 flex-1">
              <h2 className="text-lg font-semibold text-foreground group-hover:text-teal-700 transition-colors">
                My Wellness Dashboard
              </h2>
              <p className="text-sm text-muted-foreground mt-0.5">
                SIMBA snapshot: activity, sleep, diet, mood, goals, clubs, and challenges in one place.
              </p>
            </div>
            <span className="text-sm font-medium text-teal-700 whitespace-nowrap">Open dashboard →</span>
          </div>
        </Link>
        <TodaySnapshot />
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {SECTIONS.map(({ href, label, description, Icon, color }) => (
            <Link key={href} href={href} className="rounded-xl border border-border bg-card p-5 hover:border-blue-400 hover:shadow-md transition-all group">
              <div className="flex items-center gap-3 mb-2">
                <div className={`rounded-lg p-2 ${color.split(" ")[0]}`}><Icon className={`h-5 w-5 ${color.split(" ")[1]}`} /></div>
                <h3 className="font-semibold text-foreground group-hover:text-primary transition-colors">{label}</h3>
              </div>
              <p className="text-sm text-muted-foreground">{description}</p>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
