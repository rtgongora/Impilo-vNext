"use client";

/**
 * Rito hub — Quality, Safety & Client Voice.
 * Static navigation cards only (no fetch); data lives on the linked surfaces.
 * Route: /rito
 */

import Link from "next/link";
import {
  ClipboardList,
  Radar,
  ShieldCheck,
  TrendingUp,
  MessageSquareHeart,
  Building2,
  Globe2,
  HeartPulse,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/rito/cases", label: "Cases", description: "Worklist of complaints, concerns and safety reports", Icon: ClipboardList },
  { href: "/rito/quality-signals", label: "Quality Signals", description: "Inbound signals to review and convert to cases", Icon: Radar },
  { href: "/rito/audits", label: "Audits & Supervision", description: "Quality audits, supportive supervision and findings", Icon: ShieldCheck },
  { href: "/rito/improvement", label: "CAPA & QI", description: "Corrective actions and quality-improvement plans", Icon: TrendingUp },
  { href: "/rito/mpdsr", label: "MPDSR Reviews", description: "Confidential maternal & perinatal death surveillance committee work", Icon: HeartPulse },
  { href: "/rito/surveys", label: "Experience Surveys", description: "Client experience instruments and results", Icon: MessageSquareHeart },
  { href: "/work/facility/rito", label: "Facility Dashboard", description: "Site-level quality & safety rollups", Icon: Building2 },
  { href: "/work/above-site/rito", label: "Above-Site Oversight", description: "District, province and national rollups", Icon: Globe2 },
];

export default function RitoHubPage() {
  return (
    <AppLayout>
      <PageShell
        title="Rito"
        subtitle="Quality, safety and client voice — governed, auditable and person-centered"
        serviceSlug="rito"
      >
        <div className="space-y-8">
          <section>
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
              Quality &amp; Safety Operations
            </h2>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
              {SECTIONS.map(({ href, label, description, Icon }) => (
                <Link
                  key={href}
                  href={href}
                  className="rounded-2xl border border-border bg-card p-5 transition-all hover:border-teal-300 hover:shadow-sm"
                >
                  <div className="mb-2 flex items-center gap-3">
                    <div className="rounded-xl bg-teal-50 p-2">
                      <Icon className="h-5 w-5 text-teal-600" />
                    </div>
                    <h3 className="font-semibold text-foreground">{label}</h3>
                  </div>
                  <p className="text-sm text-muted-foreground">{description}</p>
                </Link>
              ))}
            </div>
          </section>

          <div className="rounded-2xl border border-teal-200 bg-teal-50 p-4 text-sm text-teal-900">
            <strong className="mb-1 block">About Rito</strong>
            Rito is the quality, safety and client-voice plane. It never owns clinical truth — it
            captures concerns, runs audits, drives improvement and listens to clients under governed
            trust headers, with every action auditable.
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
