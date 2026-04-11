"use client";

/**
 * My Dependants — Health OS §4
 * View and manage people you provide care for.
 * Route: /caregiving/dependants | Zone: caregiving | Guard: auth
 */

import { Users, Plus, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function DependantsPage() {
  return (
    <AppLayout>
      <PageShell
        title="My Dependants"
        subtitle="View and manage people you provide care for"
        icon={<Users className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Actions bar */}
          <div className="flex items-center justify-between">
            <div className="relative w-72">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search dependants..."
                className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg bg-purple-600 px-4 py-2 text-sm font-medium text-white hover:bg-purple-700 transition-colors">
              <Plus className="h-4 w-4" />
              Add Dependant
            </button>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <Users className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-4 text-sm font-semibold text-gray-900">No dependants yet</h3>
            <p className="mt-2 text-sm text-gray-600">
              Add family members, children, or elderly relatives you provide care for.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
