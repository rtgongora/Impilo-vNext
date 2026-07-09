"use client";

/**
 * Chronic Care Plans — Health OS §2
 * Active care plans linked to remote monitoring.
 * Route: /monitoring/care-plans | Zone: monitoring | Guard: auth
 */

import { FileHeart, Plus, Search } from "lucide-react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function MonitoringCarePlansPage() {
  return (
    <AppLayout>
      <PageShell
        title="Chronic Care Plans"
        subtitle="Active care plans linked to remote monitoring and device thresholds"
        icon={<FileHeart className="h-6 w-6" />}
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
        <div className="space-y-6">
          {/* Actions bar */}
          <div className="flex items-center justify-between">
            <div className="relative w-72">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                placeholder="Search care plans..."
                className="w-full rounded-lg border border-border py-2 pl-10 pr-4 text-sm focus:border-orange-500 focus:outline-none focus:ring-1 focus:ring-orange-500"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-700 transition-colors">
              <Plus className="h-4 w-4" />
              New Care Plan
            </button>
          </div>

          {/* Care plan categories */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {[
              { title: "Diabetes Management", description: "Blood glucose monitoring, insulin tracking, HbA1c goals" },
              { title: "Hypertension Control", description: "Blood pressure monitoring, medication adherence, lifestyle targets" },
              { title: "Heart Failure", description: "Weight monitoring, fluid management, activity tracking" },
              { title: "COPD Management", description: "Oxygen saturation, peak flow monitoring, exacerbation tracking" },
            ].map((plan) => (
              <GlassSurface key={plan.title} className="p-5 transition-all hover:shadow-glow-teal cursor-pointer">
                <h3 className="font-semibold text-foreground mb-1">{plan.title}</h3>
                <p className="text-sm text-muted-foreground">{plan.description}</p>
                <p className="mt-3 text-xs text-muted-foreground">No active plans</p>
              </GlassSurface>
            ))}
          </div>
        </div>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
