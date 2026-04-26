"use client";

/**
 * Care Tasks — Health OS §4
 * View and complete assigned care tasks for dependants.
 * Route: /caregiving/tasks | Zone: caregiving | Guard: role CAREGIVER
 */

import { ListChecks, CheckCircle2, Clock, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const FILTERS = [
  { label: "All Tasks", count: 0, Icon: ListChecks, color: "text-gray-600 bg-gray-50" },
  { label: "Pending", count: 0, Icon: Clock, color: "text-amber-600 bg-amber-50" },
  { label: "Overdue", count: 0, Icon: AlertCircle, color: "text-red-600 bg-red-50" },
  { label: "Completed", count: 0, Icon: CheckCircle2, color: "text-green-600 bg-green-50" },
];

export default function CareTasksPage() {
  return (
    <AppLayout>
      <PageShell
        title="Care Tasks"
        subtitle="View and complete assigned care tasks for your dependants"
        icon={<ListChecks className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Status filters */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {FILTERS.map(({ label, count, Icon, color }) => (
              <button
                key={label}
                className="flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-4 hover:border-purple-400 transition-colors text-left"
              >
                <div className={`rounded-lg p-2 ${color.split(" ")[1]}`}>
                  <Icon className={`h-5 w-5 ${color.split(" ")[0]}`} />
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-900">{label}</p>
                  <p className="text-xs text-gray-500">{count} tasks</p>
                </div>
              </button>
            ))}
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <ListChecks className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-4 text-sm font-semibold text-gray-900">No care tasks</h3>
            <p className="mt-2 text-sm text-gray-600">
              Tasks such as medication reminders, appointment follow-ups, and health checks will appear here.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
