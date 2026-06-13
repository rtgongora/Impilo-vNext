"use client";

/**
 * Equipment Management — absorbs ops-console sidecar
 * Clinical equipment: calibration, maintenance, device linkage.
 * Route: /operations/equipment | Zone: operations | Guard: role ADMIN
 */

import { Wrench, Search, Plus, Filter, Calendar, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function EquipmentPage() {
  return (
    <AppLayout>
      <PageShell
        title="Equipment Management"
        subtitle="Clinical equipment calibration, maintenance schedules, and device linkage"
        icon={<Wrench className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Summary */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {[
              { label: "Total Equipment", value: "0" },
              { label: "Operational", value: "0" },
              { label: "Due Calibration", value: "0", alert: true },
              { label: "Under Maintenance", value: "0" },
            ].map(({ label, value, alert }) => (
              <div key={label} className={`rounded-lg border bg-card p-4 ${alert ? "border-warning/35" : "border-border"}`}>
                <div className="flex items-center justify-between">
                  <p className="text-sm text-muted-foreground">{label}</p>
                  {alert && <AlertTriangle className="h-4 w-4 text-amber-500" />}
                </div>
                <p className="text-2xl font-bold text-foreground">{value}</p>
              </div>
            ))}
          </div>

          {/* Actions bar */}
          <div className="flex items-center justify-between">
            <div className="flex gap-3">
              <div className="relative w-80">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <input
                  type="text"
                  placeholder="Search equipment by name or serial..."
                  className="w-full rounded-lg border border-border py-2 pl-10 pr-4 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                />
              </div>
              <button className="inline-flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-background transition-colors">
                <Filter className="h-4 w-4" />
                Filters
              </button>
            </div>
            <div className="flex items-center gap-2">
              <button className="inline-flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-background transition-colors">
                <Calendar className="h-4 w-4" />
                Maintenance Schedule
              </button>
              <button className="inline-flex items-center gap-2 rounded-lg bg-slate-700 px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover transition-colors">
                <Plus className="h-4 w-4" />
                Register Equipment
              </button>
            </div>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-border bg-background p-12 text-center">
            <Wrench className="mx-auto h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-sm font-semibold text-foreground">No equipment registered</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              Register clinical equipment to track calibration schedules, maintenance history, and device linkage.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
