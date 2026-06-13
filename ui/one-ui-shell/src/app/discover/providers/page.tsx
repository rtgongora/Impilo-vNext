"use client";

/**
 * Find a Provider — Health OS §2
 * Search for healthcare providers by specialty, location, or availability.
 * Route: /discover/providers | Zone: discovery | Guard: auth
 */

import { UserSearch, Search, MapPin, Filter } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function DiscoverProvidersPage() {
  return (
    <AppLayout>
      <PageShell
        title="Find a Provider"
        subtitle="Search for healthcare providers by specialty, location, or availability"
        icon={<UserSearch className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Search bar */}
          <div className="flex gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                placeholder="Search by name, specialty, or condition..."
                className="w-full rounded-lg border border-border py-2.5 pl-10 pr-4 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-primary/40"
              />
            </div>
            <div className="relative w-56">
              <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                placeholder="Location..."
                className="w-full rounded-lg border border-border py-2.5 pl-10 pr-4 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-primary/40"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg border border-border px-4 py-2 text-sm text-foreground hover:bg-background transition-colors">
              <Filter className="h-4 w-4" />
              Filters
            </button>
          </div>

          {/* Specialties quick filter */}
          <div className="flex flex-wrap gap-2">
            {["General Practice", "Dentistry", "Physiotherapy", "Optometry", "Psychiatry", "Paediatrics"].map((s) => (
              <button key={s} className="rounded-full border border-border bg-card px-3 py-1 text-xs font-medium text-foreground hover:border-impilo-400 hover:text-primary transition-colors">
                {s}
              </button>
            ))}
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-border bg-background p-12 text-center">
            <UserSearch className="mx-auto h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-sm font-semibold text-foreground">Search for providers</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              Enter a specialty or provider name to find healthcare professionals near you.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
