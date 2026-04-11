"use client";

/**
 * Lab Worklist — absorbs oros-web sidecar
 * Pending and in-progress lab orders for this facility.
 * Route: /lab/worklist | Zone: lab | Guard: shift
 */

import { ListChecks, Search, Filter, Clock, CheckCircle2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function LabWorklistPage() {
  return (
    <AppLayout>
      <PageShell
        title="Lab Worklist"
        subtitle="Pending and in-progress lab orders for this facility"
        icon={<ListChecks className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Status summary */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {[
              { label: "Pending Collection", value: "0", Icon: Clock, color: "bg-amber-50 text-amber-600" },
              { label: "In Progress", value: "0", Icon: ListChecks, color: "bg-blue-50 text-blue-600" },
              { label: "Completed", value: "0", Icon: CheckCircle2, color: "bg-green-50 text-green-600" },
              { label: "Urgent", value: "0", Icon: AlertCircle, color: "bg-red-50 text-red-600" },
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
                placeholder="Search by patient name, order ID, or test..."
                className="w-full rounded-lg border border-gray-300 py-2.5 pl-10 pr-4 text-sm focus:border-violet-500 focus:outline-none focus:ring-1 focus:ring-violet-500"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
              <Filter className="h-4 w-4" />
              Filters
            </button>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <ListChecks className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-4 text-sm font-semibold text-gray-900">Worklist empty</h3>
            <p className="mt-2 text-sm text-gray-600">
              No pending lab orders for the current shift. New orders will appear here as they are placed.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
