"use client";

/**
 * Emergency landing — citizen entry to the Daidzai emergency suite.
 * Real CTAs only: SOS, nearest services, track. No fake tracker/availability.
 * Route: /emergency
 */

import Link from "next/link";
import { Ambulance, MapPin, Activity, PhoneCall } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";

const TILES = [
  {
    href: "/emergency/sos",
    title: "Get emergency help (SOS)",
    body: "Ask for help for yourself or someone near you. You won't be asked to pay first.",
    icon: Ambulance,
    tone: "bg-red-50 border-red-200 text-red-800",
  },
  {
    href: "/emergency/services",
    title: "Find nearest services",
    body: "See facilities able to help right now (routing via Ndila maps).",
    icon: MapPin,
    tone: "bg-teal-50 border-teal-200 text-teal-800",
  },
  {
    href: "/emergency/track",
    title: "Track a request",
    body: "Follow the status of an emergency you raised.",
    icon: Activity,
    tone: "bg-slate-50 border-border text-foreground",
  },
];

export default function EmergencyLandingPage() {
  return (
    <AppLayout>
      <PageShell
        title="Emergency"
        subtitle="Fast, governed emergency help — your details go straight to the response team"
        serviceSlug="daidzai"
      >
        <div className="mb-5 flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <PhoneCall className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            For an immediate life-threatening emergency, raise an SOS now — the team can come to
            you. You never need to pay before help is sent.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {TILES.map((t) => (
            <Link
              key={t.href}
              href={t.href}
              className={`flex flex-col gap-2 rounded-2xl border p-5 shadow-sm transition hover:shadow-md ${t.tone}`}
            >
              <t.icon className="h-6 w-6" />
              <span className="text-base font-semibold">{t.title}</span>
              <span className="text-sm opacity-90">{t.body}</span>
            </Link>
          ))}
        </div>

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/emergency" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
