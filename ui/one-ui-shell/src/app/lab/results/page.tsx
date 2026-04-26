"use client";

/**
 * Results Review — absorbs oros-web sidecar
 * Review and authorize lab results.
 * Route: /lab/results | Zone: lab | Guard: shift
 */

import { FileSearch, Search, Filter, CheckSquare } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function LabResultsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Results Review"
        subtitle="Review, validate, and authorize laboratory results"
        icon={<FileSearch className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Actions bar */}
          <div className="flex items-center justify-between">
            <div className="flex gap-3">
              <div className="relative w-80">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                <input
                  type="text"
                  placeholder="Search results by patient or test..."
                  className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-violet-500 focus:outline-none focus:ring-1 focus:ring-violet-500"
                />
              </div>
              <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                <Filter className="h-4 w-4" />
                Filters
              </button>
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 transition-colors">
              <CheckSquare className="h-4 w-4" />
              Authorize Selected
            </button>
          </div>

          {/* Results table placeholder */}
          <div className="rounded-lg border border-gray-200 bg-white">
            <div className="border-b border-gray-200 px-4 py-3 grid grid-cols-6 gap-4 text-xs font-semibold text-gray-500 uppercase">
              <span>Order ID</span>
              <span>Patient</span>
              <span>Test</span>
              <span>Collected</span>
              <span>Status</span>
              <span>Actions</span>
            </div>
            <div className="p-12 text-center">
              <p className="text-sm text-gray-500">No results pending review</p>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
