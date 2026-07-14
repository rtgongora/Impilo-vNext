"use client";

import Link from "next/link";
import { ArrowRight, Building, MonitorSmartphone } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { DiscoverFacilitiesMapPanel } from "@/components/maps/DiscoverFacilitiesMapPanel";

export default function DiscoverFacilitiesPage() {
  return (
    <AppLayout>
      <PageShell
        title="Find a Facility"
        subtitle="Locate clinics, hospitals, and health service points on governed Ndila maps"
        icon={<Building className="h-6 w-6" />}
      >
        <div className="space-y-4">
          {/* Virtual care: no travel needed — the virtual-hospital lane. */}
          <section aria-label="Virtual care">
            <Link
              href="/discover/virtual-care"
              className="flex items-center justify-between gap-3 rounded-xl border border-teal-200 bg-teal-50 p-4 hover:border-teal-300"
            >
              <div className="flex items-center gap-3">
                <MonitorSmartphone className="h-6 w-6 shrink-0 text-teal-600" aria-hidden />
                <div>
                  <h2 className="text-sm font-semibold text-slate-900">Virtual care</h2>
                  <p className="text-xs text-slate-600">
                    Can&apos;t travel? Request a teleconsult from a virtual hospital instead.
                  </p>
                </div>
              </div>
              <ArrowRight className="h-4 w-4 shrink-0 text-teal-600" aria-hidden />
            </Link>
          </section>

          <DiscoverFacilitiesMapPanel />
        </div>
      </PageShell>
    </AppLayout>
  );
}
