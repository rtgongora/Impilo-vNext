"use client";

/**
 * Service Discovery Hub — Health OS §2
 * Find providers, facilities, and health services.
 * Route: /discover | Zone: discovery | Guard: auth
 */

import Link from "next/link";
import { Compass, UserSearch, Building, BookOpen } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/discover/providers", label: "Find a Provider", description: "Search by specialty, location, or availability", Icon: UserSearch },
  { href: "/discover/facilities", label: "Find a Facility", description: "Locate clinics, hospitals, and service points", Icon: Building },
  { href: "/discover/services", label: "Browse Services", description: "Explore available health services and programmes", Icon: BookOpen },
];

export default function DiscoverPage() {
  return (
    <AppLayout>
      <PageShell title="Find Services" subtitle="Health OS §2 — Discover providers, facilities, and health services" icon={<Compass className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {SECTIONS.map(({ href, label, description, Icon }) => (
            <Link key={href} href={href} className="rounded-lg border border-border bg-card p-5 hover:border-impilo-400 hover:shadow-sm transition-all">
              <div className="flex items-center gap-3 mb-2">
                <div className="rounded-lg bg-primary-soft p-2"><Icon className="h-5 w-5 text-primary" /></div>
                <h3 className="font-semibold text-foreground">{label}</h3>
              </div>
              <p className="text-sm text-muted-foreground">{description}</p>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
