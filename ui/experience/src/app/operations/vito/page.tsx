"use client";

/**
 * Identity Operations (VITO) — absorbs ops-console sidecar
 * Client registry operations: dedup, issuance queue, match review, card management.
 * Route: /operations/vito | Zone: operations | Guard: role ADMIN
 */

import { Users, Search, Filter, GitMerge, CreditCard, UserCheck, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { label: "Deduplication Queue", description: "Review and resolve potential duplicate person records", Icon: GitMerge, color: "bg-amber-50 text-amber-600" },
  { label: "Health ID Issuance", description: "Pending Health ID issuance requests and approvals", Icon: CreditCard, color: "bg-impilo-50 text-impilo-500" },
  { label: "Match Review", description: "Manual review of probabilistic identity matches", Icon: UserCheck, color: "bg-green-50 text-green-600" },
  { label: "Identity Exceptions", description: "Flagged identity records requiring administrative action", Icon: AlertCircle, color: "bg-red-50 text-red-600" },
];

export default function VitoOpsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Identity Operations"
        subtitle="VITO client registry: deduplication, Health ID issuance, match review"
        icon={<Users className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Search */}
          <div className="flex gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search by Health ID or person name..."
                className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
              <Filter className="h-4 w-4" />
              Filters
            </button>
          </div>

          {/* Operation sections */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {SECTIONS.map(({ label, description, Icon, color }) => (
              <div
                key={label}
                className="rounded-lg border border-gray-200 bg-white p-5 hover:border-slate-400 hover:shadow-sm transition-all cursor-pointer"
              >
                <div className="flex items-center gap-3 mb-2">
                  <div className={`rounded-lg p-2 ${color.split(" ")[0]}`}>
                    <Icon className={`h-5 w-5 ${color.split(" ")[1]}`} />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900">{label}</h3>
                    <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">0 pending</span>
                  </div>
                </div>
                <p className="text-sm text-gray-600">{description}</p>
              </div>
            ))}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
