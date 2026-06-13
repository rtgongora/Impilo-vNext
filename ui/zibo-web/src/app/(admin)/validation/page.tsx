"use client";

import { useState, useEffect, useCallback } from "react";
import {
  ziboApi,
  type ValidationLog,
  type TopFailure,
  type GovernanceStats,
  type ValidationSeverity,
} from "@/lib/ziboApi";

const SEVERITY_COLORS: Record<ValidationSeverity, string> = {
  ERROR: "badge-severity-error",
  WARNING: "badge-severity-warning",
  INFO: "badge-severity-info",
};

export default function ValidationPage() {
  const [logs, setLogs] = useState<ValidationLog[]>([]);
  const [topFailures, setTopFailures] = useState<TopFailure[]>([]);
  const [stats, setStats] = useState<GovernanceStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [filterSeverity, setFilterSeverity] = useState<string>("");
  const [filterService, setFilterService] = useState<string>("");

  const loadData = useCallback(async () => {
    try {
      setLoading(true);

      const logParams: Record<string, string> = {};
      if (filterSeverity) logParams.severity = filterSeverity;
      if (filterService) logParams.sourceService = filterService;

      const [logsData, failuresData, statsData] = await Promise.all([
        ziboApi.getValidationLogs(logParams),
        ziboApi.getTopFailures(),
        ziboApi.getGovernanceStats(),
      ]);

      setLogs(logsData);
      setTopFailures(failuresData);
      setStats(statsData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load validation data");
    } finally {
      setLoading(false);
    }
  }, [filterSeverity, filterService]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="grid grid-cols-4 gap-4">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="card p-4 space-y-2">
                <div className="h-4 bg-neutral-100 rounded w-2/3" />
                <div className="h-8 bg-neutral-100 rounded w-1/2" />
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold text-neutral-900">Validation Logs</h1>
      <p className="text-sm text-neutral-500 mt-1 mb-6">
        Monitor terminology validation events, track failures, and review governance statistics.
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {/* Stats summary */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <div className="card p-4">
            <p className="text-xs text-neutral-500 font-medium uppercase tracking-wider">
              Total Validations
            </p>
            <p className="text-2xl font-bold text-neutral-900 mt-1">
              {stats.totalValidations.toLocaleString()}
            </p>
          </div>
          <div className="card p-4">
            <p className="text-xs text-neutral-500 font-medium uppercase tracking-wider">
              Errors
            </p>
            <p className="text-2xl font-bold text-red-600 mt-1">
              {stats.errorCount.toLocaleString()}
            </p>
            <p className="text-xs text-neutral-400 mt-0.5">
              {(stats.errorRate * 100).toFixed(1)}% error rate
            </p>
          </div>
          <div className="card p-4">
            <p className="text-xs text-neutral-500 font-medium uppercase tracking-wider">
              Warnings
            </p>
            <p className="text-2xl font-bold text-yellow-600 mt-1">
              {stats.warningCount.toLocaleString()}
            </p>
          </div>
          <div className="card p-4">
            <p className="text-xs text-neutral-500 font-medium uppercase tracking-wider">
              Info
            </p>
            <p className="text-2xl font-bold text-primary mt-1">
              {stats.infoCount.toLocaleString()}
            </p>
          </div>
        </div>
      )}

      {/* Top offending services */}
      {stats && stats.topOffendingServices.length > 0 && (
        <div className="card p-6 mb-6">
          <h2 className="text-sm font-semibold text-neutral-700 mb-3">
            Top Offending Services
          </h2>
          <div className="space-y-2">
            {stats.topOffendingServices.map((svc) => {
              const total = svc.errorCount + svc.warningCount;
              const errorPercent = total > 0 ? (svc.errorCount / total) * 100 : 0;
              return (
                <div key={svc.service} className="flex items-center gap-3">
                  <span className="text-sm text-neutral-900 font-medium w-40 truncate">
                    {svc.service}
                  </span>
                  <div className="flex-1 h-4 bg-neutral-100 rounded-full overflow-hidden">
                    <div className="h-full flex">
                      <div
                        className="bg-red-400 h-full"
                        style={{ width: `${errorPercent}%` }}
                      />
                      <div
                        className="bg-yellow-400 h-full"
                        style={{ width: `${100 - errorPercent}%` }}
                      />
                    </div>
                  </div>
                  <span className="text-xs text-neutral-500 w-24 text-right">
                    {svc.errorCount}E / {svc.warningCount}W
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Top failures */}
      {topFailures.length > 0 && (
        <div className="card overflow-hidden mb-6">
          <div className="px-4 py-3 bg-neutral-50 border-b border-neutral-200">
            <h3 className="text-sm font-semibold text-neutral-700">
              Top Failures ({topFailures.length})
            </h3>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200">
                <th className="text-left px-4 py-3 font-medium text-neutral-600">System</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Code</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Display</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Severity</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Count</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Last Seen</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {topFailures.map((failure, idx) => (
                <tr key={idx} className="hover:bg-neutral-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs text-neutral-600 max-w-[200px] truncate">
                    {failure.system}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs font-bold text-neutral-900">
                    {failure.code}
                  </td>
                  <td className="px-4 py-3 text-neutral-700">
                    {failure.display || "--"}
                  </td>
                  <td className="px-4 py-3">
                    <span className={SEVERITY_COLORS[failure.severity]}>
                      {failure.severity}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-bold text-neutral-900">
                    {failure.count.toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-neutral-500 text-xs">
                    {new Date(failure.lastSeen).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Filters */}
      <div className="flex gap-4 mb-4">
        <select
          value={filterSeverity}
          onChange={(e) => setFilterSeverity(e.target.value)}
          className="select-field w-40"
        >
          <option value="">All Severities</option>
          <option value="ERROR">Error</option>
          <option value="WARNING">Warning</option>
          <option value="INFO">Info</option>
        </select>
        <input
          type="text"
          value={filterService}
          onChange={(e) => setFilterService(e.target.value)}
          placeholder="Filter by service name..."
          className="input-field flex-1"
        />
      </div>

      {/* Validation logs table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Timestamp</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Service</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Resource Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Severity</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Code</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Diagnostics</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {logs.map((log) => (
              <tr key={log.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3 text-neutral-500 text-xs whitespace-nowrap">
                  {new Date(log.timestamp).toLocaleString()}
                </td>
                <td className="px-4 py-3 text-neutral-900 font-medium">
                  {log.sourceService}
                </td>
                <td className="px-4 py-3 text-neutral-600">
                  {log.resourceType}
                </td>
                <td className="px-4 py-3">
                  <span className={SEVERITY_COLORS[log.severity]}>
                    {log.severity}
                  </span>
                </td>
                <td className="px-4 py-3 font-mono text-xs text-neutral-700">
                  {log.code}
                  {log.system && (
                    <span className="block text-neutral-400 mt-0.5">
                      {log.system}:{log.valueCode}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-neutral-600 max-w-xs truncate">
                  {log.diagnostics}
                </td>
              </tr>
            ))}
            {logs.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-neutral-500">
                  No validation logs found for the current filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        {logs.length} log entr{logs.length !== 1 ? "ies" : "y"}
      </div>
    </div>
  );
}
