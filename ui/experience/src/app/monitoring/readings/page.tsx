"use client";

/**
 * Readings & Trends — Health OS §2
 * View vitals and measurements from connected devices.
 * Route: /monitoring/readings | Zone: monitoring | Guard: auth
 */

import { TrendingUp, Calendar, Filter } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const METRIC_TYPES = [
  { label: "Blood Pressure", unit: "mmHg", color: "bg-red-50 border-red-200" },
  { label: "Blood Glucose", unit: "mmol/L", color: "bg-amber-50 border-amber-200" },
  { label: "Heart Rate", unit: "bpm", color: "bg-pink-50 border-pink-200" },
  { label: "Oxygen Saturation", unit: "%", color: "bg-blue-50 border-blue-200" },
  { label: "Weight", unit: "kg", color: "bg-green-50 border-green-200" },
  { label: "Temperature", unit: "°C", color: "bg-orange-50 border-orange-200" },
];

export default function ReadingsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Readings & Trends"
        subtitle="View vitals and health measurements from your connected devices"
        icon={<TrendingUp className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Time range and filters */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              {["7D", "30D", "90D", "1Y"].map((range) => (
                <button
                  key={range}
                  className="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 hover:border-orange-400 hover:text-orange-600 transition-colors"
                >
                  {range}
                </button>
              ))}
            </div>
            <div className="flex items-center gap-2">
              <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                <Calendar className="h-4 w-4" />
                Date Range
              </button>
              <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                <Filter className="h-4 w-4" />
                Filter
              </button>
            </div>
          </div>

          {/* Metric cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {METRIC_TYPES.map(({ label, unit, color }) => (
              <div key={label} className={`rounded-lg border p-5 ${color}`}>
                <h3 className="font-semibold text-gray-900 mb-1">{label}</h3>
                <p className="text-xs text-gray-500 mb-3">Unit: {unit}</p>
                <p className="text-sm text-gray-500 italic">No readings yet</p>
              </div>
            ))}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
