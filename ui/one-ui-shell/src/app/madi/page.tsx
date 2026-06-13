"use client";

import Link from "next/link";
import {
  Droplet,
  LayoutDashboard,
  Heart,
  CalendarDays,
  Building2,
  ClipboardList,
  Activity,
  FlaskConical,
  ShieldAlert,
  Truck,
  Landmark,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const WORK_SECTIONS = [
  { href: "/madi/dashboard", label: "Operations Dashboard", description: "Donors, drives, orders and stock at a glance", Icon: LayoutDashboard },
  { href: "/madi/drives", label: "Donation Drives", description: "Plan, publish and run community blood drives", Icon: CalendarDays },
  { href: "/madi/blood-bank", label: "Local Blood Bank", description: "Stock, crossmatch, issue and reconcile units", Icon: Building2 },
  { href: "/madi/orders", label: "Order Blood", description: "Clinical blood product requests for inpatients", Icon: ClipboardList },
  { href: "/madi/transfusion", label: "Record Transfusion", description: "Start episodes, observations and verification", Icon: Activity },
  { href: "/madi/processing", label: "Blood Processing", description: "Receive, test, quarantine and prepare components", Icon: FlaskConical },
  { href: "/madi/haemovigilance", label: "Haemovigilance", description: "Report reactions and investigate cases", Icon: ShieldAlert },
  { href: "/madi/central-bank", label: "Central Blood Bank", description: "National inventory and oversight metrics", Icon: Landmark },
  { href: "/madi/logistics", label: "Blood Logistics", description: "Dispatch blood products via Nhume last-mile", Icon: Truck },
];

const LIFE_SECTIONS = [
  { href: "/madi/donor", label: "My Donor Hub", description: "Eligibility, history and preferences", Icon: Heart },
  { href: "/madi/donor/register", label: "Become a Donor", description: "Register to give blood safely and voluntarily", Icon: Droplet },
  { href: "/madi/donor/drives", label: "Find a Drive", description: "Discover donation events near you", Icon: CalendarDays },
];

export default function MadiHubPage() {
  return (
    <AppLayout>
      <PageShell
        title="Madi"
        subtitle="Blood donation, transfusion safety and haemovigilance"
        serviceSlug="madi"
      >
        <div className="space-y-8">
          <section>
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground mb-3">Work</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {WORK_SECTIONS.map(({ href, label, description, Icon }) => (
                <Link
                  key={href}
                  href={href}
                  className="rounded-2xl border border-border bg-card p-5 hover:border-rose-300 hover:shadow-sm transition-all"
                >
                  <div className="flex items-center gap-3 mb-2">
                    <div className="rounded-xl bg-danger-soft p-2">
                      <Icon className="h-5 w-5 text-rose-600" />
                    </div>
                    <h3 className="font-semibold text-foreground">{label}</h3>
                  </div>
                  <p className="text-sm text-muted-foreground">{description}</p>
                </Link>
              ))}
            </div>
          </section>

          <section>
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground mb-3">My Life — Donors</h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {LIFE_SECTIONS.map(({ href, label, description, Icon }) => (
                <Link
                  key={href}
                  href={href}
                  className="rounded-2xl border border-border bg-card p-5 hover:border-rose-300 hover:shadow-sm transition-all"
                >
                  <div className="flex items-center gap-3 mb-2">
                    <div className="rounded-xl bg-danger-soft p-2">
                      <Icon className="h-5 w-5 text-rose-600" />
                    </div>
                    <h3 className="font-semibold text-foreground">{label}</h3>
                  </div>
                  <p className="text-sm text-muted-foreground">{description}</p>
                </Link>
              ))}
            </div>
          </section>

          <div className="rounded-2xl border border-danger/28 bg-danger-soft p-4 text-sm text-danger">
            <strong className="block mb-1">About Madi</strong>
            Madi connects voluntary donors, facility blood banks, and clinical teams under
            governed trust headers. Donor-facing surfaces use privacy-safe language; clinical
            workflows require facility context and auditable actions.
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
