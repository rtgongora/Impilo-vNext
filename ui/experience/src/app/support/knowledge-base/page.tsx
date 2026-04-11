"use client";

/**
 * Knowledge Base — absorbs support-console sidecar
 * Help articles, guides, and FAQs.
 * Route: /support/knowledge-base | Zone: support | Guard: auth
 */

import { BookOpen, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const CATEGORIES = [
  { label: "Getting Started", description: "Account setup, first login, and navigation guides", articles: 0 },
  { label: "Clinical Workflows", description: "Patient registration, encounters, orders, and prescriptions", articles: 0 },
  { label: "Identity & Access", description: "Health ID, provider activation, role management", articles: 0 },
  { label: "Marketplace & Orders", description: "Placing orders, tracking deliveries, vendor management", articles: 0 },
  { label: "Finance & Billing", description: "Claims, payments, reconciliation, and tariffs", articles: 0 },
  { label: "System Administration", description: "User management, policies, audit, and integrations", articles: 0 },
  { label: "Devices & Monitoring", description: "Pairing devices, reading data, alert thresholds", articles: 0 },
  { label: "Troubleshooting", description: "Common issues, error resolution, and system status", articles: 0 },
];

export default function KnowledgeBasePage() {
  return (
    <AppLayout>
      <PageShell
        title="Knowledge Base"
        subtitle="Help articles, user guides, and frequently asked questions"
        icon={<BookOpen className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Search */}
          <div className="relative w-full md:w-96">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search help articles..."
              className="w-full rounded-lg border border-gray-300 py-2.5 pl-10 pr-4 text-sm focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
            />
          </div>

          {/* Categories */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {CATEGORIES.map(({ label, description, articles }) => (
              <div
                key={label}
                className="rounded-lg border border-gray-200 bg-white p-5 hover:border-teal-400 hover:shadow-sm transition-all cursor-pointer"
              >
                <h3 className="font-semibold text-gray-900 mb-1">{label}</h3>
                <p className="text-sm text-gray-600 mb-3">{description}</p>
                <span className="text-xs text-gray-400">{articles} articles</span>
              </div>
            ))}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
