"use client";

/**
 * Patient Monitoring Dashboard — Health OS §2
 * Provider-facing dashboard for monitoring enrolled patients remotely.
 * Route: /monitoring/provider-dashboard | Zone: monitoring | Guard: shift
 */

import { LayoutDashboard, Search, Filter, Users, AlertTriangle, Activity } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function ProviderDashboardPage() {
  return (
    <AppLayout>
      <PageShell
        title="Patient Monitoring Dashboard"
        subtitle="Remote patient monitoring overview for clinical staff"
        icon={<LayoutDashboard className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Summary cards */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {[
              { label: "Enrolled Patients", value: "0", Icon: Users, color: "bg-impilo-50 text-impilo-500" },
              { label: "Active Alerts", value: "0", Icon: AlertTriangle, color: "bg-red-50 text-red-600" },
              { label: "Readings Today", value: "0", Icon: Activity, color: "bg-green-50 text-green-600" },
              { label: "Pending Review", value: "0", Icon: Filter, color: "bg-amber-50 text-amber-600" },
            ].map(({ label, value, Icon, color }) => (
              <div key={label} className="rounded-lg border border-gray-200 bg-white p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-gray-500">{label}</span>
                  <div className={`rounded-lg p-1.5 ${color.split(" ")[0]}`}>
                    <Icon className={`h-4 w-4 ${color.split(" ")[1]}`} />
                  </div>
                </div>
                <p className="text-2xl font-bold text-gray-900">{value}</p>
              </div>
            ))}
          </div>

          {/* Search and filter */}
          <div className="flex gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search patients by name or Health ID..."
                className="w-full rounded-lg border border-gray-300 py-2.5 pl-10 pr-4 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-400"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
              <Filter className="h-4 w-4" />
              Filters
            </button>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <LayoutDashboard className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-4 text-sm font-semibold text-gray-900">No monitored patients</h3>
            <p className="mt-2 text-sm text-gray-600">
              Patients enrolled in remote monitoring programmes with connected devices will appear here.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
