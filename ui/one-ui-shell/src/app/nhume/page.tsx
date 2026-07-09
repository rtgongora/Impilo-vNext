"use client";

/**
 * Nhume Hub
 *
 * Single entry-point that fans out to all Nhume surfaces. Backed by
 * services/nhume-service (`/api/v1/nhume`). Visible to anyone who is
 * authenticated; downstream pages enforce their own role guards.
 */

import Link from "next/link";
import {
  Truck,
  LayoutDashboard,
  ListChecks,
  Map as MapIcon,
  Navigation,
  PackageCheck,
  Users,
  ScrollText,
  Plane,
  BarChart3,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { ShellIcon } from "@/components/shell/ShellIcon";
import { CARGO_PROFILES } from "@/lib/nhume/cargo-profiles";

const SECTIONS = [
  { href: "/nhume/dashboard", label: "Operations Dashboard", description: "Counts, alerts, SLA panel and live map", Icon: LayoutDashboard },
  { href: "/nhume/deliveries", label: "Missions & Deliveries", description: "Browse, create and progress dispatch missions", Icon: ListChecks },
  { href: "/nhume/dispatcher", label: "Dispatcher Console", description: "Pending queue, suggested assignments, exceptions", Icon: Navigation },
  { href: "/nhume/inbound", label: "Inbound & Handover", description: "Incoming movements to receive and hand over", Icon: PackageCheck },
  { href: "/nhume/map", label: "Fleet Tracking Map", description: "Vehicles, routes, geofences and ETAs", Icon: MapIcon },
  { href: "/nhume/courier", label: "Courier Console", description: "Assigned deliveries, pickup and proof capture", Icon: PackageCheck },
  { href: "/nhume/fleet", label: "Fleet & Assets", description: "Vehicles, drones, robots and lockers", Icon: Truck },
  { href: "/nhume/couriers", label: "Drivers & Couriers", description: "Profiles, availability, verification", Icon: Users },
  { href: "/nhume/policies", label: "Delivery Policies", description: "SLA, proof rules, allowed modes", Icon: ScrollText },
  { href: "/nhume/autonomous", label: "Autonomous Delivery", description: "Drones, robots and AV missions", Icon: Plane },
  { href: "/nhume/analytics", label: "Analytics", description: "Lifecycle, cold-chain and route deviations", Icon: BarChart3 },
];

export default function NhumeHubPage() {
  return (
    <AppLayout>
      <PageShell
        title="Nhume"
        subtitle="Dispatch, Delivery, Fleet Tracking & Last-Mile Logistics"
        serviceSlug="nhume"
      >
        <section className="mb-6">
          <h2 className="mb-1 text-sm font-semibold text-foreground">Start a movement</h2>
          <p className="mb-3 text-xs text-muted-foreground">
            Nhume moves what the health system needs moved — pick the cargo to open a mission with the right handling and fields.
          </p>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
            {CARGO_PROFILES.filter((p) => p.id !== "GENERAL").map((p) => (
              <Link
                key={p.id}
                href={`/nhume/deliveries/new?cargo=${p.id}`}
                className="flex flex-col items-start gap-1 rounded-xl border border-border bg-card p-3 hover:border-teal-300 hover:shadow-sm transition-all"
              >
                <ShellIcon name={p.icon} className="h-4 w-4 text-teal-600" />
                <span className="text-sm font-medium text-foreground">{p.label}</span>
              </Link>
            ))}
          </div>
        </section>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {SECTIONS.map(({ href, label, description, Icon }) => (
            <Link
              key={href}
              href={href}
              className="rounded-2xl border border-border bg-card p-5 hover:border-teal-300 hover:shadow-sm transition-all"
            >
              <div className="flex items-center gap-3 mb-2">
                <div className="rounded-xl bg-teal-50 p-2">
                  <Icon className="h-5 w-5 text-teal-600" />
                </div>
                <h3 className="font-semibold text-foreground">{label}</h3>
              </div>
              <p className="text-sm text-muted-foreground">{description}</p>
            </Link>
          ))}
        </div>
        <div className="mt-8 rounded-2xl border border-warning/35 bg-warning-soft p-4 text-sm text-warning-foreground">
          <strong className="block mb-1">About Nhume</strong>
          Nhume is the logistics nervous system of Impilo. It moves medicines, samples, vaccines,
          devices, blood products, documents, marketplace orders and programme commodities safely,
          visibly and accountably — with role-aware tracking, chain of custody, proof of delivery,
          and Trust-Layer-governed dispatch. Maps and ETAs are provided through Ndila; messaging is
          orchestrated through Comms Hub.
        </div>
      </PageShell>
    </AppLayout>
  );
}
