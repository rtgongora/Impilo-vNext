"use client";

/**
 * API Catalog — Health OS §10
 * Browse and explore Health OS API endpoints.
 * Route: /developer/api-catalog | Zone: developer | Guard: role ADMIN
 */

import { BookOpen, Search, ExternalLink } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const APIS = [
  { name: "TSHEPO — Trust & Authorization", version: "v1", description: "Policy engine, RBAC/ABAC, consent, audit trail", status: "Stable" },
  { name: "VITO — Identity Registry", version: "v1", description: "Person identity, Health ID issuance, deduplication", status: "Stable" },
  { name: "VARAPI — Provider Registry", version: "v1", description: "Provider registration, credentialing, practice management", status: "Stable" },
  { name: "TUSO — Facility Registry", version: "v1", description: "Facility registration, location services, operating hours", status: "Stable" },
  { name: "BUTANO — Shared Health Record", version: "v1", description: "HAPI FHIR facade, clinical data, observations", status: "Stable" },
  { name: "OROS — Laboratory", version: "v1", description: "Lab orders, results, worklists, test catalog", status: "Beta" },
  { name: "Msika — Marketplace", version: "v1", description: "Product catalog, orders, vendor management", status: "Stable" },
  { name: "PCT — Claims & Billing", version: "v1", description: "Claims submission, adjudication, billing", status: "Stable" },
];

export default function ApiCatalogPage() {
  return (
    <AppLayout>
      <PageShell
        title="API Catalog"
        subtitle="Browse and explore Health OS API endpoints and integration guides"
        icon={<BookOpen className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Search */}
          <div className="relative w-full md:w-96">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search APIs..."
              className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>

          {/* API listing */}
          <div className="space-y-3">
            {APIS.map((api) => (
              <div
                key={api.name}
                className="flex items-center justify-between rounded-lg border border-gray-200 bg-white p-4 hover:border-indigo-400 hover:shadow-sm transition-all cursor-pointer"
              >
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="font-semibold text-gray-900">{api.name}</h3>
                    <span className="rounded bg-gray-100 px-2 py-0.5 text-xs font-mono text-gray-600">{api.version}</span>
                    <span className={`rounded px-2 py-0.5 text-xs font-medium ${api.status === "Stable" ? "bg-green-50 text-green-700" : "bg-amber-50 text-amber-700"}`}>
                      {api.status}
                    </span>
                  </div>
                  <p className="text-sm text-gray-600">{api.description}</p>
                </div>
                <ExternalLink className="h-4 w-4 text-gray-400 shrink-0 ml-4" />
              </div>
            ))}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
