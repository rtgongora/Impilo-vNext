"use client";

import React, { useState } from "react";
import { butanoApi, type FhirBundle, type FhirBundleEntry } from "@/lib/butanoApi";

/**
 * IPS sections we extract from the bundle, in display order.
 */
const IPS_SECTIONS = [
  { resourceType: "AllergyIntolerance", label: "Allergies" },
  { resourceType: "Condition", label: "Problems" },
  { resourceType: "MedicationRequest", label: "Medications" },
  { resourceType: "Immunization", label: "Immunizations" },
  { resourceType: "Observation", label: "Vitals & Lab Results" },
  { resourceType: "Procedure", label: "Procedures" },
  { resourceType: "CarePlan", label: "Care Plans" },
] as const;

/**
 * Extract a human-readable summary from a FHIR resource entry.
 */
function summarizeResource(resource: FhirBundleEntry["resource"]): string {
  // Try common FHIR display fields
  if (resource.code && typeof resource.code === "object") {
    const code = resource.code as { text?: string; coding?: Array<{ display?: string }> };
    if (code.text) return code.text;
    if (code.coding?.[0]?.display) return code.coding[0].display;
  }
  if (resource.medicationCodeableConcept && typeof resource.medicationCodeableConcept === "object") {
    const med = resource.medicationCodeableConcept as { text?: string; coding?: Array<{ display?: string }> };
    if (med.text) return med.text;
    if (med.coding?.[0]?.display) return med.coding[0].display;
  }
  if (resource.vaccineCode && typeof resource.vaccineCode === "object") {
    const vax = resource.vaccineCode as { text?: string; coding?: Array<{ display?: string }> };
    if (vax.text) return vax.text;
    if (vax.coding?.[0]?.display) return vax.coding[0].display;
  }
  if (typeof resource.description === "string") return resource.description;
  if (typeof resource.text === "object" && resource.text !== null) {
    const text = resource.text as { div?: string };
    if (text.div) {
      // Strip HTML tags for display
      return text.div.replace(/<[^>]*>/g, "").substring(0, 200);
    }
  }
  return `${resource.resourceType}/${resource.id ?? "unknown"}`;
}

export default function IpsPage() {
  const [cpid, setCpid] = useState("");
  const [bundle, setBundle] = useState<FhirBundle | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set());

  const toggleSection = (sectionType: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(sectionType)) {
        next.delete(sectionType);
      } else {
        next.add(sectionType);
      }
      return next;
    });
  };

  const handleGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!cpid.trim()) return;

    setLoading(true);
    setError(null);
    setBundle(null);
    setExpandedSections(new Set());

    try {
      const data = await butanoApi.getIpsSummary(cpid.trim());
      setBundle(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate IPS bundle");
    } finally {
      setLoading(false);
    }
  };

  /**
   * Group bundle entries by resource type.
   */
  const sectionEntries = (resourceType: string): FhirBundleEntry[] => {
    if (!bundle?.entry) return [];
    return bundle.entry.filter((e) => e.resource.resourceType === resourceType);
  };

  return (
    <div className="max-w-5xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          IPS Bundle Viewer
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Generate and inspect an International Patient Summary for a patient by CPID
        </p>
      </div>

      {/* Search form */}
      <form onSubmit={handleGenerate} className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6 mb-6">
        <div className="flex gap-3 items-end">
          <div className="flex-1">
            <label htmlFor="cpid" className="block text-sm font-medium text-neutral-700 mb-1">
              CPID
            </label>
            <input
              id="cpid"
              type="text"
              value={cpid}
              onChange={(e) => setCpid(e.target.value)}
              placeholder="CPID-00000001"
              className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
              required
            />
          </div>
          <button
            type="submit"
            disabled={loading || !cpid.trim()}
            className="px-6 py-2 text-sm font-medium text-white bg-brand-primary rounded-lg hover:bg-brand-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {loading ? "Generating..." : "Generate IPS"}
          </button>
        </div>
      </form>

      {/* Error */}
      {error && (
        <div className="bg-danger/5 border border-danger/20 text-danger text-sm rounded-lg px-4 py-3 mb-6">
          {error}
        </div>
      )}

      {/* IPS Sections */}
      {bundle && (
        <div className="space-y-3">
          <div className="text-sm text-neutral-500 mb-2">
            Bundle contains {bundle.entry?.length ?? 0} resources (type: {bundle.type})
          </div>

          {IPS_SECTIONS.map(({ resourceType, label }) => {
            const entries = sectionEntries(resourceType);
            const isExpanded = expandedSections.has(resourceType);

            return (
              <div
                key={resourceType}
                className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 overflow-hidden"
              >
                <button
                  onClick={() => toggleSection(resourceType)}
                  className="w-full flex items-center justify-between px-5 py-4 text-left hover:bg-neutral-50 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <h2 className="text-sm font-semibold text-neutral-900">{label}</h2>
                    <span className="inline-flex px-2 py-0.5 rounded-full text-[11px] font-medium bg-neutral-100 text-neutral-600">
                      {entries.length}
                    </span>
                  </div>
                  <svg
                    className={`w-4 h-4 text-neutral-400 transition-transform ${isExpanded ? "rotate-180" : ""}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth={2}
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                  </svg>
                </button>

                {isExpanded && (
                  <div className="border-t border-neutral-100">
                    {entries.length === 0 ? (
                      <div className="px-5 py-4 text-sm text-neutral-400">
                        No {label.toLowerCase()} found in the IPS bundle.
                      </div>
                    ) : (
                      <div className="divide-y divide-neutral-50">
                        {entries.map((entry, idx) => (
                          <div key={entry.resource.id ?? idx} className="px-5 py-3">
                            <p className="text-sm text-neutral-900">
                              {summarizeResource(entry.resource)}
                            </p>
                            <div className="flex gap-3 mt-1 text-[11px] text-neutral-400">
                              {entry.resource.id && <span>ID: {entry.resource.id}</span>}
                              {entry.fullUrl && <span className="truncate max-w-xs">{entry.fullUrl}</span>}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
