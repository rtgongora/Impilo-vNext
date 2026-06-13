"use client";

/**
 * Browse Services — Health OS §2
 * Explore available health services and programmes.
 * Route: /discover/services | Zone: discovery | Guard: auth
 */

import { BookOpen, Search, Filter } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const CATEGORIES = [
  { label: "Primary Care", description: "General consultations, check-ups, and basic health services" },
  { label: "Preventive Care", description: "Screenings, immunizations, and wellness programmes" },
  { label: "Mental Health", description: "Counselling, psychiatric care, and support groups" },
  { label: "Maternal Health", description: "Antenatal, delivery, and postnatal care services" },
  { label: "Chronic Disease", description: "Diabetes, hypertension, and long-term condition management" },
  { label: "Rehabilitation", description: "Physiotherapy, occupational therapy, and recovery services" },
];

export default function DiscoverServicesPage() {
  return (
    <AppLayout>
      <PageShell
        title="Browse Services"
        subtitle="Explore available health services and programmes"
        icon={<BookOpen className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Search bar */}
          <div className="flex gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                placeholder="Search services..."
                className="w-full rounded-lg border border-border py-2.5 pl-10 pr-4 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-primary/40"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg border border-border px-4 py-2 text-sm text-foreground hover:bg-background transition-colors">
              <Filter className="h-4 w-4" />
              Filters
            </button>
          </div>

          {/* Service categories */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {CATEGORIES.map(({ label, description }) => (
              <div
                key={label}
                className="rounded-lg border border-border bg-card p-5 hover:border-impilo-400 hover:shadow-sm transition-all cursor-pointer"
              >
                <h3 className="font-semibold text-foreground mb-1">{label}</h3>
                <p className="text-sm text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
