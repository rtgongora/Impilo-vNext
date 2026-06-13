"use client";

import React, { useCallback, useEffect, useState } from "react";
import { butanoApi, type ResourceStats } from "@/lib/butanoApi";

/**
 * Color classes for resource count magnitude.
 * Higher counts get warmer colors to indicate "heavier" resource types.
 */
function magnitudeColor(count: number): string {
  if (count >= 100_000) return "bg-brand-accent-red/10 text-brand-accent-red border-brand-accent-red/20";
  if (count >= 10_000) return "bg-warning/10 text-warning border-warning/20";
  if (count >= 1_000) return "bg-brand-primary/10 text-brand-primary border-brand-primary/20";
  if (count >= 100) return "bg-info/10 text-info border-info/20";
  return "bg-neutral-100 text-neutral-700 border-neutral-200";
}

/**
 * Format large numbers with locale separators.
 */
function formatCount(count: number): string {
  return count.toLocaleString();
}

export default function StatsPage() {
  const [stats, setStats] = useState<ResourceStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadStats = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await butanoApi.getResourceStats();
      setStats(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load resource statistics");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  // Sort entries by count descending
  const sortedEntries = stats
    ? Object.entries(stats.counts).sort(([, a], [, b]) => b - a)
    : [];

  const totalResources = sortedEntries.reduce((sum, [, count]) => sum + count, 0);

  return (
    <div className="max-w-5xl mx-auto px-6 py-8">
      <div className="mb-8 flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">
            Resource Statistics
          </h1>
          <p className="text-sm text-neutral-500 mt-1">
            Aggregate FHIR resource counts for the current tenant partition
          </p>
        </div>
        <button
          onClick={loadStats}
          disabled={loading}
          className="px-4 py-2 text-sm font-medium text-brand-primary bg-brand-primary/10 rounded-lg hover:bg-brand-primary/20 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {loading ? "Loading..." : "Refresh"}
        </button>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-danger/5 border border-danger/20 text-danger text-sm rounded-lg px-4 py-3 mb-6">
          {error}
        </div>
      )}

      {loading && !stats && (
        <div className="text-center py-12 text-neutral-500 text-sm">
          Loading resource statistics...
        </div>
      )}

      {stats && (
        <>
          {/* Summary bar */}
          <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-5 mb-6 flex items-center justify-between">
            <div>
              <p className="text-sm text-neutral-500">Total Resources</p>
              <p className="text-3xl font-semibold text-neutral-900 tabular-nums">
                {formatCount(totalResources)}
              </p>
            </div>
            <div>
              <p className="text-sm text-neutral-500">Resource Types</p>
              <p className="text-3xl font-semibold text-neutral-900 tabular-nums">
                {sortedEntries.length}
              </p>
            </div>
          </div>

          {/* Resource cards grid */}
          {sortedEntries.length === 0 ? (
            <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-8 text-center text-neutral-500 text-sm">
              No resources found in the current tenant partition.
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {sortedEntries.map(([resourceType, count]) => (
                <div
                  key={resourceType}
                  className={`rounded-[12px] border p-5 transition-colors ${magnitudeColor(count)}`}
                >
                  <p className="text-xs font-semibold uppercase tracking-wider opacity-70 mb-1">
                    {resourceType}
                  </p>
                  <p className="text-2xl font-semibold tabular-nums">
                    {formatCount(count)}
                  </p>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
