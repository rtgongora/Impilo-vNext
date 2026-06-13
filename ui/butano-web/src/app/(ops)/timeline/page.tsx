"use client";

import React, { useCallback, useState } from "react";
import { butanoApi, type TimelineItem, type TimelineResponse } from "@/lib/butanoApi";

/**
 * FHIR resource types available for timeline filtering.
 */
const RESOURCE_TYPES = [
  "Encounter",
  "Observation",
  "Condition",
  "MedicationRequest",
  "Immunization",
  "AllergyIntolerance",
  "Procedure",
  "DiagnosticReport",
  "CarePlan",
] as const;

/**
 * Color mapping for resource type badges in the timeline.
 */
const TYPE_COLORS: Record<string, string> = {
  Encounter: "bg-info/10 text-info",
  Observation: "bg-brand-primary/10 text-brand-primary",
  Condition: "bg-brand-accent-red/10 text-brand-accent-red",
  MedicationRequest: "bg-purple-100 text-warning-foreground",
  Immunization: "bg-success/10 text-success",
  AllergyIntolerance: "bg-warning/10 text-warning",
  Procedure: "bg-indigo-100 text-primary-hover",
  DiagnosticReport: "bg-teal-100 text-teal-700",
  CarePlan: "bg-cyan-100 text-cyan-700",
};

export default function TimelinePage() {
  const [cpid, setCpid] = useState("");
  const [since, setSince] = useState("");
  const [selectedTypes, setSelectedTypes] = useState<Set<string>>(new Set());
  const [page, setPage] = useState(0);
  const [timeline, setTimeline] = useState<TimelineResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggleType = (type: string) => {
    setSelectedTypes((prev) => {
      const next = new Set(prev);
      if (next.has(type)) {
        next.delete(type);
      } else {
        next.add(type);
      }
      return next;
    });
  };

  const fetchTimeline = useCallback(
    async (pageNum: number) => {
      if (!cpid.trim()) return;
      setLoading(true);
      setError(null);
      try {
        const data = await butanoApi.getTimeline(cpid.trim(), {
          since: since || undefined,
          type: selectedTypes.size > 0 ? Array.from(selectedTypes).join(",") : undefined,
          page: pageNum,
          size: 20,
        });
        setTimeline(data);
        setPage(pageNum);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to fetch timeline");
        setTimeline(null);
      } finally {
        setLoading(false);
      }
    },
    [cpid, since, selectedTypes],
  );

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    fetchTimeline(0);
  };

  return (
    <div className="max-w-5xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          CPID Timeline Viewer
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Chronological view of all clinical resources for a patient by CPID
        </p>
      </div>

      {/* Search form */}
      <form onSubmit={handleSearch} className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6 mb-6">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <div>
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
          <div>
            <label htmlFor="since" className="block text-sm font-medium text-neutral-700 mb-1">
              Since (date)
            </label>
            <input
              id="since"
              type="date"
              value={since}
              onChange={(e) => setSince(e.target.value)}
              className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
            />
          </div>
          <div className="flex items-end">
            <button
              type="submit"
              disabled={loading || !cpid.trim()}
              className="w-full px-4 py-2 text-sm font-medium text-white bg-brand-primary rounded-lg hover:bg-brand-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? "Loading..." : "Search Timeline"}
            </button>
          </div>
        </div>

        {/* Resource type filter */}
        <div>
          <p className="text-xs font-medium text-neutral-500 mb-2">Filter by resource type</p>
          <div className="flex flex-wrap gap-2">
            {RESOURCE_TYPES.map((type) => {
              const isSelected = selectedTypes.has(type);
              return (
                <button
                  key={type}
                  type="button"
                  onClick={() => toggleType(type)}
                  className={`px-3 py-1 text-xs font-medium rounded-full border transition-colors ${
                    isSelected
                      ? "bg-brand-primary text-white border-brand-primary"
                      : "bg-card text-neutral-600 border-neutral-200 hover:border-brand-primary/40"
                  }`}
                >
                  {type}
                </button>
              );
            })}
          </div>
        </div>
      </form>

      {/* Error */}
      {error && (
        <div className="bg-danger/5 border border-danger/20 text-danger text-sm rounded-lg px-4 py-3 mb-6">
          {error}
        </div>
      )}

      {/* Timeline */}
      {timeline && (
        <div className="space-y-0">
          <div className="text-sm text-neutral-500 mb-4">
            Showing {timeline.items.length} of {timeline.totalElements} resources
            (page {timeline.page + 1} of {timeline.totalPages})
          </div>

          {timeline.items.length === 0 ? (
            <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-8 text-center text-neutral-500 text-sm">
              No timeline entries found for this CPID with the given filters.
            </div>
          ) : (
            <div className="relative">
              {/* Vertical line */}
              <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-neutral-200" />

              <div className="space-y-4">
                {timeline.items.map((item: TimelineItem, idx: number) => (
                  <div key={`${item.resourceId}-${idx}`} className="relative pl-10">
                    {/* Dot on timeline */}
                    <div className="absolute left-2.5 top-4 w-3 h-3 rounded-full bg-card border-2 border-brand-primary" />

                    <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-4">
                      <div className="flex items-center gap-2 mb-1">
                        <span
                          className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                            TYPE_COLORS[item.resourceType] ?? "bg-neutral-100 text-neutral-700"
                          }`}
                        >
                          {item.resourceType}
                        </span>
                        <span className="text-xs text-neutral-400">
                          {new Date(item.date).toLocaleString()}
                        </span>
                      </div>
                      <p className="text-sm text-neutral-900">{item.summary}</p>
                      <div className="flex gap-3 mt-1.5 text-[11px] text-neutral-400">
                        <span>ID: {item.resourceId}</span>
                        {item.encounterRef && <span>Encounter: {item.encounterRef}</span>}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Pagination */}
          {timeline.totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 mt-6">
              <button
                onClick={() => fetchTimeline(page - 1)}
                disabled={page === 0 || loading}
                className="px-3 py-1.5 text-xs font-medium text-neutral-600 bg-card border border-neutral-200 rounded-lg hover:bg-neutral-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <span className="text-xs text-neutral-500">
                Page {page + 1} of {timeline.totalPages}
              </span>
              <button
                onClick={() => fetchTimeline(page + 1)}
                disabled={!timeline.hasNext || loading}
                className="px-3 py-1.5 text-xs font-medium text-neutral-600 bg-card border border-neutral-200 rounded-lg hover:bg-neutral-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Next
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
