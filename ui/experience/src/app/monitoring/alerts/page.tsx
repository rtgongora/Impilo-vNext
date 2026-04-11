"use client";

/**
 * Monitoring Alerts — Health OS §2
 * Threshold breaches and care escalations from remote monitoring.
 * Route: /monitoring/alerts | Zone: monitoring | Guard: auth
 */

import { AlertTriangle, Filter, CheckCheck, Bell } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function MonitoringAlertsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Monitoring Alerts"
        subtitle="Threshold breaches, anomaly detections, and care escalations"
        icon={<AlertTriangle className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Alert severity summary */}
          <div className="grid grid-cols-3 gap-4">
            {[
              { label: "Critical", count: 0, color: "border-red-200 bg-red-50 text-red-700" },
              { label: "Warning", count: 0, color: "border-amber-200 bg-amber-50 text-amber-700" },
              { label: "Info", count: 0, color: "border-blue-200 bg-blue-50 text-blue-700" },
            ].map(({ label, count, color }) => (
              <div key={label} className={`rounded-lg border p-4 text-center ${color}`}>
                <p className="text-2xl font-bold">{count}</p>
                <p className="text-sm font-medium">{label}</p>
              </div>
            ))}
          </div>

          {/* Actions */}
          <div className="flex items-center justify-between">
            <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
              <Filter className="h-4 w-4" />
              Filter
            </button>
            <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
              <CheckCheck className="h-4 w-4" />
              Acknowledge All
            </button>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <Bell className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-4 text-sm font-semibold text-gray-900">No alerts</h3>
            <p className="mt-2 text-sm text-gray-600">
              When connected devices detect abnormal readings or threshold breaches, alerts will appear here.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
