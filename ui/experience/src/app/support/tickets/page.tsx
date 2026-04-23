"use client";

/**
 * Support Tickets — absorbs support-console sidecar
 * Create, track, and resolve support requests.
 * Route: /support/tickets | Zone: support | Guard: auth
 */

import { Ticket, Plus, Search, Filter, Clock, CheckCircle2 } from "lucide-react";
import { HelpdeskLearningSuggestions } from "@/components/learning/HelpdeskLearningSuggestions";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { HelpdeskIntelligenceAssist } from "@/components/support/HelpdeskIntelligenceAssist";

export default function TicketsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Support Tickets"
        subtitle="Create, track, and resolve support requests"
        icon={<Ticket className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <HelpdeskIntelligenceAssist />
          <HelpdeskLearningSuggestions issueType="GENERAL" title="Suggested training before you open a ticket" />

          {/* Status summary */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {[
              { label: "Open", value: "0", Icon: Clock, color: "bg-impilo-50 text-impilo-500" },
              { label: "In Progress", value: "0", Icon: Clock, color: "bg-amber-50 text-amber-600" },
              { label: "Resolved", value: "0", Icon: CheckCircle2, color: "bg-green-50 text-green-600" },
              { label: "Total", value: "0", Icon: Ticket, color: "bg-gray-50 text-gray-600" },
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

          {/* Actions bar */}
          <div className="flex items-center justify-between">
            <div className="flex gap-3">
              <div className="relative w-72">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                <input
                  type="text"
                  placeholder="Search tickets..."
                  className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
                />
              </div>
              <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                <Filter className="h-4 w-4" />
                Filters
              </button>
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 transition-colors">
              <Plus className="h-4 w-4" />
              New Ticket
            </button>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <Ticket className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-4 text-sm font-semibold text-gray-900">No support tickets</h3>
            <p className="mt-2 text-sm text-gray-600">
              Create a support ticket for system issues, access requests, or general assistance.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
