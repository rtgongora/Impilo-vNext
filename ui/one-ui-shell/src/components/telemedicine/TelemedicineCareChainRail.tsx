"use client";

import Link from "next/link";
import { ArrowDownLeft, ClipboardList, Plus, Stethoscope, Video } from "lucide-react";

const LINKS = [
  {
    href: "/queue/triage",
    label: "Triage queue",
    description: "Assess acuity and escalate to teleconsult",
    icon: Stethoscope,
  },
  {
    href: "/queue/incoming-referrals",
    label: "Incoming referrals",
    description: "Specialist intake and P2P handoffs",
    icon: ArrowDownLeft,
  },
  {
    href: "/telemedicine/new",
    label: "New teleconsult",
    description: "Schedule governed virtual session",
    icon: Plus,
  },
  {
    href: "/telemedicine",
    label: "Telemedicine hub",
    description: "Active sessions and SLA context",
    icon: Video,
  },
] as const;

export function TelemedicineCareChainRail() {
  return (
    <section
      className="rounded-xl border border-cyan-200 bg-cyan-50/50 px-4 py-4"
      data-testid="telemedicine-care-chain-rail"
    >
      <div className="mb-3 flex items-center gap-2">
        <ClipboardList className="h-4 w-4 text-cyan-700" />
        <h3 className="text-sm font-semibold text-slate-900">Telehealth care chain</h3>
      </div>
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        {LINKS.map((link) => {
          const Icon = link.icon;
          return (
            <Link
              key={link.href}
              href={link.href}
              data-testid={`telemedicine-care-chain-${link.href.replace(/\//g, "-").replace(/^-/, "")}`}
              className="rounded-lg border border-cyan-100 bg-white px-3 py-2 transition-colors hover:border-cyan-300 hover:bg-cyan-50/80"
            >
              <div className="flex items-center gap-2 text-sm font-medium text-slate-900">
                <Icon className="h-3.5 w-3.5 text-cyan-600" />
                {link.label}
              </div>
              <p className="mt-1 text-xs text-slate-600">{link.description}</p>
            </Link>
          );
        })}
      </div>
    </section>
  );
}
