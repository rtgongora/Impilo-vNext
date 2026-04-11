"use client";

/**
 * Test Catalog — absorbs oros-web sidecar
 * Available lab tests and capabilities for this facility.
 * Route: /lab/catalog | Zone: lab | Guard: shift
 */

import { BookOpen, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const CATEGORIES = [
  { label: "Haematology", description: "FBC, ESR, coagulation studies, blood film", count: 0 },
  { label: "Chemistry", description: "U&E, LFTs, lipids, glucose, HbA1c", count: 0 },
  { label: "Microbiology", description: "Culture & sensitivity, microscopy, rapid antigen", count: 0 },
  { label: "Immunology", description: "HIV, hepatitis, autoimmune panels, allergy testing", count: 0 },
  { label: "Histopathology", description: "Biopsy, cytology, frozen section", count: 0 },
  { label: "Point of Care", description: "Rapid tests, urinalysis, pregnancy, malaria", count: 0 },
];

export default function LabCatalogPage() {
  return (
    <AppLayout>
      <PageShell
        title="Test Catalog"
        subtitle="Available laboratory tests and facility capabilities"
        icon={<BookOpen className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Search */}
          <div className="relative w-full md:w-96">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search tests by name or code..."
              className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-violet-500 focus:outline-none focus:ring-1 focus:ring-violet-500"
            />
          </div>

          {/* Test categories */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {CATEGORIES.map(({ label, description, count }) => (
              <div
                key={label}
                className="rounded-lg border border-gray-200 bg-white p-5 hover:border-violet-400 hover:shadow-sm transition-all cursor-pointer"
              >
                <div className="flex items-center justify-between mb-1">
                  <h3 className="font-semibold text-gray-900">{label}</h3>
                  <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">{count} tests</span>
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
