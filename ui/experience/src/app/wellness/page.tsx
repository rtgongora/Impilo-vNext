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
  UtensilsCrossed, Moon, Users2, Trophy, MapPin, HeartHandshake,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/wellness/goals", label: "Health Goals", description: "Set and track personal health targets", Icon: Target, color: "bg-green-50 text-green-600" },
  { href: "/wellness/activity", label: "Activity & Fitness", description: "Track physical activity, steps, and exercise", Icon: Activity, color: "bg-blue-50 text-blue-600" },
  { href: "/wellness/diet", label: "Diet & Nutrition", description: "Dietary support, healthy eating guidance, and food tracking", Icon: UtensilsCrossed, color: "bg-orange-50 text-orange-600" },
  { href: "/wellness/sleep", label: "Sleep & Recovery", description: "Sleep patterns, recovery support, and rest tracking", Icon: Moon, color: "bg-indigo-50 text-indigo-600" },
  { href: "/wellness/clubs", label: "Clubs & Communities", description: "Walking, running, and fitness clubs near you", Icon: Users2, color: "bg-purple-50 text-purple-600" },
  { href: "/wellness/challenges", label: "Challenges", description: "Wellness challenges, achievements, and progress", Icon: Trophy, color: "bg-amber-50 text-amber-600" },
  { href: "/wellness/routes", label: "Routes & Places", description: "Find safe places, routes, and parks for exercise", Icon: MapPin, color: "bg-teal-50 text-teal-600" },
  { href: "/wellness/coaching", label: "Coaching & Habits", description: "Coaching, motivation, and habit support", Icon: HeartHandshake, color: "bg-pink-50 text-pink-600" },
  { href: "/wellness/programs", label: "Prevention Programs", description: "Enroll in wellness and prevention programmes", Icon: BookOpen, color: "bg-emerald-50 text-emerald-600" },
  { href: "/wellness/screenings", label: "Screening Schedule", description: "View due and upcoming health screenings", Icon: CalendarCheck, color: "bg-cyan-50 text-cyan-600" },
];

export default function WellnessPage() {
  return (
    <AppLayout>
      <PageShell title="Wellness Hub" subtitle="Move. Eat. Sleep. Rest. Recover. Live." icon={<Sparkles className="h-6 w-6" />}>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
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
